package com.picklecal.lg.lgcontrol

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * High-level LG TV controller wrapping the SSAP client.
 * Provides calibration-focused operations for LG webOS TVs.
 *
 * Supports:
 * - Picture mode selection
 * - Basic picture settings (backlight, contrast, brightness, color, sharpness)
 * - Color management (gamut, gamma, color temperature)
 * - Processing disable (dynamic contrast, tone mapping, black level)
 * - White balance 2-point (gains & offsets)
 * - White balance 20-point (per-IRE R/G/B corrections)
 * - CMS (Color Management System) per-color HSL adjustments
 */
class LgTvController(context: Context) {

    companion object {
        private const val TAG = "LgTvController"

        // Picture modes available on LG C2
        val PICTURE_MODES = arrayOf(
            "cinema", "expert1", "expert2", "filmmaker",
            "game", "sports", "vivid", "standard",
            "eco", "hdr cinema", "hdr game", "hdr vivid",
            "hdr filmmaker", "hdr standard", "dolbyHdrCinema",
            "dolbyHdrGame", "dolbyHdrVivid"
        )

        // Color gamut options
        val COLOR_GAMUTS = arrayOf(
            "auto", "extended", "wide", "srgb", "native", "adobe", "bt2020"
        )

        // Gamma options
        val GAMMA_OPTIONS = arrayOf(
            "low", "medium", "high1", "high2",
            "1.9", "2.0", "2.1", "2.2", "2.4", "2.6", "bt1886"
        )

        // Color temperature options
        val COLOR_TEMPS = arrayOf(
            "warm50", "warm40", "warm30", "warm20", "warm10",
            "medium", "cool10", "cool20", "cool30", "cool40", "cool50"
        )

        // CMS color names
        val CMS_COLORS = arrayOf("Red", "Green", "Blue", "Cyan", "Magenta", "Yellow")

        // 20-point WB IRE values (0-100 in 5% steps)
        val WB_20PT_IRE_VALUES = (0..100 step 5).toList().toTypedArray()
    }

    val ssapClient = SsapClient(context)

    var onLog: ((String) -> Unit)? = null

    private fun log(msg: String) {
        Log.i(TAG, msg)
        onLog?.invoke(msg)
    }

    // ---- Connection ----

    fun connect(tvIp: String, useSecure: Boolean = true) {
        ssapClient.connect(tvIp, useSecure)
    }

    fun disconnect() {
        ssapClient.disconnect()
    }

    val isConnected: Boolean get() = ssapClient.isConnected
    val isPaired: Boolean get() = ssapClient.isPaired

    // ---- Picture Mode ----

    fun setPictureMode(mode: String, callback: SsapClient.ResponseCallback? = null) {
        log("Setting picture mode: $mode")
        ssapClient.setSystemSettings("picture", mapOf("pictureMode" to mode),
            wrapCallback("setPictureMode", callback))
    }

    // ---- Basic Picture Settings ----

    fun setBacklight(value: Int, callback: SsapClient.ResponseCallback? = null) {
        log("Setting backlight: $value")
        ssapClient.setSystemSettings("picture", mapOf("backlight" to value.toString()),
            wrapCallback("setBacklight", callback))
    }

    fun setContrast(value: Int, callback: SsapClient.ResponseCallback? = null) {
        log("Setting contrast: $value")
        ssapClient.setSystemSettings("picture", mapOf("contrast" to value.toString()),
            wrapCallback("setContrast", callback))
    }

    fun setBrightness(value: Int, callback: SsapClient.ResponseCallback? = null) {
        log("Setting brightness: $value")
        ssapClient.setSystemSettings("picture", mapOf("brightness" to value.toString()),
            wrapCallback("setBrightness", callback))
    }

    fun setColor(value: Int, callback: SsapClient.ResponseCallback? = null) {
        log("Setting color: $value")
        ssapClient.setSystemSettings("picture", mapOf("color" to value.toString()),
            wrapCallback("setColor", callback))
    }

    fun setSharpness(value: Int, callback: SsapClient.ResponseCallback? = null) {
        log("Setting sharpness: $value")
        ssapClient.setSystemSettings("picture", mapOf("sharpness" to value.toString()),
            wrapCallback("setSharpness", callback))
    }

    /**
     * Apply multiple basic picture settings at once.
     */
    fun applyPictureSettings(
        backlight: Int, contrast: Int, brightness: Int, color: Int, sharpness: Int,
        callback: SsapClient.ResponseCallback? = null
    ) {
        log("Applying picture settings: BL=$backlight C=$contrast B=$brightness Col=$color S=$sharpness")
        ssapClient.setSystemSettings("picture", mapOf(
            "backlight" to backlight.toString(),
            "contrast" to contrast.toString(),
            "brightness" to brightness.toString(),
            "color" to color.toString(),
            "sharpness" to sharpness.toString()
        ), wrapCallback("applyPictureSettings", callback))
    }

