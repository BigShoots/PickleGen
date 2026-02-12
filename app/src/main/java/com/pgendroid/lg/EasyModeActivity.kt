package com.picklecal.lg

import android.content.Intent
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.picklecal.lg.hdr.HdrController
import com.picklecal.lg.model.AppState
import com.picklecal.lg.model.PatternMode
import com.picklecal.lg.network.PickleCalServer
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Easy Mode activity: The PickleGen device waits for a PickleCal Windows app
 * connection and becomes fully remote-controlled.
 *
 * The user sees:
 * 1. A waiting screen with the device IP address
 * 2. Instructions to enter this IP in PickleCal on their PC
 * 3. Once connected, a status indicator and "Go Full Screen" button
 * 4. When calibration starts, the app switches to the pattern display
 */
class EasyModeActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIpAddress: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnFullScreen: Button
    private lateinit var btnBack: Button

    private var server: PickleCalServer? = null
    private var serverThread: Thread? = null
    private var isPatternActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_easy_mode)

        initViews()
        displayDeviceInfo()
        startServer()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvEasyStatus)
        tvIpAddress = findViewById(R.id.tvEasyIpAddress)
        tvInstructions = findViewById(R.id.tvEasyInstructions)
        tvDeviceInfo = findViewById(R.id.tvEasyDeviceInfo)
        progressBar = findViewById(R.id.progressEasyWaiting)
        btnFullScreen = findViewById(R.id.btnEasyFullScreen)
        btnBack = findViewById(R.id.btnEasyBack)

        btnFullScreen.visibility = View.GONE

        btnFullScreen.setOnClickListener {
            launchPatternActivity()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private fun displayDeviceInfo() {
        val ip = getDeviceIpAddress()
        tvIpAddress.text = ip ?: "No network connection"

        val hdrInfo = HdrController.getHdrInfo(this)
        val caps = mutableListOf<String>()
        if (hdrInfo.isAmlogicDevice) caps.add("Amlogic")
        if (hdrInfo.supportsHdr10) caps.add("HDR10")
        if (hdrInfo.supportsHlg) caps.add("HLG")
        if (hdrInfo.supportsDolbyVision) caps.add("Dolby Vision")

        val capText = if (caps.isNotEmpty()) caps.joinToString(" • ") else "Standard display"
        tvDeviceInfo.text = "${android.os.Build.MODEL} — $capText"

        tvInstructions.text = buildString {
            append("Step 1: Open PickleCal on your Windows PC\n")
            append("Step 2: Click 'Connect to PickleGen'\n")
            append("Step 3: Enter the IP address shown above\n")
            append("Step 4: Follow the calibration wizard\n\n")
            append("PickleCal will control this device automatically.\n")
            append("No other setup required!")
        }
    }

    private fun startServer() {
        server = PickleCalServer(
            onModeChange = { hdr, bitDepth ->
                runOnUiThread {
                    tvStatus.text = "Mode: ${bitDepth}-bit ${if (hdr) "HDR" else "SDR"}"
                    // If we're in pattern activity, apply mode there
                    if (isPatternActive) {
                        // Mode will be applied when PatternActivity reads AppState
                    }
                }
            },
            onHdrMetadata = { maxCll, maxFall, maxDml ->
                runOnUiThread {
                    tvStatus.text = "HDR metadata updated (CLL=$maxCll)"
                }
            },
            onStatusUpdate = { status ->
                runOnUiThread {
                    tvStatus.text = status
                    updateUiForConnectionState()
                }
            }
        )

        serverThread = Thread({
            server?.start()
        }, "PickleCal-Server").also {
            it.isDaemon = true
            it.start()
        }

        tvStatus.text = "Waiting for PickleCal to connect..."
    }

    private fun updateUiForConnectionState() {
        val connected = server?.isConnected == true
        if (connected) {
            progressBar.visibility = View.GONE
            btnFullScreen.visibility = View.VISIBLE
            tvInstructions.text = "✓ PickleCal connected!\n\nPress 'Start Patterns' below or\nwait for calibration to begin."

            // Auto-launch pattern activity when first commands come in
            if (!isPatternActive) {
                launchPatternActivity()
            }
        } else {
            progressBar.visibility = View.VISIBLE
            btnFullScreen.visibility = View.GONE
            displayDeviceInfo() // Reset instructions
        }
    }

    private fun launchPatternActivity() {
        if (isPatternActive) return
        isPatternActive = true

        val intent = Intent(this, PatternActivity::class.java).apply {
            putExtra(PatternActivity.EXTRA_MODE, PatternMode.PICKLECAL_EASY.name)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        isPatternActive = false
        updateUiForConnectionState()
    }

    override fun onDestroy() {
        server?.stop()
        try { serverThread?.join(2000) } catch (_: Exception) {}
        server = null
        serverThread = null
        super.onDestroy()
    }

    private fun getDeviceIpAddress(): String? {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                val wifiInfo = wifiManager.connectionInfo
                val ipInt = wifiInfo.ipAddress
                if (ipInt != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff, (ipInt shr 8) and 0xff,
                        (ipInt shr 16) and 0xff, (ipInt shr 24) and 0xff
                    )
                }
            }
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address) return addr.hostAddress
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
