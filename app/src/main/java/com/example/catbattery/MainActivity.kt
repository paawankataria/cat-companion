package com.example.catbattery

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences(CatOverlayService.PREFERENCES, MODE_PRIVATE) }
    private lateinit var permissionText: TextView
    private lateinit var toggleButton: Button
    private var waitingForOverlayPermission = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(255, 249, 247)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(28))
        }
        scroll.addView(content)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.WHITE, 24)
            setPadding(dp(10), dp(10), dp(16), dp(10))
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.cat_rig_preview)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Gray and cream animated cat"
        }, LinearLayout.LayoutParams(dp(84), dp(92)))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        title.addView(label("Cat Companion", 23, Color.rgb(57, 46, 53), true))
        title.addView(label("A little animated friend", 14, Color.rgb(112, 95, 102)))
        hero.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(hero)

        content.addView(label("Your cat keeps the time, stretches, blinks, and curls up when left alone. Tap for a cuddle, drag to move, or press and hold for these settings.", 14, Color.rgb(108, 90, 98)).apply {
            setPadding(dp(2), dp(14), dp(2), dp(16))
        })

        permissionText = label("", 13, Color.rgb(103, 89, 96))
        content.addView(permissionText)
        content.addView(Button(this).apply {
            text = "Show / hide the cat"
            setOnClickListener { toggleWidget() }
            toggleButton = this
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
            bottomMargin = dp(16)
        })

        content.addView(sectionTitle("Clock"))
        content.addView(label("Choose whether the time appears on the cat.", 13, Color.rgb(108, 90, 98)))
        addPreferenceToggle(content, "Show clock", "show_clock", true)
        addPreferenceToggle(content, "Use 24-hour time", "use_24_hour", false)

        setContentView(scroll)
        refreshPageState()
    }

    override fun onResume() {
        super.onResume()
        refreshPageState()
        if (waitingForOverlayPermission && Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = false
            showWidget()
        }
    }

    private fun toggleWidget() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            waitingForOverlayPermission = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (preferences.getBoolean("widget_showing", false)) {
            stopService(Intent(this, CatOverlayService::class.java))
            preferences.edit().putBoolean("widget_showing", false).apply()
        } else showWidget()
        refreshPageState()
    }

    private fun showWidget() {
        preferences.edit().putBoolean("widget_showing", true).apply()
        startService(Intent(this, CatOverlayService::class.java))
        refreshPageState()
    }

    private fun refreshVisibleWidget() {
        if (preferences.getBoolean("widget_showing", false)) {
            startService(Intent(this, CatOverlayService::class.java).setAction(CatOverlayService.ACTION_REFRESH))
        }
    }

    private fun refreshPageState() {
        if (!::permissionText.isInitialized) return
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        permissionText.text = if (allowed) "Overlay permission: allowed" else "Overlay permission: needed to show the cat over other apps"
        toggleButton.text = if (preferences.getBoolean("widget_showing", false)) "Hide floating cat" else "Show floating cat"
    }

    private fun addPreferenceToggle(parent: LinearLayout, title: String, key: String, defaultValue: Boolean) {
        val box = CheckBox(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.rgb(70, 55, 63))
            buttonTintList = ColorStateList.valueOf(Color.rgb(164, 133, 189))
            isChecked = preferences.getBoolean(key, defaultValue)
            setOnCheckedChangeListener { _, checked ->
                preferences.edit().putBoolean(key, checked).apply()
                refreshVisibleWidget()
            }
        }
        parent.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)))
    }

    private fun sectionTitle(text: String) = label(text, 17, Color.rgb(57, 46, 53), true).apply {
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }
}
