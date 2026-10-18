package com.flightjoy.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Simple Cross (+) Gyroscope Indicator (X and Y Axes) in monochrome theme.
 * - When pitching up (climb, pitch > 0), the white dot moves DOWN on the Y axis.
 * - Tapping the center of the cross locks/unlocks the gyroscope at (0%, 0%).
 */
class VirtualStickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pitch: Float = 0.0f
    private var roll: Float = 0.0f
    private var deadzone: Float = 0.03f
    private val density = resources.displayMetrics.density

    var isLocked: Boolean = false
        set(value) {
            field = value
            if (value) {
                pitch = 0.0f
                roll = 0.0f
            }
            invalidate()
        }

    var onCenterLockChanged: ((Boolean) -> Unit)? = null

    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#444444")
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
    }

    private val tickLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#383838")
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
    }

    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val lockRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }

    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 9.5f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val statusLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 9.5f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun updateAxes(pitchNorm: Float, rollNorm: Float, dz: Float) {
        if (isLocked) {
            pitch = 0.0f
            roll = 0.0f
        } else {
            pitch = pitchNorm.coerceIn(-1.0f, 1.0f)
            roll = rollNorm.coerceIn(-1.0f, 1.0f)
        }
        deadzone = dz.coerceIn(0.0f, 0.4f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.47f
        val halfArm = min(w, h) * 0.37f

        // Simple X (Roll) and Y (Pitch) cross lines (+)
        canvas.drawLine(cx - halfArm, cy, cx + halfArm, cy, axisLinePaint)
        canvas.drawLine(cx, cy - halfArm, cx, cy + halfArm, axisLinePaint)

        // End ticks (100%) and mid ticks (50%)
        val endTickHalf = 5.5f * density
        val midTickHalf = 4.0f * density
        for (sign in intArrayOf(-1, 1)) {
            val endX = cx + sign * halfArm
            val endY = cy + sign * halfArm
            canvas.drawLine(endX, cy - endTickHalf, endX, cy + endTickHalf, axisLinePaint)
            canvas.drawLine(cx - endTickHalf, endY, cx + endTickHalf, endY, axisLinePaint)

            val midX = cx + sign * (halfArm * 0.5f)
            val midY = cy + sign * (halfArm * 0.5f)
            canvas.drawLine(midX, cy - midTickHalf, midX, cy + midTickHalf, tickLinePaint)
            canvas.drawLine(cx - midTickHalf, midY, cx + midTickHalf, midY, tickLinePaint)
        }

        // Subtle X and Y labels at the cross ends
        canvas.drawText("Y", cx, cy - halfArm - 6f * density, axisLabelPaint)
        canvas.drawText("X", cx + halfArm + 10f * density, cy + 3.5f * density, axisLabelPaint)

        if (isLocked) {
            canvas.drawCircle(cx, cy, 8f * density, dotPaint)
            canvas.drawCircle(cx, cy, 14f * density, lockRingPaint)
            statusLabelPaint.color = Color.parseColor("#FFFFFF")
            canvas.drawText("LOCKED (TAP CENTER)", cx, cy + halfArm + 16f * density, statusLabelPaint)
        } else {
            val dotX = cx + roll * halfArm
            val dotY = cy + pitch * halfArm

            canvas.drawLine(dotX, cy, dotX, dotY, guideLinePaint)
            canvas.drawLine(cx, dotY, dotX, dotY, guideLinePaint)

            canvas.drawCircle(dotX, dotY, 8.5f * density, dotPaint)

            statusLabelPaint.color = Color.parseColor("#666666")
            canvas.drawText("Tap center to lock", cx, cy + halfArm + 16f * density, statusLabelPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w * 0.5f
            val cy = h * 0.47f
            val halfArm = min(w, h) * 0.37f
            val dist = hypot((event.x - cx).toDouble(), (event.y - cy).toDouble()).toFloat()

            if (dist <= halfArm * 0.45f) {
                isLocked = !isLocked
                onCenterLockChanged?.invoke(isLocked)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
