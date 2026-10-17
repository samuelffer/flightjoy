package com.flightjoy.android.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Componente visual minimalista que exibe a posição em tempo real de
 * Pitch (vertical), Roll (horizontal) e Yaw (barra inferior) do giroscópio.
 */
class VirtualStickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pitch: Float = 0.0f
    private var roll: Float = 0.0f
    private var yaw: Float = 0.0f
    private var deadzone: Float = 0.03f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B1120")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#334155")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val deadzonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22C55E")
        style = Paint.Style.FILL
    }

    private val yawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F59E0B")
        style = Paint.Style.FILL
    }

    fun updateAxes(pitchNorm: Float, rollNorm: Float, yawNorm: Float, dz: Float) {
        pitch = pitchNorm.coerceIn(-1.0f, 1.0f)
        roll = rollNorm.coerceIn(-1.0f, 1.0f)
        yaw = yawNorm.coerceIn(-1.0f, 1.0f)
        deadzone = dz.coerceIn(0.0f, 0.4f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val yawBarHeight = 22f
        val arenaH = h - yawBarHeight - 10f
        val cx = w * 0.5f
        val cy = arenaH * 0.5f
        val radius = min(w, arenaH) * 0.44f

        // Fundo circular e anéis de referência
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, gridPaint)
        canvas.drawCircle(cx, cy, radius * 0.5f, gridPaint)

        // Zona morta
        if (deadzone > 0.005f) {
            canvas.drawCircle(cx, cy, radius * deadzone, deadzonePaint)
        }

        // Eixos centrais
        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)

        // Ponto atual (Roll = X, Pitch = Y)
        val dotX = cx + roll * radius
        val dotY = cy - pitch * radius

        canvas.drawLine(cx, cy, dotX, dotY, vectorPaint)
        canvas.drawCircle(dotX, dotY, 14f, dotPaint)

        // Barra inferior de Yaw (Leme)
        val barTop = h - yawBarHeight
        val barLeft = cx - radius
        val barRight = cx + radius
        canvas.drawRect(barLeft, barTop, barRight, h - 4f, bgPaint)
        canvas.drawRect(barLeft, barTop, barRight, h - 4f, gridPaint)
        canvas.drawLine(cx, barTop, cx, h - 4f, gridPaint)

        val yawX = cx + yaw * radius
        canvas.drawCircle(yawX, (barTop + h - 4f) * 0.5f, 8f, yawPaint)
    }
}
