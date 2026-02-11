package com.picklecal.lg.model

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Shared application state for communication between network threads and the renderer.
 * Thread-safe singleton that mirrors dogegen's global state variables.
 */
object AppState {
    @Volatile
    var bitDepth: Int = 8

    @Volatile
    var hdr: Boolean = false

    @Volatile
    var flicker: Int = 0

    val debug = AtomicBoolean(false)

    @Volatile
    var maxCLL: Int = -1
    @Volatile
    var maxFALL: Int = -1
    @Volatile
    var maxDML: Int = -1

    private val drawCommands = AtomicReference<List<DrawCommand>>(emptyList())
    private val pending = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    @Volatile
    var modeChanged: Boolean = false

    val connectionStatus = AtomicReference("Idle")

    /** LG TV connection status */
    val lgConnectionStatus = AtomicReference("Disconnected")

    /** LG TV IP address (persisted) */
    @Volatile
    var lgTvIp: String = ""

    val maxValue: Float
        get() = ((1 shl bitDepth) - 1).toFloat()

    fun setCommands(commands: List<DrawCommand>) {
        drawCommands.set(commands)
        setPending()
    }

    fun getCommands(): List<DrawCommand> = drawCommands.get()

    fun setPending() {
        lock.withLock {
            pending.set(true)
            condition.signalAll()
        }
    }

    fun waitPending() {
        lock.withLock {
            while (pending.get()) {
                condition.await()
            }
        }
    }

    fun clearPending() {
        lock.withLock {
            pending.set(false)
            condition.signalAll()
        }
    }

    fun isPending(): Boolean = pending.get()

    fun setMode(bits: Int, isHdr: Boolean) {
        bitDepth = bits
        hdr = isHdr
        modeChanged = true
    }

    fun parseModeString(mode: String): Boolean {
        return when (mode.trim().lowercase()) {
            "8" -> { setMode(8, false); true }
            "8_hdr" -> { setMode(8, true); true }
            "10" -> { setMode(10, false); true }
            "10_hdr" -> { setMode(10, true); true }
            else -> false
        }
    }

    fun reset() {
        bitDepth = 8
        hdr = false
        flicker = 0
        maxCLL = -1
        maxFALL = -1
        maxDML = -1
        drawCommands.set(emptyList())
        pending.set(false)
        modeChanged = false
        connectionStatus.set("Idle")
    }
}

enum class PatternMode {
    MANUAL,
    RESOLVE_SDR,
    RESOLVE_HDR,
    PGEN_SDR,
    PGEN_HDR
}
