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

/** A tiny canvas rig: separate torso, paws, head expression, tail and battery fill. */
private class CatWidgetView(context: Context, private val preferences: android.content.SharedPreferences) : View(context) {
    private fun load(drawable: Int) = android.graphics.BitmapFactory.decodeResource(
        resources, drawable, android.graphics.BitmapFactory.Options().apply { inScaled = false }
    )

    private val body = load(R.drawable.cat_body_layer)
    private val paws = load(R.drawable.cat_paws_layer)
    private val hindPaws = load(R.drawable.cat_hind_paws_layer)
    private val tail = load(R.drawable.cat_tail_layer)
    private val heads = listOf(
        load(R.drawable.cat_expression_neutral),
        load(R.drawable.cat_expression_sad),
        load(R.drawable.cat_expression_happy),
        load(R.drawable.cat_expression_sleep)
    )
    private val density = resources.displayMetrics.density
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private val catRect = RectF()
    private val bodyRect = RectF()
    private val headRect = RectF()
    private val pawsRect = RectF()
    private val tailRect = RectF()
    private val tailPath = Path()

    private enum class Mood { CALM, LOW_BATTERY, CHARGING, SLEEPING, CUDDLE }
    private var mood = Mood.CALM
    private var battery = 0
    private var smoothBattery = 0f
    private var batteryStatus = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var accent = Color.rgb(177, 155, 217)
    private var tintStrength = 0.28f
    private var showClock = true
    private var showBattery = true
    private var lastInteraction = System.currentTimeMillis()
    private var cuddleStarted = 0L
    private var cuddleUntil = 0L
    private var currentHead = 0
    private var targetHead = 0
    private var headTransitionStarted = 0L
    private var headTransitionDuration = 420L
    private var nextBlink = System.currentTimeMillis() + 6_000L
    private var blinkUntil = 0L
    private var nextHappyMoment = System.currentTimeMillis() + 12_000L
    private var happyStarted = 0L
    private var previousFrameTime = System.currentTimeMillis()

