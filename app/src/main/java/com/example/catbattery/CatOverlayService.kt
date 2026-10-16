package com.example.catbattery

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView

class CatOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var badge: TextView
    private lateinit var params: WindowManager.LayoutParams
    private var batteryReceiver: BroadcastReceiver? = null

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        badge = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(5), dp(9), dp(5))
            background = PinkPill()
        }
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(38)
        }
        badge.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (touchStartX - event.rawX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(4)) moved = true
                    params.x = (initialX + dx).coerceAtLeast(0)
                    params.y = (initialY + dy).coerceAtLeast(0)
                    windowManager.updateViewLayout(badge, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) stopSelf()
                    true
                }
                else -> true
            }
        }
        try {
            windowManager.addView(badge, params)
        } catch (_: Exception) {
            stopSelf()
            return
        }
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = updateBattery(intent)
        }
        @Suppress("DEPRECATION")
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialX = 0
    private var initialY = 0
    private var moved = false

    private fun updateBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val percent = if (scale > 0) level * 100 / scale else 0
        badge.text = "ฅ^•ﻌ•^ฅ  $percent%"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        batteryReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        if (::badge.isInitialized) try { windowManager.removeView(badge) } catch (_: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class PinkPill : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 90, 131) }
        override fun draw(canvas: Canvas) {
            val bounds: Rect = bounds
            canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), bounds.height() / 2f, bounds.height() / 2f, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated by Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
