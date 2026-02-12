package com.picklecal.lg.lgcontrol

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*

/**
 * LG webOS SSAP (Simple Service Access Protocol) WebSocket client.
 *
 * Handles:
 * - WebSocket connection (ws:// port 3000 or wss:// port 3001)
 * - Registration/pairing with PROMPT type
 * - Client-key persistence via SharedPreferences
 * - SSAP request/response messaging with JSON payloads
 * - Luna service calls via ssap://com.webos.settingsservice
 *
 * For LG C2 (2022+) use secure WebSocket (wss://IP:3001).
 */
class SsapClient(private val context: Context) {

    companion object {
        private const val TAG = "SsapClient"
        private const val PREFS_NAME = "lgcontrol_prefs"
        private const val PREF_CLIENT_KEY = "lg_client_key"
        private const val PREF_TV_IP = "lg_tv_ip"
        private const val WS_PORT = 3000
        private const val WSS_PORT = 3001
        private const val CONNECT_TIMEOUT_SEC = 10L
        private const val REQUEST_TIMEOUT_SEC = 10L
    }

    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val messageId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<String, ResponseCallback>()

    private var webSocket: WebSocket? = null
    private var clientKey: String? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var isPaired: Boolean = false
        private set

    var onStatusChange: ((String) -> Unit)? = null
    var onPairingRequired: (() -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null

    // Registration payload (required by LG webOS for SSAP access)
    private val registrationPayload = """
    {
        "forcePairing": false,
        "pairingType": "PROMPT",
        "manifest": {
            "manifestVersion": 1,
            "appVersion": "1.0.0",
            "signed": {
                "created": "20240101",
                "appId": "com.picklecal.lg",
                "vendorId": "com.picklecal",
                "localizedAppNames": { "": "PickleCal" },
                "localizedVendorNames": { "": "PickleCal" },
                "permissions": [
                    "CONTROL_AUDIO",
                    "CONTROL_DISPLAY",
                    "CONTROL_INPUT_JOYSTICK",
                    "CONTROL_INPUT_MEDIA_PLAYBACK",
                    "CONTROL_INPUT_MEDIA_RECORDING",
                    "CONTROL_INPUT_TEXT",
                    "CONTROL_INPUT_TV",
                    "CONTROL_MOUSE_AND_KEYBOARD",
                    "CONTROL_POWER",
                    "READ_APP_STATUS",
                    "READ_CURRENT_CHANNEL",
                    "READ_INPUT_DEVICE_LIST",
                    "READ_NETWORK_STATE",
                    "READ_RUNNING_APPS",
                    "READ_TV_CHANNEL_LIST",
                    "READ_TV_CURRENT_TIME",
                    "WRITE_NOTIFICATION_TOAST",
                    "READ_INSTALLED_APPS",
                    "CONTROL_TV_SCREEN"
                ],
                "serial": "pgendroid-lg-001"
            },
            "permissions": [
                "CONTROL_AUDIO",
                "CONTROL_DISPLAY",
                "CONTROL_INPUT_JOYSTICK",
                "CONTROL_INPUT_MEDIA_PLAYBACK",
                "CONTROL_INPUT_MEDIA_RECORDING",
                "CONTROL_INPUT_TEXT",
                "CONTROL_INPUT_TV",
                "CONTROL_MOUSE_AND_KEYBOARD",
                "CONTROL_POWER",
                "READ_APP_STATUS",
                "READ_CURRENT_CHANNEL",
                "READ_INPUT_DEVICE_LIST",
                "READ_NETWORK_STATE",
                "READ_RUNNING_APPS",
                "READ_TV_CHANNEL_LIST",
                "READ_TV_CURRENT_TIME",
                "WRITE_NOTIFICATION_TOAST",
                "READ_INSTALLED_APPS",
                "CONTROL_TV_SCREEN"
            ],
            "signatures": [
                {
                    "signatureVersion": 1,
                    "signature": "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbmctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRyaMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQojoa7NQnAtw=="
                }
            ]
        }
    }
    """.trimIndent()

    /**
     * Connect to the LG TV via secure WebSocket.
     * Uses wss:// on port 3001 (required for 2022+ LG TVs like C2).
     */
    fun connect(tvIp: String, useSecure: Boolean = true) {
        if (isConnected) {
            Log.w(TAG, "Already connected")
            return
        }

        // Load saved client key
        clientKey = prefs.getString(PREF_CLIENT_KEY, null)
        prefs.edit().putString(PREF_TV_IP, tvIp).apply()

        val url = if (useSecure) "wss://$tvIp:$WSS_PORT" else "ws://$tvIp:$WS_PORT"
        Log.i(TAG, "Connecting to $url")
        onStatusChange?.invoke("Connecting to $tvIp...")

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)  // No read timeout for WebSocket
            .writeTimeout(REQUEST_TIMEOUT_SEC, TimeUnit.SECONDS)

        // For secure WebSocket, trust the TV's self-signed certificate
        if (useSecure) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                clientBuilder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                clientBuilder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup SSL", e)
            }
        }

        val client = clientBuilder.build()
        val request = Request.Builder().url(url).build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connected")
                webSocket = ws
                isConnected = true
                onStatusChange?.invoke("Connected, registering...")
                sendRegistration()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code $reason")
                handleDisconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                onStatusChange?.invoke("Connection failed: ${t.message}")
                handleDisconnect()
            }
        })
    }

    /**
     * Send the registration/pairing message.
     */
    private fun sendRegistration() {
        val payload = JsonParser.parseString(registrationPayload).asJsonObject

        // Include saved client key if available
        if (clientKey != null) {
            payload.addProperty("client-key", clientKey)
        }

        val message = JsonObject().apply {
            addProperty("type", "register")
            addProperty("id", "register_0")
            add("payload", payload)
        }

        webSocket?.send(gson.toJson(message))
        Log.i(TAG, "Sent registration ${if (clientKey != null) "with saved key" else "without key"}")
    }

    /**
     * Handle incoming WebSocket messages.
     */
    private fun handleMessage(text: String) {
        try {
            val json = JsonParser.parseString(text).asJsonObject
            val type = json.get("type")?.asString ?: return
            val id = json.get("id")?.asString

            when (type) {
                "registered" -> {
                    // Successfully paired/registered
                    val payload = json.getAsJsonObject("payload")
                    val newClientKey = payload?.get("client-key")?.asString
                    if (newClientKey != null) {
                        clientKey = newClientKey
                        prefs.edit().putString(PREF_CLIENT_KEY, newClientKey).apply()
                        Log.i(TAG, "Registered and saved client key")
                    }
                    isPaired = true
                    onStatusChange?.invoke("Connected & paired")
                }

                "response" -> {
                    // Response to a request
                    if (id != null) {
                        val callback = pendingRequests.remove(id)
                        callback?.onResponse(json)
                    }
                }

                "error" -> {
                    val errorText = json.get("error")?.asString ?: "Unknown error"
                    Log.e(TAG, "Error response (id=$id): $errorText")
                    if (id != null) {
                        val callback = pendingRequests.remove(id)
                        callback?.onError(errorText)
                    }
                    // If registration error with 409, need re-pairing
                    if (id == "register_0") {
                        // Client key may be invalid, clear and re-register
                        clientKey = null
                        prefs.edit().remove(PREF_CLIENT_KEY).apply()
                        onStatusChange?.invoke("Pairing required - accept on TV")
                        onPairingRequired?.invoke()
                        sendRegistration()
                    }
                }

                "prompt" -> {
                    // TV is showing the pairing prompt
                    Log.i(TAG, "Pairing prompt shown on TV - user must accept")
                    onStatusChange?.invoke("Accept pairing on TV")
                    onPairingRequired?.invoke()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message: ${e.message}", e)
        }
    }

    /**
     * Send an SSAP request and get the response asynchronously.
     */
    fun sendRequest(
        uri: String,
        payload: JsonObject? = null,
        callback: ResponseCallback? = null
    ): String {
        val id = "msg_${messageId.getAndIncrement()}"

        val message = JsonObject().apply {
            addProperty("type", "request")
            addProperty("id", id)
            addProperty("uri", uri)
            if (payload != null) {
                add("payload", payload)
            }
        }

        if (callback != null) {
            pendingRequests[id] = callback
        }

        val json = gson.toJson(message)
        webSocket?.send(json) ?: run {
            callback?.onError("Not connected")
        }

        return id
    }

    /**
     * Send an SSAP request and wait for the response synchronously.
     * Must NOT be called from the main thread.
     */
    fun sendRequestSync(
        uri: String,
        payload: JsonObject? = null,
        timeoutSec: Long = REQUEST_TIMEOUT_SEC
    ): JsonObject? {
        val latch = CountDownLatch(1)
        var result: JsonObject? = null
        var error: String? = null

        sendRequest(uri, payload, object : ResponseCallback {
            override fun onResponse(response: JsonObject) {
                result = response
                latch.countDown()
            }

            override fun onError(errorMessage: String) {
                error = errorMessage
                latch.countDown()
            }
        })

        latch.await(timeoutSec, TimeUnit.SECONDS)

        if (error != null) {
            Log.e(TAG, "Request error for $uri: $error")
        }

        return result
    }

    /**
     * Send a Luna service call via SSAP.
     * This is used for calibration settings on LG webOS TVs.
     *
     * URI: ssap://com.webos.service.settings/setSystemSettings
     * or use the createAlert trick for privileged access.
     */
    fun setSystemSettings(
        category: String,
        settings: Map<String, Any>,
        callback: ResponseCallback? = null
    ): String {
        val payload = JsonObject().apply {
            addProperty("category", category)
            val settingsObj = JsonObject()
            for ((key, value) in settings) {
                when (value) {
                    is String -> settingsObj.addProperty(key, value)
                    is Number -> settingsObj.addProperty(key, value)
                    is Boolean -> settingsObj.addProperty(key, value)
                    else -> settingsObj.addProperty(key, value.toString())
                }
            }
            add("settings", settingsObj)
        }

        return sendRequest(
            "ssap://com.webos.service.settings/setSystemSettings",
            payload,
            callback
        )
    }

    /**
     * Get system settings from the TV.
     */
    fun getSystemSettings(
        category: String,
        keys: List<String>,
        callback: ResponseCallback? = null
    ): String {
        val payload = JsonObject().apply {
            addProperty("category", category)
            val keysArray = com.google.gson.JsonArray()
            keys.forEach { keysArray.add(it) }
            add("keys", keysArray)
        }

        return sendRequest(
            "ssap://com.webos.service.settings/getSystemSettings",
            payload,
            callback
        )
    }

    /**
     * Execute a Luna service call using the createAlert / close trick.
     * This is needed for accessing privileged Luna service calls that
     * are not directly exposed via SSAP.
     *
     * Used for calibration operations on LG TVs (similar to LGTVCompanion approach).
     */
    fun executeLunaCall(
        lunaUri: String,
        params: JsonObject,
        callback: ResponseCallback? = null
    ): String {
        // Use createAlert trick to execute Luna service calls
        val alertPayload = JsonObject().apply {
            addProperty("message", " ")  // Invisible alert
            val buttons = com.google.gson.JsonArray()
            val button = JsonObject().apply {
                addProperty("label", " ")
                addProperty("onClick", lunaUri)
                add("params", params)
            }
            buttons.add(button)
            add("buttons", buttons)
        }

        return sendRequest(
            "ssap://com.webos.service.system.launcher/createAlert",
            alertPayload,
            object : ResponseCallback {
                override fun onResponse(response: JsonObject) {
                    // Close the alert immediately after creating it
                    val alertId = response.getAsJsonObject("payload")?.get("alertId")?.asString
                    if (alertId != null) {
                        val closePayload = JsonObject().apply {
                            addProperty("alertId", alertId)
                        }
                        sendRequest("ssap://com.webos.service.system.launcher/closeAlert", closePayload)
                    }
                    callback?.onResponse(response)
                }

                override fun onError(errorMessage: String) {
                    callback?.onError(errorMessage)
                }
            }
        )
    }

    /**
     * Show a toast notification on the TV.
     */
    fun showToast(message: String) {
        val payload = JsonObject().apply {
            addProperty("message", message)
        }
        sendRequest("ssap://com.webos.service.system.notifications/createToast", payload)
    }

    /**
     * Disconnect from the TV.
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        handleDisconnect()
    }

    private fun handleDisconnect() {
        isConnected = false
        isPaired = false
        webSocket = null
        pendingRequests.clear()
        onDisconnect?.invoke()
    }

    /**
     * Get the saved TV IP address.
     */
    fun getSavedTvIp(): String? = prefs.getString(PREF_TV_IP, null)

    /**
     * Check if we have a saved client key (previously paired).
     */
    fun hasSavedKey(): Boolean = prefs.getString(PREF_CLIENT_KEY, null) != null

    interface ResponseCallback {
        fun onResponse(response: JsonObject)
        fun onError(errorMessage: String)
    }
}
