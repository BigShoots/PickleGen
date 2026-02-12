package com.picklecal.lg

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.picklecal.lg.hdr.HdrController
import com.picklecal.lg.lgcontrol.LgTvController
import com.picklecal.lg.lgcontrol.SsapClient
import com.picklecal.lg.model.AppState
import com.picklecal.lg.model.PatternMode
import com.google.gson.JsonObject
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Main configuration activity for PickleCal.
 * Provides UI for:
 * - Mode selection (8/10 bit, SDR/HDR)
 * - Network protocol setup (Resolve client / PGenerator server)
 * - Manual test pattern generation
 * - HDR metadata configuration
 * - LG TV connection and control
 *
 * Designed for Android TV with D-pad navigation.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDeviceIp: TextView
    private lateinit var spinnerMode: Spinner
    private lateinit var etServerIp: EditText
    private lateinit var etServerPort: EditText
    private lateinit var etWindowSize: EditText
    private lateinit var etRed: EditText
    private lateinit var etGreen: EditText
    private lateinit var etBlue: EditText
    private lateinit var etMaxCll: EditText
    private lateinit var etMaxFall: EditText
    private lateinit var etMaxDml: EditText

    // LG TV controls
    private lateinit var etLgTvIp: EditText
    private lateinit var tvLgStatus: TextView
    private lateinit var btnLgConnect: Button
    private lateinit var btnLgCalibration: Button
    private lateinit var btnLgDisableProcessing: Button
    private lateinit var btnLgDisconnect: Button

    private val modes = arrayOf("8-bit SDR", "8-bit HDR", "10-bit SDR", "10-bit HDR")
    private val modeValues = arrayOf("8", "8_hdr", "10", "10_hdr")

    private var lgController: LgTvController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupModeSpinner()
        setupButtons()
        setupLgControls()
        updateDeviceIp()
        displayHdrInfo()
    }

    override fun onResume() {
        super.onResume()
        updateStatus(AppState.connectionStatus.get())
        updateLgStatus()
    }

    override fun onDestroy() {
        lgController?.disconnect()
        super.onDestroy()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvDeviceIp = findViewById(R.id.tvDeviceIp)
        spinnerMode = findViewById(R.id.spinnerMode)
        etServerIp = findViewById(R.id.etServerIp)
        etServerPort = findViewById(R.id.etServerPort)
        etWindowSize = findViewById(R.id.etWindowSize)
        etRed = findViewById(R.id.etRed)
        etGreen = findViewById(R.id.etGreen)
        etBlue = findViewById(R.id.etBlue)
        etMaxCll = findViewById(R.id.etMaxCll)
        etMaxFall = findViewById(R.id.etMaxFall)
        etMaxDml = findViewById(R.id.etMaxDml)

        // LG views
        etLgTvIp = findViewById(R.id.etLgTvIp)
        tvLgStatus = findViewById(R.id.tvLgStatus)
        btnLgConnect = findViewById(R.id.btnLgConnect)
        btnLgCalibration = findViewById(R.id.btnLgCalibration)
        btnLgDisableProcessing = findViewById(R.id.btnLgDisableProcessing)
        btnLgDisconnect = findViewById(R.id.btnLgDisconnect)

        // Set defaults
        etServerPort.setText("20002")
        etWindowSize.setText("10")
        etRed.setText("255")
        etGreen.setText("255")
        etBlue.setText("255")
        etMaxCll.setText("1000")
        etMaxFall.setText("400")
        etMaxDml.setText("1000")

        // Load saved LG TV IP
        val controller = LgTvController(this)
        lgController = controller
        val savedIp = controller.getSavedTvIp()
        if (savedIp != null) {
            etLgTvIp.setText(savedIp)
        }
    }

    private fun setupModeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMode.adapter = adapter

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                AppState.parseModeString(modeValues[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        // Resolve protocol buttons
        findViewById<Button>(R.id.btnResolveSdr).setOnClickListener { startResolve(false) }
        findViewById<Button>(R.id.btnResolveHdr).setOnClickListener { startResolve(true) }

        // PGenerator protocol buttons
        findViewById<Button>(R.id.btnPgenSdr).setOnClickListener { startPGen(false) }
        findViewById<Button>(R.id.btnPgenHdr).setOnClickListener { startPGen(true) }

        // Manual pattern buttons
        findViewById<Button>(R.id.btnPluge).setOnClickListener { startManualPattern("pluge") }
        findViewById<Button>(R.id.btnPlugeHdr).setOnClickListener { startManualPattern("pluge_hdr") }
        findViewById<Button>(R.id.btnBarsFull).setOnClickListener { startManualPattern("bars_full") }
        findViewById<Button>(R.id.btnBarsLimited).setOnClickListener { startManualPattern("bars_limited") }

        // Window pattern button
        findViewById<Button>(R.id.btnWindow).setOnClickListener { startWindowPattern() }
    }

    private fun setupLgControls() {
        val controller = lgController ?: return

        // Setup callbacks
        controller.ssapClient.onStatusChange = { status ->
            runOnUiThread {
                tvLgStatus.text = status
                updateLgButtonStates()
            }
        }

        controller.ssapClient.onPairingRequired = {
            runOnUiThread {
                Toast.makeText(this, "Please accept the pairing request on your LG TV", Toast.LENGTH_LONG).show()
            }
        }

        controller.ssapClient.onDisconnect = {
            runOnUiThread {
                tvLgStatus.text = getString(R.string.lg_status_disconnected)
                updateLgButtonStates()
            }
        }

        // Connect button
        btnLgConnect.setOnClickListener {
            val tvIp = etLgTvIp.text.toString().trim()
            if (tvIp.isEmpty()) {
                Toast.makeText(this, "Please enter the LG TV IP address", Toast.LENGTH_SHORT).show()
                etLgTvIp.requestFocus()
                return@setOnClickListener
            }
            AppState.lgTvIp = tvIp
            controller.connect(tvIp, useSecure = true)
        }

        // Calibration activity button
        btnLgCalibration.setOnClickListener {
            val intent = Intent(this, LgControlActivity::class.java)
            startActivity(intent)
        }

        // Disable processing button
        btnLgDisableProcessing.setOnClickListener {
            controller.disableAllProcessing(object : SsapClient.ResponseCallback {
                override fun onResponse(response: JsonObject) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Processing disabled for calibration", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(errorMessage: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

        // Disconnect button
        btnLgDisconnect.setOnClickListener {
            controller.disconnect()
        }

        updateLgButtonStates()
    }

    private fun updateLgButtonStates() {
        val connected = lgController?.isConnected == true && lgController?.isPaired == true
        btnLgCalibration.isEnabled = connected
        btnLgDisableProcessing.isEnabled = connected
        btnLgDisconnect.isEnabled = lgController?.isConnected == true
        btnLgConnect.isEnabled = lgController?.isConnected != true
    }

    private fun updateLgStatus() {
        val controller = lgController
        if (controller != null && controller.isConnected && controller.isPaired) {
            tvLgStatus.text = getString(R.string.lg_status_connected)
        } else {
            tvLgStatus.text = getString(R.string.lg_status_disconnected)
        }
        updateLgButtonStates()
    }

    private fun startResolve(isHdr: Boolean) {
        val ip = etServerIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter the server IP address", Toast.LENGTH_SHORT).show()
            etServerIp.requestFocus()
            return
        }

        val port = etServerPort.text.toString().toIntOrNull() ?: 20002
        val windowSize = etWindowSize.text.toString().toFloatOrNull() ?: 0f

        updateHdrMetadata()

        val intent = Intent(this, PatternActivity::class.java).apply {
            putExtra(PatternActivity.EXTRA_MODE, if (isHdr) PatternMode.RESOLVE_HDR.name else PatternMode.RESOLVE_SDR.name)
            putExtra(PatternActivity.EXTRA_IP, ip)
            putExtra(PatternActivity.EXTRA_PORT, port)
            putExtra(PatternActivity.EXTRA_WINDOW_SIZE, windowSize)
        }
        startActivity(intent)
    }

    private fun startPGen(isHdr: Boolean) {
        val r = etRed.text.toString().toIntOrNull() ?: -1
        val g = etGreen.text.toString().toIntOrNull() ?: -1
        val b = etBlue.text.toString().toIntOrNull() ?: -1

        updateHdrMetadata()

        val intent = Intent(this, PatternActivity::class.java).apply {
            putExtra(PatternActivity.EXTRA_MODE, if (isHdr) PatternMode.PGEN_HDR.name else PatternMode.PGEN_SDR.name)
            putExtra(PatternActivity.EXTRA_PASSIVE_R, r)
            putExtra(PatternActivity.EXTRA_PASSIVE_G, g)
            putExtra(PatternActivity.EXTRA_PASSIVE_B, b)
        }
        startActivity(intent)
    }

    private fun startManualPattern(patternType: String) {
        val selectedMode = modeValues[spinnerMode.selectedItemPosition]
        AppState.parseModeString(selectedMode)
        updateHdrMetadata()

        val intent = Intent(this, PatternActivity::class.java).apply {
            putExtra(PatternActivity.EXTRA_MODE, PatternMode.MANUAL.name)
            putExtra(PatternActivity.EXTRA_PATTERN_TYPE, patternType)
        }
        startActivity(intent)
    }

    private fun startWindowPattern() {
        val selectedMode = modeValues[spinnerMode.selectedItemPosition]
        AppState.parseModeString(selectedMode)

        val r = etRed.text.toString().toIntOrNull()
        val g = etGreen.text.toString().toIntOrNull()
        val b = etBlue.text.toString().toIntOrNull()
        val windowSize = etWindowSize.text.toString().toFloatOrNull() ?: 10f

        if (r == null || g == null || b == null) {
            Toast.makeText(this, "Please enter valid RGB values", Toast.LENGTH_SHORT).show()
            return
        }

        val maxV = AppState.maxValue.toInt()
        if (r < 0 || r > maxV || g < 0 || g > maxV || b < 0 || b > maxV) {
            Toast.makeText(this, "RGB values must be 0-$maxV for current mode", Toast.LENGTH_SHORT).show()
            return
        }

        updateHdrMetadata()

        val intent = Intent(this, PatternActivity::class.java).apply {
            putExtra(PatternActivity.EXTRA_MODE, PatternMode.MANUAL.name)
            putExtra(PatternActivity.EXTRA_PATTERN_TYPE, "window")
            putExtra(PatternActivity.EXTRA_WINDOW_SIZE, windowSize)
            putExtra(PatternActivity.EXTRA_PASSIVE_R, r)
            putExtra(PatternActivity.EXTRA_PASSIVE_G, g)
            putExtra(PatternActivity.EXTRA_PASSIVE_B, b)
        }
        startActivity(intent)
    }

    private fun updateHdrMetadata() {
        AppState.maxCLL = etMaxCll.text.toString().toIntOrNull() ?: -1
        AppState.maxFALL = etMaxFall.text.toString().toIntOrNull() ?: -1
        AppState.maxDML = etMaxDml.text.toString().toIntOrNull() ?: -1
    }

    private fun updateStatus(status: String) {
        runOnUiThread { tvStatus.text = status }
    }

    private fun updateDeviceIp() {
        val ip = getDeviceIpAddress()
        tvDeviceIp.text = "IP: ${ip ?: "No network"}"
    }

    private fun displayHdrInfo() {
        val hdrInfo = HdrController.getHdrInfo(this)
        val capabilities = mutableListOf<String>()
        if (hdrInfo.isAmlogicDevice) capabilities.add("Amlogic")
        if (hdrInfo.supportsHdr10) capabilities.add("HDR10")
        if (hdrInfo.supportsHlg) capabilities.add("HLG")
        if (hdrInfo.supportsDolbyVision) capabilities.add("DV")

        if (capabilities.isNotEmpty()) {
            val capText = capabilities.joinToString(", ")
            tvDeviceIp.text = "${tvDeviceIp.text} | $capText"
        }
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
                        ipInt and 0xff,
                        (ipInt shr 8) and 0xff,
                        (ipInt shr 16) and 0xff,
                        (ipInt shr 24) and 0xff
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
                    if (addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }
}