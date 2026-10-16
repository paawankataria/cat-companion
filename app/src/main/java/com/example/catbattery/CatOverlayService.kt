package com.example.catbattery

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

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
    private var viewAdded = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
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
        cat = CatWidgetView(this, preferences).apply { setOnTouchListener { _, event -> handleTouch(event) } }
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(
            dp(142), dp(174), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(40)
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
                downX = event.rawX
                downY = event.rawY
                startX = params.x
                startY = params.y
                dragged = false
                longPressed = false
                handler.postDelayed(longPress, 550L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (downX - event.rawX).toInt()
                val dy = (event.rawY - downY).toInt()
                if (abs(dx) + abs(dy) > dp(6)) {
                    dragged = true
                    handler.removeCallbacks(longPress)
                    params.x = (startX + dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(0)
                    if (viewAdded) try { windowManager.updateViewLayout(cat, params) } catch (_: Exception) { }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!dragged && !longPressed) cat.playWithTap()
                return true
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
        }
        return true
    }

    private fun applyPreferences() {
        if (::cat.isInitialized) cat.setClockVisible(preferences.getBoolean("show_clock", true))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) applyPreferences()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(longPress)
        if (viewAdded) try { windowManager.removeView(cat) } catch (_: Exception) { }
        preferences.edit().putBoolean("widget_showing", false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Draws a small cutout puppet; each limb, ear, eye and tail has an independent transform. */
private class CatWidgetView(context: Context, private val preferences: android.content.SharedPreferences) : View(context) {
    private fun load(id: Int): Bitmap = BitmapFactory.decodeResource(
        resources, id, BitmapFactory.Options().apply { inScaled = false }
    )

    private val body = load(R.drawable.cat_rig_body)
    private val head = load(R.drawable.cat_rig_head)
    private val leftEar = load(R.drawable.cat_rig_ear_left)
    private val rightEar = load(R.drawable.cat_rig_ear_right)
    private val leftArm = load(R.drawable.cat_rig_arm_left)
    private val rightArm = load(R.drawable.cat_rig_arm_right)
    private val leftFoot = load(R.drawable.cat_rig_foot_left)
    private val rightFoot = load(R.drawable.cat_rig_foot_right)
    private val tail = load(R.drawable.cat_rig_tail)
    private val density = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val shape = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }
    private var showClock = true
    private var mood = Mood.CALM
    private var lastInteraction = System.currentTimeMillis()
    private var playUntil = 0L
    private var nextBlink = System.currentTimeMillis() + 3_500L
    private var blinkStarted = 0L
    private var previousFrameTime = System.currentTimeMillis()
    private var bodyBreath = 0f
    private var happyExpression = 0f
    private var sleepingExpression = 0f
    private var headTilt = 0f
    private var headSway = 0f
    private var leftArmSwing = 0f
    private var rightArmSwing = 0f
    private var leftEarTilt = 0f
    private var rightEarTilt = 0f
    private var tailSwing = 0f

    private enum class Mood { CALM, HAPPY, SLEEPING }

    private val animator = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = (now - previousFrameTime).coerceIn(1L, 80L) / 1000f
            previousFrameTime = now
            mood = when {
                now < playUntil -> Mood.HAPPY
                now - lastInteraction > 60_000L -> Mood.SLEEPING
                else -> Mood.CALM
            }
            if (mood != Mood.SLEEPING && now >= nextBlink) {
                blinkStarted = now
                nextBlink = now + 4_000L + (now % 2_800L)
            }

            val t = now / 1000f
            val asleep = mood == Mood.SLEEPING
            val happy = mood == Mood.HAPPY
            val ease = 1f - exp(-dt * 8.5f)
            happyExpression += ((if (mood == Mood.HAPPY) 1f else 0f) - happyExpression) * ease
            sleepingExpression += ((if (mood == Mood.SLEEPING) 1f else 0f) - sleepingExpression) * ease
            val breath = sin(t * if (asleep) .72f else 1.15f).toFloat() * if (asleep) .010f else .014f
            val tilt = when {
                asleep -> 9f + sin(t * .45f).toFloat() * 1.4f
                happy -> sin(t * 4.2f).toFloat() * 5f
                else -> sin(t * .55f).toFloat() * 1.2f
            }
            val sway = when {
                happy -> sin(t * 3.2f).toFloat() * .012f
                else -> sin(t * .68f).toFloat() * .004f
            }
            val armWave = if (happy) sin(t * 5.4f).toFloat() else 0f
            val earFlick = if (happy && now % 1_700L < 380L) 8f else 0f
            val tail = sin(t * if (asleep) .5f else if (happy) 3.3f else 1.1f).toFloat() * if (asleep) 2f else if (happy) 17f else 5f

            bodyBreath += (breath - bodyBreath) * ease
            headTilt += (tilt - headTilt) * ease
            headSway += (sway - headSway) * ease
            leftArmSwing += ((if (happy) -16f - armWave * 7f else if (asleep) 8f else 0f) - leftArmSwing) * ease
            rightArmSwing += ((if (happy) 16f + armWave * 7f else if (asleep) -8f else 0f) - rightArmSwing) * ease
            leftEarTilt += ((sin(t * .7f).toFloat() * 1.6f - earFlick) - leftEarTilt) * ease
            rightEarTilt += ((-sin(t * .7f).toFloat() * 1.6f + earFlick) - rightEarTilt) * ease
            tailSwing += (tail - tailSwing) * ease
            invalidate()
            postDelayed(this, 33L)
        }
    }

    init {
        contentDescription = "Animated cat clock. Tap to make the cat happy, press and hold for settings, drag to move."
        setLayerType(LAYER_TYPE_HARDWARE, null)
        post(animator)
    }

    fun setClockVisible(visible: Boolean) {
        showClock = visible
        invalidate()
    }

    fun playWithTap() {
        val now = System.currentTimeMillis()
        lastInteraction = now
        playUntil = now + 3_400L
    }

    private fun pxX(fraction: Float) = width * fraction
    private fun pxY(fraction: Float) = height * fraction

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.currentTimeMillis()
        val headCenterX = pxX(.5f + headSway)
        val headCenterY = pxY(.52f)

        // The fluffy tail is behind the torso and swings from its hip pivot.
        drawPart(canvas, tail, .015f, .51f, .39f, .45f, .82f, .79f, tailSwing * .58f)

        // Feet/legs are separate from the body so the seated pose can shift and cuddle.
        val legWave = if (mood == Mood.HAPPY) sin(now / 190f).toFloat() * 3.8f else sin(now / 1_900f).toFloat() * .6f
        drawPart(canvas, leftFoot, .115f, .705f, .30f, .29f, .78f, .10f, legWave)
        drawPart(canvas, rightFoot, .585f, .705f, .30f, .29f, .22f, .10f, -legWave)

        canvas.save()
        canvas.scale(1f + bodyBreath * .35f, 1f + bodyBreath, pxX(.5f), pxY(.94f))
        drawPart(canvas, body, .17f, .35f, .66f, .59f)
        canvas.restore()

        // Ears attach to the head but rotate independently at their roots.
        drawPart(canvas, leftEar, .205f + headSway, .015f, .245f, .285f, .80f, .88f, leftEarTilt, headCenterX, headCenterY, headTilt)
        drawPart(canvas, rightEar, .55f + headSway, .015f, .245f, .285f, .20f, .88f, rightEarTilt, headCenterX, headCenterY, headTilt)
        canvas.save()
        canvas.rotate(headTilt, headCenterX, headCenterY)
        drawPart(canvas, head, .15f + headSway, .065f, .70f, .50f)
        drawEyes(canvas, headCenterX, headCenterY, now)
        drawMuzzle(canvas, headCenterX, headCenterY)
        canvas.restore()

        // Arms each have a shoulder pivot and separate rotation.
        drawPart(canvas, leftArm, .235f, .48f, .255f, .39f, .77f, .12f, leftArmSwing)
        drawPart(canvas, rightArm, .51f, .48f, .255f, .39f, .23f, .12f, rightArmSwing)
        if (showClock) drawClock(canvas, now)
    }

    private fun drawEyes(canvas: Canvas, centerX: Float, centerY: Float, now: Long) {
        val blink = blinkAmount(now) * (1f - sleepingExpression)
        val eyeY = pxY(.30f)
        val eyeW = pxX(.105f)
        val eyeH = pxY(.064f)
        val spacing = pxX(.105f)
        for (side in listOf(-1f, 1f)) {
            val cx = centerX + spacing * side
            val openAlpha = ((1f - maxOf(happyExpression, sleepingExpression, blink)) * 255).toInt()
            if (openAlpha > 0) {
                ink.alpha = openAlpha
                canvas.drawOval(RectF(cx - eyeW * .56f, eyeY - eyeH * .55f, cx + eyeW * .56f, eyeY + eyeH * .55f), set(ink, Color.rgb(76, 49, 39)))
                canvas.drawOval(RectF(cx - eyeW * .47f, eyeY - eyeH * .46f, cx + eyeW * .47f, eyeY + eyeH * .46f), set(ink, Color.rgb(255, 229, 169)))
                val iris = RectF(cx - eyeW * .34f, eyeY - eyeH * .38f, cx + eyeW * .34f, eyeY + eyeH * .40f)
                canvas.drawOval(iris, set(ink, Color.rgb(205, 131, 44)))
                canvas.drawOval(RectF(cx - eyeW * .18f, eyeY - eyeH * .35f, cx + eyeW * .18f, eyeY + eyeH * .36f), set(ink, Color.rgb(52, 34, 30)))
                canvas.drawCircle(cx - eyeW * .10f, eyeY - eyeH * .15f, eyeH * .10f, set(ink, Color.WHITE))
                canvas.drawCircle(cx + eyeW * .10f, eyeY + eyeH * .13f, eyeH * .05f, set(ink, Color.WHITE))
            }
            val happyAlpha = (happyExpression * (1f - sleepingExpression) * 255).toInt()
            val closedAlpha = (maxOf(sleepingExpression, blink) * 255).toInt()
            if (happyAlpha > 0) drawClosedEye(canvas, cx, eyeY, eyeW, eyeH, happyAlpha, smile = true)
            if (closedAlpha > 0) drawClosedEye(canvas, cx, eyeY, eyeW, eyeH, closedAlpha, smile = false)
        }
        ink.alpha = 255
    }

    private fun drawClosedEye(canvas: Canvas, cx: Float, eyeY: Float, eyeW: Float, eyeH: Float, alpha: Int, smile: Boolean) {
        ink.alpha = alpha.coerceIn(0, 255)
        ink.color = Color.rgb(78, 51, 49)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1.6f * density
        shape.reset()
        shape.moveTo(cx - eyeW * .5f, eyeY)
        shape.quadTo(cx, eyeY + eyeH * if (smile) .38f else .30f, cx + eyeW * .5f, eyeY)
        canvas.drawPath(shape, ink)
        ink.style = Paint.Style.FILL
        ink.alpha = 255
    }

    private fun drawMuzzle(canvas: Canvas, centerX: Float, centerY: Float) {
        val noseY = pxY(.39f)
        val noseW = pxX(.035f)
        ink.color = Color.rgb(229, 135, 133)
        shape.reset()
        shape.moveTo(centerX - noseW, noseY - pxY(.006f))
        shape.quadTo(centerX, noseY - pxY(.020f), centerX + noseW, noseY - pxY(.006f))
        shape.quadTo(centerX, noseY + pxY(.014f), centerX - noseW, noseY - pxY(.006f))
        canvas.drawPath(shape, ink)

        ink.color = Color.rgb(82, 56, 52)
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 1.25f * density
        shape.reset()
        shape.moveTo(centerX, noseY + pxY(.008f))
        shape.cubicTo(centerX - pxX(.008f), noseY + pxY(.030f), centerX - pxX(.037f), noseY + pxY(.034f), centerX - pxX(.050f), noseY + pxY(.014f))
        shape.moveTo(centerX, noseY + pxY(.008f))
        shape.cubicTo(centerX + pxX(.008f), noseY + pxY(.030f), centerX + pxX(.037f), noseY + pxY(.034f), centerX + pxX(.050f), noseY + pxY(.014f))
        canvas.drawPath(shape, ink)
        if (happyExpression > .01f) {
            ink.alpha = (happyExpression * 255).toInt()
            ink.style = Paint.Style.FILL
            ink.color = Color.rgb(192, 91, 95)
            canvas.drawOval(RectF(centerX - pxX(.025f), noseY + pxY(.023f), centerX + pxX(.025f), noseY + pxY(.065f)), ink)
        }
        ink.alpha = 255
        ink.style = Paint.Style.FILL
    }

    private fun blinkAmount(now: Long): Float {
        val elapsed = now - blinkStarted
        if (elapsed !in 0..250L) return 0f
        val phase = elapsed / 250f
        return if (phase < .5f) smooth(phase * 2f) else 1f - smooth((phase - .5f) * 2f)
    }

    private fun drawClock(canvas: Canvas, now: Long) {
        val use24Hour = preferences.getBoolean("use_24_hour", DateFormat.is24HourFormat(context))
        val format = if (use24Hour) "HH:mm" else "h:mm a"
        text.textSize = 9.5f * density
        text.color = Color.rgb(71, 54, 50)
        text.setShadowLayer(2f * density, 0f, density, Color.WHITE)
        canvas.drawText(SimpleDateFormat(format, Locale.getDefault()).format(Date(now)), pxX(.5f), pxY(.738f), text)
        text.clearShadowLayer()
    }

    private fun drawPart(
        canvas: Canvas, bitmap: Bitmap, left: Float, top: Float, width: Float, height: Float,
        pivotX: Float = .5f, pivotY: Float = .5f, rotation: Float = 0f,
        parentX: Float = pxX(left + width * pivotX), parentY: Float = pxY(top + height * pivotY),
        parentRotation: Float = 0f
    ) {
        canvas.save()
        if (parentRotation != 0f) canvas.rotate(parentRotation, parentX, parentY)
        if (rotation != 0f) canvas.rotate(rotation, pxX(left + width * pivotX), pxY(top + height * pivotY))
        canvas.drawBitmap(bitmap, null, RectF(pxX(left), pxY(top), pxX(left + width), pxY(top + height)), bitmapPaint)
        canvas.restore()
    }

    private fun set(paint: Paint, color: Int): Paint = paint.apply { this.color = color; style = Paint.Style.FILL }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(animator)
        super.onDetachedFromWindow()
    }
}
