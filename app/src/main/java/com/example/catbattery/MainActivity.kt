package com.example.catbattery

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private var waitingForOverlayPermission = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(Color.rgb(255, 248, 250))
        }
        root.addView(TextView(this).apply {
            text = "ฅ^•ﻌ•^ฅ"
            textSize = 38f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(214, 90, 131))
        })
        root.addView(TextView(this).apply {
            text = "Cat Battery"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(64, 45, 54))
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "A tiny floating cat shows your battery percentage.\n\nAndroid does not let regular apps replace the OnePlus status bar battery icon. This cat sits just below it instead."
            textSize = 16f
            setTextColor(Color.rgb(94, 78, 85))
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(22))
        })
        root.addView(Button(this).apply {
            text = "Enable cat battery"
            setOnClickListener { enableOverlay() }
        })
        root.addView(TextView(this).apply {
            text = "Drag the cat to move it. Tap it to hide it."
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false
            startService(Intent(this, CatOverlayService::class.java))
            finish()
        }
    }

    private fun enableOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            startService(Intent(this, CatOverlayService::class.java))
            finish()
        }
    }
}
