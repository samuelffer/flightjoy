package com.flightjoy.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Indicador minimalista do giroscópio em tema monocromático (Preto, Branco e Cinza).
 */
class VirtualStickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pitch: Float = 0.0f
    private var roll: Float = 0.0f
    private var deadzone: Float = 0.03f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D0D0D")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val deadzonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D3D3D")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.FILL
    }

    fun updateAxes(pitchNorm: Float, rollNorm: Float, dz: Float) {
        pitch = pitchNorm.coerceIn(-1.0f, 1.0f)
        roll = rollNorm.coerceIn(-1.0f, 1.0f)
        deadzone = dz.coerceIn(0.0f, 0.4f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val radius = min(w, h) * 0.45f

        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, gridPaint)
        canvas.drawCircle(cx, cy, radius * 0.5f, gridPaint)

        if (deadzone > 0.005f) {
            canvas.drawCircle(cx, cy, radius * deadzone, deadzonePaint)
        }

        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)
        canvas.drawCircle(cx, cy, 4f, centerDotPaint)

        val dotX = cx + roll * radius
        val dotY = cy - pitch * radius

        canvas.drawLine(cx, cy, dotX, dotY, vectorPaint)
        canvas.drawCircle(dotX, dotY, 12f, dotPaint)
    }
}
