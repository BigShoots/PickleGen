package com.picklecal.lg.hdr

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window
import android.view.WindowManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * HDR mode controller for Android TV boxes with Amlogic chipsets.
 */
object HdrController {
    private const val TAG = "HdrController"

    private const val AMHDMITX_PATH = "/sys/class/amhdmitx/amhdmitx0"
    private const val HDR_CAP_PATH = "$AMHDMITX_PATH/hdr_cap"
    private const val ATTR_PATH = "$AMHDMITX_PATH/attr"
    private const val CONFIG_PATH = "$AMHDMITX_PATH/config"
    private const val HDR_MODE_PATH = "/sys/module/am_vecm/parameters/hdr_mode"
    private const val FORCE_HDR_PATH = "/sys/module/am_vecm/parameters/force_hdr"
    private const val DISPLAY_MODE_PATH = "/sys/class/display/mode"

    data class HdrInfo(
        val isHdrCapable: Boolean,
        val supportsHdr10: Boolean,
        val supportsHlg: Boolean,
        val supportsDolbyVision: Boolean,
        val isAmlogicDevice: Boolean,
        val currentMode: String?,
        val currentColorDepth: String?
    )

    fun getHdrInfo(activity: Activity): HdrInfo {
        val isAmlogic = isAmlogicDevice()
        var supportsHdr10 = false
        var supportsHlg = false
        var supportsDv = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val display = activity.windowManager.defaultDisplay
            val hdrCaps = display.hdrCapabilities
            if (hdrCaps != null) {
                val types = hdrCaps.supportedHdrTypes
                supportsHdr10 = types.contains(Display.HdrCapabilities.HDR_TYPE_HDR10)
                supportsHlg = types.contains(Display.HdrCapabilities.HDR_TYPE_HLG)
                supportsDv = types.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
            }
        }

        if (isAmlogic) {
            val hdrCap = readSysfs(HDR_CAP_PATH)
            if (hdrCap != null) {
                if (hdrCap.contains("HDR10")) supportsHdr10 = true
                if (hdrCap.contains("HLG")) supportsHlg = true
                if (hdrCap.contains("DolbyVision")) supportsDv = true
            }
        }

        val currentMode = if (isAmlogic) readSysfs(DISPLAY_MODE_PATH) else null
        val currentColorDepth = if (isAmlogic) readSysfs(ATTR_PATH) else null

        return HdrInfo(
            isHdrCapable = supportsHdr10 || supportsHlg || supportsDv,
            supportsHdr10 = supportsHdr10,
            supportsHlg = supportsHlg,
            supportsDolbyVision = supportsDv,
            isAmlogicDevice = isAmlogic,
            currentMode = currentMode?.trim(),
            currentColorDepth = currentColorDepth?.trim()
        )
    }

    fun setHdrMode(activity: Activity, hdr: Boolean, bitDepth: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val window = activity.window
                if (hdr) {
                    window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                    Log.i(TAG, "Set window color mode to wide color gamut")
                } else {
                    window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
                    Log.i(TAG, "Set window color mode to default")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set window color mode", e)
            }
        }

        if (isAmlogicDevice()) {
            setAmlogicHdrMode(hdr, bitDepth)
        }
    }

    fun setHdrMetadata(maxCLL: Int, maxFALL: Int, maxDML: Int) {
        if (!isAmlogicDevice()) {
            Log.w(TAG, "HDR metadata setting only supported on Amlogic devices")
            return
        }

        val metadataPath = "$AMHDMITX_PATH/hdr_mdata"
        if (File(metadataPath).exists()) {
            writeSysfs(metadataPath, "$maxCLL $maxFALL $maxDML")
            Log.i(TAG, "Set HDR metadata: MaxCLL=$maxCLL MaxFALL=$maxFALL MaxDML=$maxDML")
        } else {
            Log.w(TAG, "HDR metadata sysfs path not available: $metadataPath")
        }
    }

    private fun setAmlogicHdrMode(hdr: Boolean, bitDepth: Int) {
        try {
            if (hdr) {
                writeSysfs(HDR_MODE_PATH, "1")
                val currentAttr = readSysfs(ATTR_PATH)
                if (currentAttr != null) {
                    val baseMode = currentAttr.split(",")[0]
                    val newAttr = if (bitDepth == 10) {
                        "$baseMode,422,12bit,hdr"
                    } else {
                        "$baseMode,444,8bit,hdr"
                    }
                    writeSysfs(ATTR_PATH, newAttr)
                    Log.i(TAG, "Set Amlogic HDR mode: $newAttr")
                }
            } else {
                writeSysfs(HDR_MODE_PATH, "0")
                Log.i(TAG, "Disabled Amlogic HDR mode")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Amlogic HDR mode (may need root)", e)
        }
    }

    fun keepScreenOn(window: Window, keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun isAmlogicDevice(): Boolean {
        if (File(AMHDMITX_PATH).exists()) return true
        val hardware = getSystemProperty("ro.hardware")
        if (hardware != null && hardware.lowercase().contains("amlogic")) return true
        val platform = getSystemProperty("ro.board.platform")
        if (platform != null && platform.lowercase().let {
                it.contains("meson") || it.contains("amlogic") || it.contains("gxl") ||
                        it.contains("gxm") || it.contains("g12a") || it.contains("g12b") ||
                        it.contains("sm1") || it.contains("sc2") || it.contains("t7") ||
                        it.contains("s4") || it.contains("s5")
            }) return true
        return false
    }

    fun getDisplayResolution(): Pair<Int, Int> {
        if (isAmlogicDevice()) {
            val mode = readSysfs(DISPLAY_MODE_PATH)?.trim()
            if (mode != null) {
                return when {
                    mode.startsWith("2160") -> Pair(3840, 2160)
                    mode.startsWith("1080") -> Pair(1920, 1080)
                    mode.startsWith("720") -> Pair(1280, 720)
                    mode.startsWith("4k2k") -> Pair(3840, 2160)
                    else -> Pair(1920, 1080)
                }
            }
        }
        return Pair(1920, 1080)
    }

    private fun readSysfs(path: String): String? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) file.readText()
            else execCommand("cat $path")
        } catch (e: Exception) { null }
    }

    private fun writeSysfs(path: String, value: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && file.canWrite()) {
                file.writeText(value)
                true
            } else {
                execCommand("echo '$value' > $path") != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to $path", e)
            false
        }
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", key))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.waitFor()
            result
        } catch (e: Exception) { null }
    }

    private fun execCommand(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readText()
            process.waitFor()
            if (process.exitValue() == 0) result else null
        } catch (e: Exception) { null }
    }
}