package com.picklecal.lg

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.picklecal.lg.hdr.HdrController
import com.picklecal.lg.model.AppState
import com.picklecal.lg.model.PatternMode
import com.picklecal.lg.network.PGenServer
import com.picklecal.lg.network.ResolveClient
import com.picklecal.lg.patterns.PatternGenerator
import com.picklecal.lg.renderer.PatternRenderer

/**
 * Fullscreen activity for displaying test patterns.
 * Handles:
 * - OpenGL ES 3.0 pattern rendering via GLSurfaceView
 * - Network protocol management (Resolve client or PGenerator server)
 * - HDR mode switching for Amlogic devices
 * - Manual test pattern display
 */
class PatternActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PatternActivity"

        const val EXTRA_MODE = "mode"
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"
        const val EXTRA_WINDOW_SIZE = "window_size"
        const val EXTRA_PASSIVE_R = "passive_r"
        const val EXTRA_PASSIVE_G = "passive_g"
        const val EXTRA_PASSIVE_B = "passive_b"
        const val EXTRA_PATTERN_TYPE = "pattern_type"
    }

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var renderer: PatternRenderer
    private var networkThread: Thread? = null
    private var resolveClient: ResolveClient? = null
    private var pgenServer: PGenServer? = null
    private var patternMode: PatternMode = PatternMode.MANUAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupFullscreen()
        setContentView(R.layout.activity_pattern)

        HdrController.keepScreenOn(window, true)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        setupGLSurface()

        parseIntentAndStart()
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
    }

    private fun setupGLSurface() {
        renderer = PatternRenderer()
        glSurfaceView.setEGLContextClientVersion(3)
        glSurfaceView.setRenderer(renderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun parseIntentAndStart() {
        val modeName = intent.getStringExtra(EXTRA_MODE) ?: PatternMode.MANUAL.name
        patternMode = try {
            PatternMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            PatternMode.MANUAL
        }

        Log.i(TAG, "Starting in mode: $patternMode")

        when (patternMode) {
            PatternMode.RESOLVE_SDR -> startResolveMode(false)
            PatternMode.RESOLVE_HDR -> startResolveMode(true)
            PatternMode.PGEN_SDR -> startPGenMode(false)
            PatternMode.PGEN_HDR -> startPGenMode(true)
            PatternMode.MANUAL -> startManualMode()
        }

        applyHdrMode()
        applyHdrMetadata()
    }

    private fun startResolveMode(isHdr: Boolean) {
        val ip = intent.getStringExtra(EXTRA_IP) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 20002)
        val windowSize = intent.getFloatExtra(EXTRA_WINDOW_SIZE, 0f)

        AppState.setMode(if (isHdr) 10 else 8, isHdr)

        resolveClient = ResolveClient(ip, port, isHdr, windowSize)

        networkThread = Thread({
            resolveClient?.start()
            runOnUiThread {
                if (!isFinishing) {
                    Log.i(TAG, "Resolve connection ended")
                }
            }
        }, "Resolve-Client").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun startPGenMode(isHdr: Boolean) {
        val passiveR = intent.getIntExtra(EXTRA_PASSIVE_R, -1)
        val passiveG = intent.getIntExtra(EXTRA_PASSIVE_G, -1)
        val passiveB = intent.getIntExtra(EXTRA_PASSIVE_B, -1)

        AppState.setMode(8, isHdr)

        pgenServer = PGenServer(isHdr, passiveR, passiveG, passiveB)

        networkThread = Thread({
            pgenServer?.start()
        }, "PGen-Server").also {
            it.isDaemon = true
            it.start()
        }
    }

    private fun startManualMode() {
        val patternType = intent.getStringExtra(EXTRA_PATTERN_TYPE) ?: "window"
        val windowSize = intent.getFloatExtra(EXTRA_WINDOW_SIZE, 10f)
        val r = intent.getIntExtra(EXTRA_PASSIVE_R, 255)
        val g = intent.getIntExtra(EXTRA_PASSIVE_G, 255)
        val b = intent.getIntExtra(EXTRA_PASSIVE_B, 255)

        val commands = when (patternType) {
            "pluge" -> PatternGenerator.drawPluge(AppState.hdr, AppState.bitDepth == 10)
            "pluge_hdr" -> PatternGenerator.drawPluge(true, AppState.bitDepth == 10)
            "bars_full" -> {
                if (AppState.bitDepth != 10) AppState.setMode(10, AppState.hdr)
                PatternGenerator.drawBars(false)
            }
            "bars_limited" -> {
                if (AppState.bitDepth != 10) AppState.setMode(10, AppState.hdr)
                PatternGenerator.drawBars(true)
            }
            "window" -> PatternGenerator.drawWindow(windowSize, r, g, b, AppState.maxValue)
            else -> PatternGenerator.parseDrawString(patternType, AppState.bitDepth) ?: emptyList()
        }

        AppState.setCommands(commands)
        Log.i(TAG, "Manual pattern '$patternType': ${commands.size} draw commands")
    }

    private fun applyHdrMode() {
        HdrController.setHdrMode(this, AppState.hdr, AppState.bitDepth)
    }

    private fun applyHdrMetadata() {
        if (AppState.maxCLL > 0) {
            val maxFall = if (AppState.maxFALL > 0) AppState.maxFALL else AppState.maxCLL
            val maxDml = if (AppState.maxDML > 0) AppState.maxDML else AppState.maxCLL
            HdrController.setHdrMetadata(AppState.maxCLL, maxFall, maxDml)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                stopAndFinish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        stopNetworkServices()
        glSurfaceView.queueEvent { renderer.cleanup() }
        HdrController.keepScreenOn(window, false)

        if (AppState.hdr) {
            HdrController.setHdrMode(this, false, 8)
        }

        AppState.reset()
        super.onDestroy()
    }

    private fun stopAndFinish() {
        stopNetworkServices()
        finish()
    }

    private fun stopNetworkServices() {
        resolveClient?.stop()
        pgenServer?.stop()
        try {
            networkThread?.join(3000)
        } catch (e: InterruptedException) {
            networkThread?.interrupt()
        }
        resolveClient = null
        pgenServer = null
        networkThread = null
    }
}