    private val animator = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val elapsedMs = (now - previousFrameTime).coerceIn(1L, 100L)
            previousFrameTime = now
            val smoothing = 1f - kotlin.math.exp(-elapsedMs / 260f)
            smoothBattery += (battery - smoothBattery) * smoothing
            updateMood(now)
            invalidate()
            postDelayed(this, 33L)
        }
    }

    init {
        contentDescription = "Animated cat with time and battery. Tap for a cuddle, hold for settings, drag to move."
        setLayerType(LAYER_TYPE_HARDWARE, null)
        post(animator)
    }

    fun setBattery(level: Int, status: Int) {
        battery = level.coerceIn(0, 100)
        batteryStatus = status
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
        val now = System.currentTimeMillis()
        lastInteraction = now
        cuddleStarted = now
        cuddleUntil = now + 2_400L
    }

    private fun updateMood(now: Long) {
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
        val nextMood = when {
            now < cuddleUntil -> Mood.CUDDLE
            battery < 20 && !charging -> Mood.LOW_BATTERY
            now - lastInteraction > 60_000L -> Mood.SLEEPING
            charging -> Mood.CHARGING
            else -> Mood.CALM
        }
        if (nextMood != mood) {
            mood = nextMood
            if (mood == Mood.CHARGING) nextHappyMoment = now + 7_000L
        }

        if (mood == Mood.CHARGING && now >= nextHappyMoment) {
            happyStarted = now
            nextHappyMoment = now + 15_000L
        }

        if (mood == Mood.CALM && now >= nextBlink) {
            blinkUntil = now + 300L
            nextBlink = now + 7_000L
        }

        val expression = when (mood) {
            Mood.LOW_BATTERY -> 1
            Mood.CHARGING, Mood.CUDDLE -> 2
            Mood.SLEEPING -> 3
            Mood.CALM -> if (now < blinkUntil) 2 else 0
        }
        if (expression != targetHead) {
            currentHead = targetHead
            targetHead = expression
            headTransitionStarted = now
            headTransitionDuration = if (mood == Mood.CUDDLE) 240L else 420L
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val width = width.toFloat()
        val height = height.toFloat()
        val pad = 3f * density
        catRect.set(pad, pad, width - pad, height - pad)
        bodyRect.set(catRect.left + width * 0.15f, catRect.top + height * 0.38f, catRect.left + width * 0.82f, catRect.bottom)
        headRect.set(catRect.left + width * 0.10f, catRect.top - height * 0.015f, catRect.left + width * 0.81f, catRect.top + height * 0.77f)
        pawsRect.set(catRect.left + width * 0.27f, catRect.top + height * 0.57f, catRect.left + width * 0.66f, catRect.bottom + height * 0.02f)
        tailRect.set(catRect.left + width * 0.60f, catRect.top + height * 0.57f, catRect.left + width * 0.94f, catRect.top + height * 0.98f)

        val sleeping = mood == Mood.SLEEPING
        val breath = kotlin.math.sin(now / if (sleeping) 1_450f else 980f).toFloat() * if (sleeping) 0.006f else 0.012f
        val happyElapsed = now - happyStarted
        val happyMotion = mood == Mood.CHARGING && happyElapsed >= 0L && happyElapsed < 1_200L
        val cuddleProgress = if (mood == Mood.CUDDLE) ((now - cuddleStarted) / 2_400f).coerceIn(0f, 1f) else 0f
        val pawLift = when {
            mood == Mood.CUDDLE -> kotlin.math.sin(Math.PI * cuddleProgress).toFloat() * 7f * density
            happyMotion -> kotlin.math.sin((happyElapsed / 1_200f * Math.PI).toFloat()).coerceAtLeast(0f) * 4f * density
            else -> 0f
        }
        val tailAngle = when (mood) {
            Mood.CHARGING, Mood.CUDDLE -> kotlin.math.sin(now / 360f).toFloat() * 5f
            Mood.LOW_BATTERY -> 3f
            Mood.SLEEPING -> kotlin.math.sin(now / 1_700f).toFloat() * 1.2f
            Mood.CALM -> kotlin.math.sin(now / 1_000f).toFloat() * 1.5f
        }

        drawTail(canvas, tailAngle)
        canvas.save()
        canvas.scale(1f + breath * 0.3f, 1f + breath, bodyRect.centerX(), bodyRect.bottom)
        canvas.drawBitmap(body, null, bodyRect, imagePaint)
        canvas.restore()
        canvas.drawBitmap(hindPaws, null, catRect, imagePaint)
        drawHead(canvas, now)
        val liftedPaws = RectF(pawsRect).apply { offset(0f, -pawLift) }
        canvas.drawBitmap(paws, null, liftedPaws, imagePaint)
        if (showClock) drawClock(canvas)
        if (showBattery) drawBatteryLevel(canvas)
    }

    private fun drawTail(canvas: Canvas, angle: Float) {
        val pivotX = tailRect.left + tailRect.width() * 0.34f
        val pivotY = tailRect.top + tailRect.height() * 0.92f
        canvas.save()
        canvas.rotate(angle, pivotX, pivotY)
        canvas.drawBitmap(tail, null, tailRect, imagePaint)
        drawBatteryMeter(canvas)
        canvas.restore()
    }

    private fun drawHead(canvas: Canvas, now: Long) {
        val t = ((now - headTransitionStarted) / headTransitionDuration.toFloat()).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        val tilt = when (mood) {
            Mood.LOW_BATTERY -> 2f
            Mood.CUDDLE -> kotlin.math.sin((now - cuddleStarted) / 340f).toFloat() * 2f
            Mood.CHARGING -> kotlin.math.sin(now / 520f).toFloat() * 1.2f
            Mood.SLEEPING -> kotlin.math.sin(now / 1_500f).toFloat() * 0.6f
            Mood.CALM -> kotlin.math.sin(now / 1_300f).toFloat() * 0.5f
        }
        canvas.save()
        canvas.rotate(tilt, headRect.centerX(), headRect.top + headRect.height() * 0.80f)
        if (eased < 1f) {
            imagePaint.alpha = ((1f - eased) * 255f).toInt()
            canvas.drawBitmap(heads[currentHead], null, headRect, imagePaint)
        }
        imagePaint.alpha = (eased * 255f).toInt()
        canvas.drawBitmap(heads[targetHead], null, headRect, imagePaint)
        imagePaint.alpha = 255
        canvas.restore()
    }

    private fun drawBatteryMeter(canvas: Canvas) {
        tailPath.reset()
        tailPath.moveTo(tailRect.left + tailRect.width() * 0.34f, tailRect.top + tailRect.height() * 0.92f)
        tailPath.cubicTo(
            tailRect.left + tailRect.width() * 0.34f, tailRect.top + tailRect.height() * 0.68f,
            tailRect.left + tailRect.width() * 0.42f, tailRect.top + tailRect.height() * 0.28f,
            tailRect.left + tailRect.width() * 0.62f, tailRect.top + tailRect.height() * 0.25f
        )
        tailPath.cubicTo(
            tailRect.left + tailRect.width() * 0.86f, tailRect.top + tailRect.height() * 0.20f,
            tailRect.left + tailRect.width() * 0.93f, tailRect.top + tailRect.height() * 0.42f,
            tailRect.left + tailRect.width() * 0.88f, tailRect.top + tailRect.height() * 0.60f
        )
        tailPath.cubicTo(
            tailRect.left + tailRect.width() * 0.85f, tailRect.top + tailRect.height() * 0.75f,
            tailRect.left + tailRect.width() * 0.74f, tailRect.top + tailRect.height() * 0.80f,
            tailRect.left + tailRect.width() * 0.63f, tailRect.top + tailRect.height() * 0.79f
        )
        tailPaint.strokeWidth = 3f * density
        tailPaint.color = Color.argb(90, 60, 52, 54)
        canvas.drawPath(tailPath, tailPaint)
        val fill = Path()
        val measure = android.graphics.PathMeasure(tailPath, false)
        measure.getSegment(0f, measure.length * smoothBattery / 100f, fill, true)
        tailPaint.strokeWidth = 2.2f * density
        tailPaint.color = blend(Color.rgb(255, 249, 247), accent, tintStrength.coerceAtLeast(0.15f))
        canvas.drawPath(fill, tailPaint)
    }

    private fun drawClock(canvas: Canvas) {
        val use24Hour = preferences.getBoolean("use_24_hour", DateFormat.is24HourFormat(context))
        val format = if (use24Hour) "HH:mm" else "h:mm a"
        val clock = SimpleDateFormat(format, Locale.getDefault()).format(Date())
        textPaint.textSize = 11f * density
        textPaint.color = Color.rgb(58, 48, 52)
        textPaint.setShadowLayer(2f * density, 0f, density, Color.WHITE)
        canvas.drawText(clock, catRect.centerX(), catRect.top + catRect.height() * 0.58f, textPaint)
        textPaint.clearShadowLayer()
    }

    private fun drawBatteryLevel(canvas: Canvas) {
        textPaint.textSize = 8f * density
        textPaint.color = Color.rgb(58, 48, 52)
        textPaint.setShadowLayer(2f * density, 0f, density, Color.WHITE)
        canvas.drawText("$battery%", tailRect.left + tailRect.width() * 0.78f, tailRect.top + tailRect.height() * 0.68f, textPaint)
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
