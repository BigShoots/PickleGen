package com.picklecal.lg

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.picklecal.lg.lgcontrol.LgTvController
import com.picklecal.lg.lgcontrol.SsapClient
import com.google.gson.JsonObject

/**
 * LG TV calibration control activity.
 * Provides detailed calibration controls:
 * - Picture mode selection
 * - Basic picture settings (backlight, contrast, brightness, color, sharpness)
 * - Color settings (gamut, gamma, color temperature)
 * - Processing overrides (dynamic contrast, tone mapping, black level)
 * - White balance 2-point (gains & offsets)
 * - White balance 20-point (per-IRE R/G/B corrections)
 * - CMS (Color Management System) per-color HSL adjustments
 */
class LgControlActivity : AppCompatActivity() {

    private lateinit var controller: LgTvController

    // Status
    private lateinit var tvLgCalStatus: TextView
    private lateinit var tvLog: TextView

    // Picture mode
    private lateinit var spinnerPictureMode: Spinner
    private lateinit var btnApplyPictureMode: Button

    // Basic picture settings
    private lateinit var etBacklight: EditText
    private lateinit var etContrast: EditText
    private lateinit var etBrightness: EditText
    private lateinit var etColor: EditText
    private lateinit var etSharpness: EditText

    // Color settings
    private lateinit var spinnerColorGamut: Spinner
    private lateinit var spinnerGamma: Spinner
    private lateinit var spinnerColorTemp: Spinner

    // WB 2-point
    private lateinit var etWbRedGain: EditText
    private lateinit var etWbGreenGain: EditText
    private lateinit var etWbBlueGain: EditText
    private lateinit var etWbRedOffset: EditText
    private lateinit var etWbGreenOffset: EditText
    private lateinit var etWbBlueOffset: EditText

    // WB 20-point
    private lateinit var spinnerWb20ptIre: Spinner
    private lateinit var etWb20ptRed: EditText
    private lateinit var etWb20ptGreen: EditText
    private lateinit var etWb20ptBlue: EditText

    // CMS
    private lateinit var spinnerCmsColor: Spinner
    private lateinit var etCmsHue: EditText
    private lateinit var etCmsSaturation: EditText
    private lateinit var etCmsLuminance: EditText