    // ---- Color / Gamut / Gamma ----

    fun setColorGamut(gamut: String, callback: SsapClient.ResponseCallback? = null) {
        log("Setting color gamut: $gamut")
        ssapClient.setSystemSettings("picture", mapOf("colorGamut" to gamut),
            wrapCallback("setColorGamut", callback))
    }

    fun setGamma(gamma: String, callback: SsapClient.ResponseCallback? = null) {
        log("Setting gamma: $gamma")
        ssapClient.setSystemSettings("picture", mapOf("gamma" to gamma),
            wrapCallback("setGamma", callback))
    }

    fun setColorTemperature(temp: String, callback: SsapClient.ResponseCallback? = null) {
        log("Setting color temperature: $temp")
        ssapClient.setSystemSettings("picture", mapOf("colorTemperature" to temp),
            wrapCallback("setColorTemperature", callback))
    }

    fun applyColorSettings(
        gamut: String, gamma: String, colorTemp: String,
        callback: SsapClient.ResponseCallback? = null
    ) {
        log("Applying color settings: gamut=$gamut gamma=$gamma temp=$colorTemp")
        ssapClient.setSystemSettings("picture", mapOf(
            "colorGamut" to gamut,
            "gamma" to gamma,
            "colorTemperature" to colorTemp
        ), wrapCallback("applyColorSettings", callback))
    }

    // ---- Processing Overrides ----

    fun setDynamicContrast(enabled: Boolean, callback: SsapClient.ResponseCallback? = null) {
        val value = if (enabled) "on" else "off"
        log("Setting dynamic contrast: $value")
        ssapClient.setSystemSettings("picture", mapOf("dynamicContrast" to value),
            wrapCallback("setDynamicContrast", callback))
    }

    fun setHdrDynamicToneMapping(enabled: Boolean, callback: SsapClient.ResponseCallback? = null) {
        val value = if (enabled) "on" else "off"
        log("Setting HDR dynamic tone mapping: $value")
        ssapClient.setSystemSettings("picture", mapOf("hdrDynamicToneMapping" to value),
            wrapCallback("setHdrDynamicToneMapping", callback))
    }

    fun setBlackLevel(level: String, callback: SsapClient.ResponseCallback? = null) {
        log("Setting black level: $level")
        ssapClient.setSystemSettings("picture", mapOf("blackLevel" to level),
            wrapCallback("setBlackLevel", callback))
    }

    /**
     * Disable all image processing for calibration.
     * Sets: dynamic contrast off, tone mapping off, sharpness 0, color temp warm50
     */
    fun disableAllProcessing(callback: SsapClient.ResponseCallback? = null) {
        log("Disabling all picture processing for calibration")
        ssapClient.setSystemSettings("picture", mapOf(
            "dynamicContrast" to "off",
            "hdrDynamicToneMapping" to "off",
            "sharpness" to "0",
            "noiseReduction" to "off",
            "mpegNoiseReduction" to "off",
            "smoothGradation" to "off",
            "realCinema" to "off"
        ), wrapCallback("disableAllProcessing", callback))
    }

    // ---- White Balance 2-Point ----

    fun setWhiteBalance2pt(
        redGain: Int, greenGain: Int, blueGain: Int,
        redOffset: Int, greenOffset: Int, blueOffset: Int,
        callback: SsapClient.ResponseCallback? = null
    ) {
        log("Setting WB 2pt: RG=$redGain GG=$greenGain BG=$blueGain RO=$redOffset GO=$greenOffset BO=$blueOffset")
        ssapClient.setSystemSettings("picture", mapOf(
            "whiteBalanceRedGain" to redGain.toString(),
            "whiteBalanceGreenGain" to greenGain.toString(),
            "whiteBalanceBlueGain" to blueGain.toString(),
            "whiteBalanceRedOffset" to redOffset.toString(),
            "whiteBalanceGreenOffset" to greenOffset.toString(),
            "whiteBalanceBlueOffset" to blueOffset.toString(),
            "whiteBalanceMethod" to "2",
            "whiteBalanceColorTemperature" to "warm50"
        ), wrapCallback("setWhiteBalance2pt", callback))
    }

    // ---- White Balance 20-Point ----

