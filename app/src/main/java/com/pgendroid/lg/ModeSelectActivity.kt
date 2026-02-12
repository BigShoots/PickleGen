package com.picklecal.lg

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Landing screen for PickleGen.
 * Lets the user choose between:
 * - Easy Mode: Connects to PickleCal Windows app. The Windows app controls everything.
 *   User just needs to install, select Easy, and follow on-screen instructions.
 * - Pro Mode: Standalone pattern generator for use with HCFR, Calman, DisplayCAL, etc.
 *   Full manual control over all settings.
 */
class ModeSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mode_select)

        val btnEasy = findViewById<View>(R.id.btnEasyMode)
        val btnPro = findViewById<View>(R.id.btnProMode)

        btnEasy.setOnClickListener {
            val intent = Intent(this, EasyModeActivity::class.java)
            startActivity(intent)
        }

        btnPro.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}
