package com.example.catbattery

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CatOverlayService : Service() {
    companion object {
        const val PREFERENCES = "cat_companion_preferences"
        const val ACTION_REFRESH = "com.example.catbattery.REFRESH_WIDGET"
    }

    private val preferences by lazy { getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var cat: CatWidgetView
    private lateinit var params: WindowManager.LayoutParams
    private var receiver: BroadcastReceiver? = null
    private var viewAdded = false
    private var touchX = 0f
    private var touchY = 0f
    private var windowX = 0
    private var windowY = 0
    private var dragged = false
    private var longPressed = false
    private val longPress = Runnable {
        longPressed = true
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        cat = CatWidgetView(this, preferences)
        cat.setOnTouchListener { _, event -> handleTouch(event) }

        @Suppress("DEPRECATION")
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            dp(132), dp(148), windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(44)
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                cat.setBattery(if (scale > 0) level * 100 / scale else 0, status)
            }
        }
        receiver = batteryReceiver
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            batteryReceiver.onReceive(this, it)
        }
        applyPreferences()
        try {
            windowManager.addView(cat, params)
            viewAdded = true
        } catch (_: Exception) {
            preferences.edit().putBoolean("widget_showing", false).apply()
            stopSelf()
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.rawX
                touchY = event.rawY
                windowX = params.x
                windowY = params.y
                dragged = false
                longPressed = false
                handler.postDelayed(longPress, 550L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (touchX - event.rawX).toInt()
                val dy = (event.rawY - touchY).toInt()
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(6)) {
                    dragged = true
                    handler.removeCallbacks(longPress)
                    params.x = (windowX + dx).coerceAtLeast(0)
                    params.y = (windowY + dy).coerceAtLeast(0)
                    if (viewAdded) try { windowManager.updateViewLayout(cat, params) } catch (_: Exception) { }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!dragged && !longPressed) cat.playCuddle()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                return true
            }
        }
        return true
    }

    private fun applyPreferences() {
        if (::cat.isInitialized) cat.setOptions(
            showClock = preferences.getBoolean("show_clock", true),
            showBattery = preferences.getBoolean("show_battery", true),
            color = preferences.getInt("tint_color", Color.rgb(177, 155, 217)),
            tintStrength = preferences.getInt("tint_strength", 28).coerceIn(0, 60) / 60f
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) applyPreferences()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(longPress)
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        if (viewAdded) try { windowManager.removeView(cat) } catch (_: Exception) { }
        preferences.edit().putBoolean("widget_showing", false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Draws the supplied cat artwork and applies small, eased motion without replacing its design. */
private class CatWidgetView(context: Context, private val preferences: android.content.SharedPreferences) : View(context) {
    private val frames = intArrayOf(
        R.drawable.cat_animation_00, R.drawable.cat_animation_01, R.drawable.cat_animation_02,
        R.drawable.cat_animation_03, R.drawable.cat_animation_04, R.drawable.cat_animation_05
    ).map { android.graphics.BitmapFactory.decodeResource(resources, it) }
    private val density = resources.displayMetrics.density
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
    private val catRect = RectF()
    private val tailPath = Path()
    private var battery = 0
    private var displayedBattery = 0f
    private var status = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var accent = Color.rgb(177, 155, 217)
    private var tintStrength = 0.28f
    private var showClock = true
    private var showBattery = true
    private var lastInteraction = System.currentTimeMillis()
    private var cuddleStarted = 0L
    private var cuddleUntil = 0L
    private var lastFrame = 0
    private var targetFrame = 0
    private var transitionStarted = 0L
    private var transitionDuration = 420L
    private var lastBlink = System.currentTimeMillis() + 6_000L
    private var previousTick = System.currentTimeMillis()
    private var happyUntil = 0L
    private var happyStarted = 0L
    private var nextHappyMoment = System.currentTimeMillis() + 12_000L

    private val animator = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val delta = (now - previousTick).coerceIn(1L, 100L)
            previousTick = now
            val batteryEase = 1f - kotlin.math.exp(-delta / 260f)
            displayedBattery += (battery - displayedBattery) * batteryEase
            chooseMood()
            invalidate()
            postDelayed(this, 33L)
        }
    }

    init {
        contentDescription = "Animated cat with time and battery level. Tap to cuddle; hold for settings; drag to move."
        setLayerType(LAYER_TYPE_HARDWARE, null)
        post(animator)
    }

    fun setBattery(level: Int, batteryStatus: Int) {
        battery = level.coerceIn(0, 100)
        status = batteryStatus
        invalidate()
    }

    fun setOptions(showClock: Boolean, showBattery: Boolean, color: Int, tintStrength: Float) {
        this.showClock = showClock
        this.showBattery = showBattery
        accent = color
        this.tintStrength = tintStrength * 0.55f
        invalidate()
    }

    fun playCuddle() {
        lastInteraction = System.currentTimeMillis()
        cuddleStarted = lastInteraction
        cuddleUntil = lastInteraction + 2_400L
        transitionTo(1, 250L)
    }

    private fun chooseMood() {
        val now = System.currentTimeMillis()
        if (now < cuddleUntil) {
            val elapsed = now - cuddleStarted
            transitionTo(when {
                elapsed < 700L -> 1 // paw-to-face greeting
                elapsed < 1_500L -> 2 // playful paws-up pose
                else -> 3 // content, eyes-closed cuddle
            }, 250L)
            return
        }
        val next = when {
            battery < 20 && status != BatteryManager.BATTERY_STATUS_CHARGING -> 4
            status == BatteryManager.BATTERY_STATUS_CHARGING -> {
                if (now >= nextHappyMoment && now >= happyUntil) {
                    happyStarted = now
                    happyUntil = now + 2_100L
                    nextHappyMoment = now + 14_000L
                }
                when {
                    now >= happyUntil -> 2
                    now - happyStarted < 700L -> 1
                    now - happyStarted < 1_400L -> 2
                    else -> 3
                }
            }
            now - lastInteraction > 60_000L -> 3
            status == BatteryManager.BATTERY_STATUS_FULL -> 2
            now < lastBlink -> 0
            now - lastBlink < 360L -> 3
            else -> { lastBlink = now + 6_000L; 0 }
        }
        transitionTo(next)
    }

    private fun transitionTo(frame: Int, duration: Long = 420L) {
        if (targetFrame == frame) return
        lastFrame = targetFrame
        targetFrame = frame
        transitionStarted = System.currentTimeMillis()
        transitionDuration = duration
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val linearTransition = ((now - transitionStarted) / transitionDuration.toFloat()).coerceIn(0f, 1f)
        val transition = linearTransition * linearTransition * (3f - 2f * linearTransition)
        val sleeping = targetFrame == 3 && System.currentTimeMillis() - lastInteraction > 60_000L
        val breathe = if (sleeping) 0.004f else 0.009f
        val pulse = kotlin.math.sin(now / if (sleeping) 1_500f else 1_050f).toFloat() * breathe
        val width = width.toFloat()
        val height = height.toFloat()
        val pad = 4f * density
        catRect.set(pad, pad + height * 0.015f + height * pulse, width - pad, height - pad)
        canvas.save()
        canvas.scale(1f + pulse * 0.3f, 1f + pulse, catRect.centerX(), catRect.centerY())
        if (transition < 1f) {
            imagePaint.alpha = ((1f - transition) * 255).toInt()
            canvas.drawBitmap(frames[lastFrame], null, catRect, imagePaint)
        }
        imagePaint.alpha = (transition * 255).toInt()
        canvas.drawBitmap(frames[targetFrame], null, catRect, imagePaint)
        imagePaint.alpha = 255
        canvas.restore()

        drawTailBattery(canvas)
        if (showClock) drawClock(canvas)
        if (showBattery) drawPercent(canvas)
    }

    private fun drawTailBattery(canvas: Canvas) {
        val left = catRect.left
        val top = catRect.top
        val w = catRect.width()
        val h = catRect.height()
        tailPath.reset()
        tailPath.moveTo(left + w * 0.80f, top + h * 0.64f)
        tailPath.cubicTo(left + w * 0.96f, top + h * 0.66f, left + w * 0.96f, top + h * 0.78f, left + w * 0.84f, top + h * 0.80f)
        linePaint.strokeWidth = 3.2f * density
        linePaint.color = Color.argb(100, 75, 66, 68)
        canvas.drawPath(tailPath, linePaint)
        linePaint.color = blend(Color.rgb(255, 249, 247), accent, tintStrength.coerceAtLeast(0.15f))
        linePaint.alpha = 240
        linePaint.strokeWidth = 2.4f * density
        val measure = android.graphics.PathMeasure(tailPath, false)
        val fill = Path()
        measure.getSegment(0f, measure.length * displayedBattery / 100f, fill, true)
        canvas.drawPath(fill, linePaint)
        linePaint.alpha = 255
    }

    private fun drawClock(canvas: Canvas) {
        val use24Hour = preferences.getBoolean("use_24_hour", DateFormat.is24HourFormat(context))
        val format = if (use24Hour) "HH:mm" else "h:mm a"
        val clock = SimpleDateFormat(format, Locale.getDefault()).format(Date())
        textPaint.textSize = 12f * density
        textPaint.color = Color.rgb(58, 48, 52)
        textPaint.setShadowLayer(2f * density, 0f, 1f * density, Color.WHITE)
        canvas.drawText(clock, catRect.centerX(), catRect.top + catRect.height() * 0.70f, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawPercent(canvas: Canvas) {
        textPaint.textSize = 9f * density
        textPaint.color = Color.rgb(58, 48, 52)
        textPaint.setShadowLayer(2f * density, 0f, 1f * density, Color.WHITE)
        canvas.drawText("$battery%", catRect.left + catRect.width() * 0.89f, catRect.top + catRect.height() * 0.75f, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun blend(base: Int, tint: Int, amount: Float): Int = Color.rgb(
        (Color.red(base) * (1f - amount) + Color.red(tint) * amount).toInt(),
        (Color.green(base) * (1f - amount) + Color.green(tint) * amount).toInt(),
        (Color.blue(base) * (1f - amount) + Color.blue(tint) * amount).toInt()
    )

    override fun onDetachedFromWindow() {
        removeCallbacks(animator)
        super.onDetachedFromWindow()
    }
}
