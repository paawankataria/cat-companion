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
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences(CatOverlayService.PREFERENCES, MODE_PRIVATE) }
    private lateinit var permissionText: TextView
    private lateinit var toggleButton: Button
    private lateinit var tintValue: TextView
    private var waitingForOverlayPermission = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(255, 249, 247)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(28))
        }
        scroll.addView(content)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(255, 255, 255), 24)
            setPadding(dp(12), dp(10), dp(16), dp(10))
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.cat_rig_preview)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Your gray and white cat companion"
        }, LinearLayout.LayoutParams(dp(86), dp(96)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        heading.addView(label("Cat Companion", 25, Color.rgb(57, 46, 53), true))
        heading.addView(label("A little cat for your screen", 14, Color.rgb(112, 95, 102)))
        hero.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(hero)

        content.addView(label("A tiny animated cat that keeps the time and battery close. Tap for a cuddle; press and hold for settings.", 14, Color.rgb(108, 90, 98)).apply {
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
            bottomMargin = dp(20)
        })

        content.addView(sectionTitle("Soft color"))
        content.addView(label("Choose a soft accent for the battery glow on the cat's tail.", 13, Color.rgb(108, 90, 98)))
        val palette = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        }
        val colors = listOf(
            "Lilac" to Color.rgb(177, 155, 217),
            "Blush" to Color.rgb(230, 165, 177),
            "Mint" to Color.rgb(143, 197, 172),
            "Sky" to Color.rgb(145, 185, 222),
            "Peach" to Color.rgb(242, 181, 143)
        )
        colors.forEach { (name, color) ->
            val chip = TextView(this).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(64, 49, 58))
                setPadding(dp(3), dp(8), dp(3), dp(8))
                contentDescription = "$name tint"
                setOnClickListener {
                    preferences.edit().putInt("tint_color", color).apply()
                    updatePaletteSelection(palette, colors)
                    refreshVisibleWidget()
                }
            }
            palette.addView(chip, LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = dp(4)
            })
        }
        content.addView(palette)
        updatePaletteSelection(palette, colors)

        val strengthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        strengthRow.addView(label("Tint strength", 14, Color.rgb(70, 55, 63), true), LinearLayout.LayoutParams(0, dp(36), 1f))
        tintValue = label("", 13, Color.rgb(103, 89, 96))
        strengthRow.addView(tintValue)
        content.addView(strengthRow)
        val strength = SeekBar(this).apply {
            max = 60
            progress = preferences.getInt("tint_strength", 28).coerceIn(0, max)
            progressTintList = ColorStateList.valueOf(Color.rgb(164, 133, 189))
            thumbTintList = ColorStateList.valueOf(Color.rgb(164, 133, 189))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tintValue.text = "$progress%"
                    if (fromUser) {
                        preferences.edit().putInt("tint_strength", progress).apply()
                        refreshVisibleWidget()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        content.addView(strength)

        content.addView(sectionTitle("Show on the cat"))
        addPreferenceToggle(content, "Clock", "show_clock", true)
        addPreferenceToggle(content, "Battery level", "show_battery", true)
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
        } else {
            showWidget()
        }
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

    private fun updatePaletteSelection(palette: LinearLayout, colors: List<Pair<String, Int>>) {
        val selected = preferences.getInt("tint_color", colors[0].second)
        colors.forEachIndexed { index, (_, color) ->
            val chip = palette.getChildAt(index) as TextView
            chip.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(mix(Color.WHITE, color, 0.30f))
                setStroke(dp(if (selected == color) 2 else 1), if (selected == color) Color.rgb(115, 82, 137) else Color.rgb(230, 218, 222))
            }
        }
    }

    private fun sectionTitle(text: String) = label(text, 17, Color.rgb(57, 46, 53), true).apply {
        setPadding(0, dp(14), 0, dp(5))
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

    private fun mix(first: Int, second: Int, ratio: Float): Int = Color.rgb(
        (Color.red(first) * (1 - ratio) + Color.red(second) * ratio).toInt(),
        (Color.green(first) * (1 - ratio) + Color.green(second) * ratio).toInt(),
        (Color.blue(first) * (1 - ratio) + Color.blue(second) * ratio).toInt()
    )
}