    /**
     * Set a single point in the 20-point white balance.
     * @param index IRE step index (0-21 in the whiteBalanceRed array, mapping to 0-100% IRE)
     * @param red Red correction value
     * @param green Green correction value
     * @param blue Blue correction value
     */
    fun setWhiteBalance20ptPoint(
        index: Int, red: Int, green: Int, blue: Int,
        callback: SsapClient.ResponseCallback? = null
    ) {
        log("Setting WB 20pt point $index: R=$red G=$green B=$blue")

        // The TV uses whiteBalancePoint to select which IRE point to modify
        // and whiteBalanceRed/Green/Blue arrays contain all 22 values
        val settings = mapOf(
            "whiteBalanceMethod" to "20",
            "whiteBalancePoint" to index.toString(),
            "whiteBalanceColorTemperature" to "warm50",
            "whiteBalanceIre" to (index * 5).toString(),
            "whiteBalanceRed" to red.toString(),
            "whiteBalanceGreen" to green.toString(),
            "whiteBalanceBlue" to blue.toString()
        )

        ssapClient.setSystemSettings("picture", settings,
            wrapCallback("setWhiteBalance20ptPoint", callback))
    }

    // ---- CMS (Color Management System) ----

    /**
     * Set CMS adjustments for a specific color.
     * @param color One of: Red, Green, Blue, Cyan, Magenta, Yellow
     * @param hue Hue adjustment value
     * @param saturation Saturation adjustment value
     * @param luminance Luminance adjustment value
     */
    fun setCmsColor(
        color: String, hue: Int, saturation: Int, luminance: Int,
        callback: SsapClient.ResponseCallback? = null
    ) {
        log("Setting CMS $color: H=$hue S=$saturation L=$luminance")
        ssapClient.setSystemSettings("picture", mapOf(
            "colorManagement${color}Hue" to hue.toString(),
            "colorManagement${color}Saturation" to saturation.toString(),
            "colorManagement${color}Luminance" to luminance.toString()
        ), wrapCallback("setCmsColor", callback))
    }

    /**
     * Reset CMS for all colors to zero.
     */
    fun resetCms(callback: SsapClient.ResponseCallback? = null) {
        log("Resetting CMS to defaults")
        val settings = mutableMapOf<String, Any>()
        for (color in CMS_COLORS) {
            settings["colorManagement${color}Hue"] = "0"
            settings["colorManagement${color}Saturation"] = "0"
            settings["colorManagement${color}Luminance"] = "0"
        }
        ssapClient.setSystemSettings("picture", settings,
            wrapCallback("resetCms", callback))
    }

    /**
     * Reset white balance to defaults.
     */
    fun resetWhiteBalance(callback: SsapClient.ResponseCallback? = null) {
        log("Resetting white balance to defaults")
        ssapClient.setSystemSettings("picture", mapOf(
            "whiteBalanceRedGain" to "0",
            "whiteBalanceGreenGain" to "0",
            "whiteBalanceBlueGain" to "0",
            "whiteBalanceRedOffset" to "0",
            "whiteBalanceGreenOffset" to "0",
            "whiteBalanceBlueOffset" to "0"
        ), wrapCallback("resetWhiteBalance", callback))
    }

    // ---- Read Settings ----

    /**
     * Read current picture settings from the TV.
     */
    fun readPictureSettings(callback: SsapClient.ResponseCallback? = null) {
        log("Reading picture settings...")
        ssapClient.getSystemSettings("picture", listOf(
            "pictureMode", "backlight", "contrast", "brightness", "color",
            "sharpness", "colorGamut", "gamma", "colorTemperature",
            "dynamicContrast", "hdrDynamicToneMapping", "blackLevel",
            "whiteBalanceRedGain", "whiteBalanceGreenGain", "whiteBalanceBlueGain",
            "whiteBalanceRedOffset", "whiteBalanceGreenOffset", "whiteBalanceBlueOffset"
        ), wrapCallback("readPictureSettings", callback))
    }

    // ---- Utility ----

    fun showToast(message: String) {
        ssapClient.showToast(message)
    }

    private fun wrapCallback(
        operation: String,
        userCallback: SsapClient.ResponseCallback?
    ): SsapClient.ResponseCallback {
        return object : SsapClient.ResponseCallback {
            override fun onResponse(response: JsonObject) {
                val payload = response.getAsJsonObject("payload")
                val returnValue = payload?.get("returnValue")?.asBoolean ?: false
                if (returnValue) {
                    log("$operation: OK")
                } else {
                    val errorText = payload?.get("errorText")?.asString ?: "Unknown error"
                    log("$operation: FAILED - $errorText")
                }
                userCallback?.onResponse(response)
            }

            override fun onError(errorMessage: String) {
                log("$operation: ERROR - $errorMessage")
                userCallback?.onError(errorMessage)
            }
        }
    }

    fun getSavedTvIp(): String? = ssapClient.getSavedTvIp()
}