    private val logLines = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lg_control)

        controller = LgTvController(this)
        controller.onLog = { msg -> appendLog(msg) }

        initViews()
        setupSpinners()
        setupButtons()

        // Reconnect if not already connected
        checkConnection()
    }

    override fun onDestroy() {
        // Don't disconnect here - keep connection alive for main activity
        super.onDestroy()
    }

    private fun checkConnection() {
        if (!controller.isConnected) {
            val savedIp = controller.getSavedTvIp()
            if (savedIp != null) {
                appendLog("Reconnecting to $savedIp...")
                controller.ssapClient.onStatusChange = { status ->
                    runOnUiThread {
                        tvLgCalStatus.text = status
                    }
                }
                controller.ssapClient.onPairingRequired = {
                    runOnUiThread {
                        Toast.makeText(this, "Accept pairing on TV", Toast.LENGTH_LONG).show()
                    }
                }
                controller.connect(savedIp)
            } else {
                tvLgCalStatus.text = "Not connected - set TV IP in main screen"
                appendLog("No saved TV IP. Go back and connect first.")
            }
        } else {
            tvLgCalStatus.text = "Connected"
            controller.ssapClient.onStatusChange = { status ->
                runOnUiThread { tvLgCalStatus.text = status }
            }
        }
    }

    private fun initViews() {
        tvLgCalStatus = findViewById(R.id.tvLgCalStatus)
        tvLog = findViewById(R.id.tvLog)

        spinnerPictureMode = findViewById(R.id.spinnerPictureMode)
        btnApplyPictureMode = findViewById(R.id.btnApplyPictureMode)

        etBacklight = findViewById(R.id.etBacklight)
        etContrast = findViewById(R.id.etContrast)
        etBrightness = findViewById(R.id.etBrightness)
        etColor = findViewById(R.id.etColor)
        etSharpness = findViewById(R.id.etSharpness)

        spinnerColorGamut = findViewById(R.id.spinnerColorGamut)
        spinnerGamma = findViewById(R.id.spinnerGamma)
        spinnerColorTemp = findViewById(R.id.spinnerColorTemp)

        etWbRedGain = findViewById(R.id.etWbRedGain)
        etWbGreenGain = findViewById(R.id.etWbGreenGain)
        etWbBlueGain = findViewById(R.id.etWbBlueGain)
        etWbRedOffset = findViewById(R.id.etWbRedOffset)
        etWbGreenOffset = findViewById(R.id.etWbGreenOffset)
        etWbBlueOffset = findViewById(R.id.etWbBlueOffset)

        spinnerWb20ptIre = findViewById(R.id.spinnerWb20ptIre)
        etWb20ptRed = findViewById(R.id.etWb20ptRed)
        etWb20ptGreen = findViewById(R.id.etWb20ptGreen)
        etWb20ptBlue = findViewById(R.id.etWb20ptBlue)

        spinnerCmsColor = findViewById(R.id.spinnerCmsColor)
        etCmsHue = findViewById(R.id.etCmsHue)
        etCmsSaturation = findViewById(R.id.etCmsSaturation)
        etCmsLuminance = findViewById(R.id.etCmsLuminance)

        // Set defaults
        etBacklight.setText("100")
        etContrast.setText("85")
        etBrightness.setText("50")
        etColor.setText("50")
        etSharpness.setText("0")
        etWbRedGain.setText("0")
        etWbGreenGain.setText("0")
        etWbBlueGain.setText("0")
        etWbRedOffset.setText("0")
        etWbGreenOffset.setText("0")
        etWbBlueOffset.setText("0")
        etWb20ptRed.setText("0")
        etWb20ptGreen.setText("0")
        etWb20ptBlue.setText("0")
        etCmsHue.setText("0")
        etCmsSaturation.setText("0")
        etCmsLuminance.setText("0")
    }

    private fun setupSpinners() {
        // Picture mode
        val pictureModeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LgTvController.PICTURE_MODES)
        pictureModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerPictureMode.adapter = pictureModeAdapter
        // Default to expert1 (index 1)
        spinnerPictureMode.setSelection(1)

        // Color gamut
        val gamutAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LgTvController.COLOR_GAMUTS)
        gamutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerColorGamut.adapter = gamutAdapter

        // Gamma
        val gammaAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LgTvController.GAMMA_OPTIONS)
        gammaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGamma.adapter = gammaAdapter
        // Default to 2.2 (index 7)
        spinnerGamma.setSelection(7)

        // Color temperature
        val tempAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LgTvController.COLOR_TEMPS)
        tempAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerColorTemp.adapter = tempAdapter

        // WB 20-point IRE
        val ireLabels = LgTvController.WB_20PT_IRE_VALUES.map { "${it}% IRE" }.toTypedArray()
        val ireAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ireLabels)
        ireAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerWb20ptIre.adapter = ireAdapter

        // CMS colors
        val cmsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, LgTvController.CMS_COLORS)
        cmsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCmsColor.adapter = cmsAdapter
    }

    private fun setupButtons() {
        // Apply picture mode
        btnApplyPictureMode.setOnClickListener {
            val mode = LgTvController.PICTURE_MODES[spinnerPictureMode.selectedItemPosition]
            controller.setPictureMode(mode, simpleCallback("Picture mode"))
        }

        // Apply picture settings
        findViewById<Button>(R.id.btnApplyPictureSettings).setOnClickListener {
            val backlight = etBacklight.text.toString().toIntOrNull() ?: return@setOnClickListener
            val contrast = etContrast.text.toString().toIntOrNull() ?: return@setOnClickListener
            val brightness = etBrightness.text.toString().toIntOrNull() ?: return@setOnClickListener
            val color = etColor.text.toString().toIntOrNull() ?: return@setOnClickListener
            val sharpness = etSharpness.text.toString().toIntOrNull() ?: return@setOnClickListener
            controller.applyPictureSettings(backlight, contrast, brightness, color, sharpness,
                simpleCallback("Picture settings"))
        }

        // Apply color settings
        findViewById<Button>(R.id.btnApplyColorSettings).setOnClickListener {
            val gamut = LgTvController.COLOR_GAMUTS[spinnerColorGamut.selectedItemPosition]
            val gamma = LgTvController.GAMMA_OPTIONS[spinnerGamma.selectedItemPosition]
            val temp = LgTvController.COLOR_TEMPS[spinnerColorTemp.selectedItemPosition]
            controller.applyColorSettings(gamut, gamma, temp, simpleCallback("Color settings"))
        }

        // Processing overrides
        findViewById<Button>(R.id.btnDisableDynContrast).setOnClickListener {
            controller.setDynamicContrast(false, simpleCallback("Dynamic contrast"))
        }
        findViewById<Button>(R.id.btnDisableToneMapping).setOnClickListener {
            controller.setHdrDynamicToneMapping(false, simpleCallback("Tone mapping"))
        }
        findViewById<Button>(R.id.btnSetBlackLevelLow).setOnClickListener {
            controller.setBlackLevel("low", simpleCallback("Black level"))
        }
        findViewById<Button>(R.id.btnSetBlackLevelHigh).setOnClickListener {
            controller.setBlackLevel("high", simpleCallback("Black level"))
        }

        // Apply WB 2-point
        findViewById<Button>(R.id.btnApplyWb2pt).setOnClickListener {
            val rg = etWbRedGain.text.toString().toIntOrNull() ?: 0
            val gg = etWbGreenGain.text.toString().toIntOrNull() ?: 0
            val bg = etWbBlueGain.text.toString().toIntOrNull() ?: 0
            val ro = etWbRedOffset.text.toString().toIntOrNull() ?: 0
            val go = etWbGreenOffset.text.toString().toIntOrNull() ?: 0
            val bo = etWbBlueOffset.text.toString().toIntOrNull() ?: 0
            controller.setWhiteBalance2pt(rg, gg, bg, ro, go, bo, simpleCallback("WB 2-point"))
        }

        // Apply WB 20-point
        findViewById<Button>(R.id.btnApplyWb20pt).setOnClickListener {
            val index = spinnerWb20ptIre.selectedItemPosition
            val red = etWb20ptRed.text.toString().toIntOrNull() ?: 0
            val green = etWb20ptGreen.text.toString().toIntOrNull() ?: 0
            val blue = etWb20ptBlue.text.toString().toIntOrNull() ?: 0
            controller.setWhiteBalance20ptPoint(index, red, green, blue,
                simpleCallback("WB 20pt point $index"))
        }

        // Apply CMS
        findViewById<Button>(R.id.btnApplyCms).setOnClickListener {
            val color = LgTvController.CMS_COLORS[spinnerCmsColor.selectedItemPosition]
            val hue = etCmsHue.text.toString().toIntOrNull() ?: 0
            val sat = etCmsSaturation.text.toString().toIntOrNull() ?: 0
            val lum = etCmsLuminance.text.toString().toIntOrNull() ?: 0
            controller.setCmsColor(color, hue, sat, lum, simpleCallback("CMS $color"))
        }

        // Read settings
        findViewById<Button>(R.id.btnReadSettings).setOnClickListener {
            controller.readPictureSettings(object : SsapClient.ResponseCallback {
                override fun onResponse(response: JsonObject) {
                    val payload = response.getAsJsonObject("payload")
                    val settings = payload?.getAsJsonObject("settings")
                    if (settings != null) {
                        runOnUiThread { populateSettingsFromResponse(settings) }
                    }
                    runOnUiThread { appendLog("Settings read successfully") }
                }
                override fun onError(errorMessage: String) {
                    runOnUiThread { appendLog("Read error: $errorMessage") }
                }
            })
        }

        // Reset WB
        findViewById<Button>(R.id.btnResetWb).setOnClickListener {
            controller.resetWhiteBalance(simpleCallback("Reset WB"))
            etWbRedGain.setText("0"); etWbGreenGain.setText("0"); etWbBlueGain.setText("0")
            etWbRedOffset.setText("0"); etWbGreenOffset.setText("0"); etWbBlueOffset.setText("0")
        }

        // Reset CMS
        findViewById<Button>(R.id.btnResetCms).setOnClickListener {
            controller.resetCms(simpleCallback("Reset CMS"))
            etCmsHue.setText("0"); etCmsSaturation.setText("0"); etCmsLuminance.setText("0")
        }

        // Back
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    /**
     * Populate UI fields from a JSON response containing picture settings.
     */
    private fun populateSettingsFromResponse(settings: JsonObject) {
        try {
            settings.get("backlight")?.asString?.let { etBacklight.setText(it) }
            settings.get("contrast")?.asString?.let { etContrast.setText(it) }
            settings.get("brightness")?.asString?.let { etBrightness.setText(it) }
            settings.get("color")?.asString?.let { etColor.setText(it) }
            settings.get("sharpness")?.asString?.let { etSharpness.setText(it) }

            // Picture mode
            settings.get("pictureMode")?.asString?.let { mode ->
                val idx = LgTvController.PICTURE_MODES.indexOf(mode)
                if (idx >= 0) spinnerPictureMode.setSelection(idx)
            }

            // Color gamut
            settings.get("colorGamut")?.asString?.let { gamut ->
                val idx = LgTvController.COLOR_GAMUTS.indexOf(gamut)
                if (idx >= 0) spinnerColorGamut.setSelection(idx)
            }

            // Gamma
            settings.get("gamma")?.asString?.let { gamma ->
                val idx = LgTvController.GAMMA_OPTIONS.indexOf(gamma)
                if (idx >= 0) spinnerGamma.setSelection(idx)
            }

            // Color temperature
            settings.get("colorTemperature")?.asString?.let { temp ->
                val idx = LgTvController.COLOR_TEMPS.indexOf(temp)
                if (idx >= 0) spinnerColorTemp.setSelection(idx)
            }

            // WB 2-point
            settings.get("whiteBalanceRedGain")?.asString?.let { etWbRedGain.setText(it) }
            settings.get("whiteBalanceGreenGain")?.asString?.let { etWbGreenGain.setText(it) }
            settings.get("whiteBalanceBlueGain")?.asString?.let { etWbBlueGain.setText(it) }
            settings.get("whiteBalanceRedOffset")?.asString?.let { etWbRedOffset.setText(it) }
            settings.get("whiteBalanceGreenOffset")?.asString?.let { etWbGreenOffset.setText(it) }
            settings.get("whiteBalanceBlueOffset")?.asString?.let { etWbBlueOffset.setText(it) }

            appendLog("UI populated from TV settings")
        } catch (e: Exception) {
            appendLog("Error parsing settings: ${e.message}")
        }
    }

    private fun simpleCallback(operation: String): SsapClient.ResponseCallback {
        return object : SsapClient.ResponseCallback {
            override fun onResponse(response: JsonObject) {
                // Logging is handled by the controller's wrapper
            }
            override fun onError(errorMessage: String) {
                runOnUiThread {
                    Toast.makeText(this@LgControlActivity, "$operation failed: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            logLines.add(message)
            // Keep last 50 lines
            if (logLines.size > 50) {
                logLines.removeAt(0)
            }
            tvLog.text = logLines.joinToString("\n")

            // Auto-scroll
            val scrollView = tvLog.parent as? ScrollView
            scrollView?.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }
}
