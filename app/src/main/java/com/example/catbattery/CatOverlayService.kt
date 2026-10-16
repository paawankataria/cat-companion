package com.example.catbattery

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CatOverlayService : Service() {
    companion object {
        const val PREFERENCES = "cat_companion_preferences"
        const val ACTION_REFRESH = "com.example.catbattery.REFRESH_WIDGET"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private lateinit var windowManager: WindowManager
    private lateinit var widget: LinearLayout
    private lateinit var infoPanel: LinearLayout
    private lateinit var clockText: TextView
    private lateinit var batteryText: TextView
    private lateinit var healthText: TextView
    private lateinit var timezoneText: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var batteryReceiver: BroadcastReceiver? = null
    private var latestLevel = 0
    private var latestHealth = BatteryManager.BATTERY_HEALTH_UNKNOWN
    private var latestStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var viewAdded = false

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createWidget()
        registerBatteryReceiver()
        mainHandler.post(clockUpdater)
        try {
            windowManager.addView(widget, params)
            viewAdded = true
        } catch (_: Exception) {
            preferences.edit().putBoolean("widget_showing", false).apply()
            stopSelf()
        }
    }

    @Suppress("DEPRECATION")
    private fun registerBatteryReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
        }
        batteryReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { updateBattery(it) }
    }

    private fun createWidget() {
        widget = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            clipChildren = false
            clipToPadding = false
        }
        val cat = ImageView(this).apply {
            setImageResource(R.drawable.cat_companion)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Cat companion"
        }
        widget.addView(cat, LinearLayout.LayoutParams(dp(106), dp(116)))

        infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        clockText = text(18, Color.rgb(53, 43, 50), true)
        batteryText = text(11, Color.rgb(71, 59, 66))
        healthText = text(10, Color.rgb(95, 81, 88))
        timezoneText = text(9, Color.rgb(95, 81, 88))
        infoPanel.addView(clockText)
        infoPanel.addView(batteryText)
        infoPanel.addView(healthText)
        infoPanel.addView(timezoneText)
        widget.addView(infoPanel, LinearLayout.LayoutParams(dp(112), dp(112)).apply {
            marginStart = dp(3)
        })

        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(44)
        }
        widget.setOnTouchListener { _, event -> handleTouch(event) }
        updateDisplayedContent()
        updateClock()
        updateBatteryViews()
    }

    private fun text(size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = size.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(null, Typeface.BOLD)
        maxLines = 1
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
                initialX = params.x
                initialY = params.y
                moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (touchStartX - event.rawX).toInt()
                val dy = (event.rawY - touchStartY).toInt()
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(4)) moved = true
                params.x = (initialX + dx).coerceAtLeast(0)
                params.y = (initialY + dy).coerceAtLeast(0)
                if (viewAdded) try { windowManager.updateViewLayout(widget, params) } catch (_: Exception) { }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(open)
                }
                return true
            }
        }
        return true
    }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialX = 0
    private var initialY = 0
    private var moved = false

    private val clockUpdater = object : Runnable {
        override fun run() {
            updateClock()
            val delay = 60_000L - (System.currentTimeMillis() % 60_000L)
            mainHandler.postDelayed(this, delay)
        }
    }

    private fun updateClock() {
        if (!::clockText.isInitialized) return
        val is24Hour = preferences.getBoolean("use_24_hour", DateFormat.is24HourFormat(this))
        val format = if (is24Hour) "HH:mm" else "h:mm a"
        clockText.text = SimpleDateFormat(format, Locale.getDefault()).format(Date())
        timezoneText.text = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT, Locale.getDefault())
    }

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        latestLevel = if (scale > 0) level * 100 / scale else 0
        latestHealth = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        latestStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        updateBatteryViews()
    }

    private fun updateBatteryViews() {
        if (!::batteryText.isInitialized) return
        val charging = latestStatus == BatteryManager.BATTERY_STATUS_CHARGING || latestStatus == BatteryManager.BATTERY_STATUS_FULL
        batteryText.text = if (charging) "🔋 $latestLevel%  ⚡" else "🔋 $latestLevel%"
        healthText.text = "Health · ${healthLabel(latestHealth)}"
    }

    private fun healthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Needs attention"
        else -> "Unknown"
    }

    private fun updateDisplayedContent() {
        if (!::infoPanel.isInitialized) return
        clockText.visibility = visible("show_clock")
        batteryText.visibility = visible("show_battery")
        healthText.visibility = visible("show_health")
        timezoneText.visibility = visible("show_timezone")
        val color = preferences.getInt("tint_color", Color.rgb(177, 155, 217))
        val strength = preferences.getInt("tint_strength", 28).coerceIn(0, 60) / 100f
        val tint = blend(Color.rgb(255, 249, 247), color, strength)
        widget.background = null
        infoPanel.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(tint)
            setStroke(dp(1), blend(tint, Color.WHITE, 0.65f))
            alpha = 230
        }
        if (viewAdded) try { windowManager.updateViewLayout(widget, params) } catch (_: Exception) { }
    }

    private fun visible(key: String): Int = if (preferences.getBoolean(key, true)) android.view.View.VISIBLE else android.view.View.GONE

    private fun blend(base: Int, tint: Int, amount: Float): Int = Color.rgb(
        (Color.red(base) * (1f - amount) + Color.red(tint) * amount).toInt(),
        (Color.green(base) * (1f - amount) + Color.green(tint) * amount).toInt(),
        (Color.blue(base) * (1f - amount) + Color.blue(tint) * amount).toInt()
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            updateDisplayedContent()
            updateClock()
            updateBatteryViews()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(clockUpdater)
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        preferences.edit().putBoolean("widget_showing", false).apply()
        if (viewAdded) try { windowManager.removeView(widget) } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
