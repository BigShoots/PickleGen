package com.picklecal.lg.network

import android.util.Log
import com.picklecal.lg.model.AppState
import com.picklecal.lg.model.DrawCommand
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PGenerator protocol server.
 * Acts as a PGenerator for HCFR / Calman calibration software.
 */
class PGenServer(
    private val isHdr: Boolean,
    private val passiveR: Int = -1,
    private val passiveG: Int = -1,
    private val passiveB: Int = -1
) {
    companion object {
        private const val TAG = "PGenServer"
        private const val UDP_PORT = 1977
        private const val TCP_PORT = 85
        private const val MAX_BUFFER_SIZE = 1024
        private const val SCREEN_WIDTH = 3840
        private const val SCREEN_HEIGHT = 2160
    }

    @Volatile
    private var running = false
    private val discoveryActive = AtomicBoolean(false)
    private var udpSocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var udpThread: Thread? = null

    private val maxV = 255f

    private fun buildPassivePattern(): List<DrawCommand> {
        if (passiveR < 0) return emptyList()
        return listOf(DrawCommand.fullFieldInt(passiveR, passiveG, passiveB, maxV))
    }

    fun start() {
        running = true
        val passivePattern = buildPassivePattern()
        var switchedMode = false

        try {
            udpSocket = DatagramSocket(UDP_PORT).apply { reuseAddress = true }

            discoveryActive.set(true)
            udpThread = Thread({ discoveryHandler() }, "PGen-Discovery").also { it.start() }

            while (running) {
                serverSocket = ServerSocket(TCP_PORT).apply {
                    reuseAddress = true
                    soTimeout = 2000
                }

                AppState.waitPending()

                if (!switchedMode) {
                    AppState.setMode(8, isHdr)
                    AppState.modeChanged = true
                    Log.i(TAG, "Switching to 8 bit ${if (isHdr) "HDR" else "SDR"} output")
                    switchedMode = true
                }

                AppState.setCommands(passivePattern)
                AppState.connectionStatus.set("PGen: Waiting on port $TCP_PORT...")
                Log.i(TAG, "Waiting for incoming connection...")

                var accepted = false
                while (running && !accepted) {
                    try {
                        clientSocket = serverSocket!!.accept()
                        accepted = true
                    } catch (e: java.net.SocketTimeoutException) { }
                }

                if (!accepted || clientSocket == null) continue

                Log.i(TAG, "Client connected. Closing server socket.")
                AppState.connectionStatus.set("PGen: Client connected")
                serverSocket?.close()
                serverSocket = null

                handleClient(clientSocket!!, passivePattern)

                Log.i(TAG, "Client disconnected. Reopening server socket.")
                clientSocket?.close()
                clientSocket = null
            }
        } catch (e: IOException) {
            if (running) {
                Log.e(TAG, "Server error", e)
                AppState.connectionStatus.set("PGen error: ${e.message}")
            }
        } finally {
            cleanup(switchedMode)
        }
    }

    private fun handleClient(client: Socket, passivePattern: List<DrawCommand>) {
        val inputStream = client.getInputStream()
        val outputStream = client.getOutputStream()

        try {
            while (running && !client.isClosed) {
                AppState.waitPending()

                val message = readPGenMessage(inputStream) ?: break

                if (AppState.debug.get()) {
                    Log.d(TAG, "Received: $message")
                }

                var response: String? = null

                when {
                    message == "CMD:GET_RESOLUTION" -> response = "OK:${SCREEN_WIDTH}x${SCREEN_HEIGHT}"
                    message == "CMD:GET_GPU_MEMORY" -> response = "OK:192"
                    message == "TESTTEMPLATE:PatternDynamic:0,0,0" -> AppState.setCommands(passivePattern)
                    message.startsWith("RGB=RECTANGLE") -> handleRectangleCommand(message, passivePattern)
                    message.startsWith("RGB=TEXT") || message.startsWith("RGB=IMAGE") -> { }
                    else -> AppState.setCommands(emptyList())
                }

                AppState.setPending()

                if (response != null) {
                    sendPGenResponse(outputStream, response)
                }
            }
        } catch (e: IOException) {
            if (running) {
                Log.e(TAG, "Client communication error", e)
            }
        }
    }

    private fun handleRectangleCommand(command: String, passivePattern: List<DrawCommand>) {
        try {
            val parts = command.substringAfter(";").split(";")
            if (parts.size < 9) {
                Log.e(TAG, "Invalid RGB=RECTANGLE command: not enough fields")
                return
            }

            val width = parts[0].toInt()
            val height = parts[1].toInt()
            val r = parts[3].toInt()
            val g = parts[4].toInt()
            val b = parts[5].toInt()
            val bgR = parts[6].toInt()
            val bgG = parts[7].toInt()
            val bgB = parts[8].toInt()

            val commands = mutableListOf<DrawCommand>()

            val background = DrawCommand()
            background.setCoordsFromWindow(100f)
            background.setColorsFromRgb(intArrayOf(bgR, bgG, bgB), maxV)
            commands.add(background)

            val draw = DrawCommand()
            draw.setColorsFromRgb(intArrayOf(r, g, b), maxV)
            draw.x1 = -1.0f * width / SCREEN_WIDTH
            draw.y1 = 1.0f * height / SCREEN_HEIGHT
            draw.x2 = -1.0f * draw.x1
            draw.y2 = -1.0f * draw.y1
            commands.add(draw)

            AppState.setCommands(commands)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RGB=RECTANGLE command", e)
        }
    }

    private fun readPGenMessage(input: InputStream): String? {
        val buffer = ByteArray(MAX_BUFFER_SIZE)
        var pos = 0

        while (pos < MAX_BUFFER_SIZE - 1) {
            val b = input.read()
            if (b == -1) return null
            buffer[pos] = b.toByte()

            if (pos > 0 && buffer[pos].toInt() == 0x0D && buffer[pos - 1].toInt() == 0x02) {
                return String(buffer, 0, pos - 1, Charsets.UTF_8)
            }
            pos++
        }

        return String(buffer, 0, pos, Charsets.UTF_8)
    }

    private fun sendPGenResponse(output: OutputStream, response: String) {
        val data = ByteArray(response.length + 1)
        System.arraycopy(response.toByteArray(Charsets.UTF_8), 0, data, 0, response.length)
        data[response.length] = 0
        output.write(data)
        output.flush()
    }

    private fun discoveryHandler() {
        val buffer = ByteArray(MAX_BUFFER_SIZE)

        while (discoveryActive.get()) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket?.receive(packet)

                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (message == "Who is a PGenerator") {
                    val response = "This is pgendroid-lg :)"
                    val responseBytes = response.toByteArray(Charsets.UTF_8)
                    val responsePacket = DatagramPacket(
                        responseBytes, responseBytes.size,
                        packet.address, packet.port
                    )
                    udpSocket?.send(responsePacket)
                    Log.i(TAG, "Sent discovery response to ${packet.address}")
                }
            } catch (e: SocketException) {
                if (discoveryActive.get()) Log.e(TAG, "UDP discovery error", e)
            } catch (e: IOException) {
                if (discoveryActive.get()) Log.e(TAG, "UDP discovery error", e)
            }
        }
    }

    fun stop() {
        running = false
        discoveryActive.set(false)
        try { clientSocket?.close() } catch (e: IOException) {}
        try { serverSocket?.close() } catch (e: IOException) {}
        try { udpSocket?.close() } catch (e: IOException) {}
        AppState.clearPending()
    }

    private fun cleanup(switchedMode: Boolean) {
        discoveryActive.set(false)
        try { udpSocket?.close() } catch (e: IOException) {}
        try { udpThread?.join(1000) } catch (e: InterruptedException) {}
        try { serverSocket?.close() } catch (e: IOException) {}
        try { clientSocket?.close() } catch (e: IOException) {}

        if (switchedMode) {
            AppState.waitPending()
            AppState.setCommands(emptyList())
        }

        AppState.connectionStatus.set("PGen: Stopped")
        Log.i(TAG, "Server stopped")
    }
}