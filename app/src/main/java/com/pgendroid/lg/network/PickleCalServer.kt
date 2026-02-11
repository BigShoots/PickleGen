package com.picklecal.lg.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.picklecal.lg.hdr.HdrController
import com.picklecal.lg.model.AppState
import com.picklecal.lg.model.DrawCommand
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PickleCal Remote Control Server — Easy Mode receiver.
 *
 * Listens on TCP port 5742 for JSON commands from the PickleCal Windows app.
 * This is a higher-level protocol than PGen — it controls:
 * - Pattern generation (full-field, window, custom)
 * - HDR/SDR mode switching
 * - Bit depth configuration (8/10)
 * - HDR metadata (MaxCLL, MaxFALL, MaxDML)
 * - Device status reporting
 * - LG TV relay commands
 *
 * Protocol: newline-delimited JSON messages.
 * Each message is a JSON object with a "cmd" field.
 * Each response is a JSON object with "status" ("ok"/"error") and optional data.
 */
class PickleCalServer(
    private val onModeChange: ((hdr: Boolean, bitDepth: Int) -> Unit)? = null,
    private val onHdrMetadata: ((maxCll: Int, maxFall: Int, maxDml: Int) -> Unit)? = null,
    private val onStatusUpdate: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "PickleCalServer"
        const val PORT = 5742
        private const val VERSION = "1.0"
    }

    @Volatile
    private var running = false
    private val connected = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var writer: BufferedWriter? = null
    private val gson = Gson()

    val isConnected: Boolean get() = connected.get()

    fun start() {
        running = true
        updateStatus("PickleCal Server: Starting on port $PORT...")

        try {
            serverSocket = ServerSocket(PORT).apply {
                reuseAddress = true
                soTimeout = 2000
            }

            updateStatus("PickleCal Server: Waiting for connection on port $PORT")

            while (running) {
                try {
                    val socket = serverSocket?.accept() ?: continue
                    clientSocket = socket
                    connected.set(true)
                    updateStatus("PickleCal: Connected from ${socket.inetAddress.hostAddress}")
                    handleClient(socket)
                } catch (e: java.net.SocketTimeoutException) {
                    // Normal timeout, keep waiting
                } catch (e: SocketException) {
                    if (running) Log.e(TAG, "Socket error", e)
                }
            }
        } catch (e: IOException) {
            if (running) {
                Log.e(TAG, "Server error", e)
                updateStatus("PickleCal Server error: ${e.message}")
            }
        } finally {
            cleanup()
        }
    }

    fun stop() {
        running = false
        connected.set(false)
        try {
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: IOException) {
            // Ignore
        }
        updateStatus("PickleCal Server: Stopped")
    }

    private fun handleClient(socket: Socket) {
        val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(socket.outputStream, Charsets.UTF_8))

        try {
            // Send hello
            sendResponse(JsonObject().apply {
                addProperty("event", "hello")
                addProperty("version", VERSION)
                addProperty("device", android.os.Build.MODEL)
                addProperty("hdr", AppState.hdr)
                addProperty("bitDepth", AppState.bitDepth)
            })

            while (running && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue

                try {
                    val json = JsonParser.parseString(line).asJsonObject
                    val response = processCommand(json)
                    sendResponse(response)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing command: $line", e)
                    sendResponse(errorResponse("Parse error: ${e.message}"))
                }
            }
        } catch (e: IOException) {
            if (running) Log.e(TAG, "Client read error", e)
        } finally {
            connected.set(false)
            writer = null
            socket.close()
            clientSocket = null
            updateStatus("PickleCal: Client disconnected")
        }
    }

    private fun processCommand(json: JsonObject): JsonObject {
        val cmd = json.get("cmd")?.asString ?: return errorResponse("Missing 'cmd' field")

        return when (cmd) {
            "ping" -> okResponse().apply { addProperty("pong", true) }

            "get_status" -> okResponse().apply {
                addProperty("hdr", AppState.hdr)
                addProperty("bitDepth", AppState.bitDepth)
                addProperty("maxCLL", AppState.maxCLL)
                addProperty("maxFALL", AppState.maxFALL)
                addProperty("maxDML", AppState.maxDML)
                addProperty("mode", if (AppState.hdr) "${AppState.bitDepth}_hdr" else "${AppState.bitDepth}")
            }

            "set_mode" -> {
                val hdr = json.get("hdr")?.asBoolean ?: AppState.hdr
                val bitDepth = json.get("bitDepth")?.asInt ?: AppState.bitDepth
                AppState.setMode(bitDepth, hdr)
                AppState.modeChanged = true
                onModeChange?.invoke(hdr, bitDepth)
                updateStatus("Mode: ${bitDepth}-bit ${if (hdr) "HDR" else "SDR"}")
                okResponse().apply {
                    addProperty("hdr", hdr)
                    addProperty("bitDepth", bitDepth)
                }
            }

            "set_hdr_metadata" -> {
                val maxCll = json.get("maxCLL")?.asInt ?: -1
                val maxFall = json.get("maxFALL")?.asInt ?: -1
                val maxDml = json.get("maxDML")?.asInt ?: -1
                AppState.maxCLL = maxCll
                AppState.maxFALL = maxFall
                AppState.maxDML = maxDml
                onHdrMetadata?.invoke(maxCll, maxFall, maxDml)
                okResponse()
            }

            "pattern_fullfield" -> {
                val r = json.get("r")?.asInt ?: 0
                val g = json.get("g")?.asInt ?: 0
                val b = json.get("b")?.asInt ?: 0
                val maxV = AppState.maxValue
                val commands = listOf(DrawCommand.fullFieldInt(r, g, b, maxV))
                AppState.setCommands(commands)
                AppState.setPending()
                okResponse()
            }

            "pattern_window" -> {
                val r = json.get("r")?.asInt ?: 255
                val g = json.get("g")?.asInt ?: 255
                val b = json.get("b")?.asInt ?: 255
                val bgR = json.get("bgR")?.asInt ?: 0
                val bgG = json.get("bgG")?.asInt ?: 0
                val bgB = json.get("bgB")?.asInt ?: 0
                val windowPercent = json.get("windowPercent")?.asFloat ?: 10f
                val maxV = AppState.maxValue

                val commands = mutableListOf<DrawCommand>()
                // Background
                val bg = DrawCommand()
                bg.setCoordsFromWindow(100f)
                bg.setColorsFromRgb(intArrayOf(bgR, bgG, bgB), maxV)
                commands.add(bg)
                // Window
                val win = DrawCommand()
                win.setCoordsFromWindow(windowPercent)
                win.setColorsFromRgb(intArrayOf(r, g, b), maxV)
                commands.add(win)

                AppState.setCommands(commands)
                AppState.setPending()
                okResponse()
            }

            "pattern_black" -> {
                AppState.setCommands(listOf(DrawCommand.fullFieldInt(0, 0, 0, AppState.maxValue)))
                AppState.setPending()
                okResponse()
            }

            "pattern_white" -> {
                val maxV = AppState.maxValue.toInt()
                AppState.setCommands(listOf(DrawCommand.fullFieldInt(maxV, maxV, maxV, AppState.maxValue)))
                AppState.setPending()
                okResponse()
            }

            "pattern_clear" -> {
                AppState.setCommands(emptyList())
                AppState.setPending()
                okResponse()
            }

            "disconnect" -> {
                sendResponse(okResponse())
                stop()
                okResponse()
            }

            else -> errorResponse("Unknown command: $cmd")
        }
    }

    /**
     * Send an unsolicited event to the connected client (e.g. status change).
     */
    fun sendEvent(eventName: String, data: JsonObject? = null) {
        if (!connected.get()) return
        val json = data ?: JsonObject()
        json.addProperty("event", eventName)
        try {
            sendResponse(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send event: $eventName", e)
        }
    }

    private fun sendResponse(json: JsonObject) {
        try {
            val w = writer ?: return
            w.write(gson.toJson(json))
            w.newLine()
            w.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send response", e)
        }
    }

    private fun okResponse(): JsonObject = JsonObject().apply { addProperty("status", "ok") }
    private fun errorResponse(msg: String): JsonObject = JsonObject().apply {
        addProperty("status", "error")
        addProperty("message", msg)
    }

    private fun updateStatus(status: String) {
        Log.i(TAG, status)
        AppState.connectionStatus.set(status)
        onStatusUpdate?.invoke(status)
    }

    private fun cleanup() {
        connected.set(false)
        try { clientSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        serverSocket = null
        writer = null
    }
}
