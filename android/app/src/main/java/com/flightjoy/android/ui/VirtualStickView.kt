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
 * Indicador minimalista do giroscópio em tema monocromático (Preto, Branco e Cinza).
 * - Tocar no centro do indicador trava/destrava o giroscópio no centro (0%, 0%),
 *   permitindo mover o celular sem alterar os comandos de voo.
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

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D0D0D")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }

    private val deadzonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3D3D3D")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }

    private val vectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val lockRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        textSize = 10f * density
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
        val cy = h * 0.5f
        // Tamanho mais compacto para deixar espaço livre na tela
        val radius = min(w, h) * 0.36f

        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, gridPaint)
        canvas.drawCircle(cx, cy, radius * 0.5f, gridPaint)

        if (deadzone > 0.005f) {
            canvas.drawCircle(cx, cy, radius * deadzone, deadzonePaint)
        }

        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)
        canvas.drawCircle(cx, cy, 4f * density, centerDotPaint)

        if (isLocked) {
            // Indicador travado no centro
            canvas.drawCircle(cx, cy, 9f * density, dotPaint)
            canvas.drawCircle(cx, cy, 15f * density, lockRingPaint)
            labelPaint.color = Color.parseColor("#FFFFFF")
            canvas.drawText("TRAVADO (TOQUE NO MEIO)", cx, cy + radius + 14f * density, labelPaint)
        } else {
            val dotX = cx + roll * radius
            val dotY = cy - pitch * radius

            canvas.drawLine(cx, cy, dotX, dotY, vectorPaint)
            canvas.drawCircle(dotX, dotY, 9f * density, dotPaint)
            labelPaint.color = Color.parseColor("#666666")
            canvas.drawText("Toque no centro p/ travar", cx, cy + radius + 14f * density, labelPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w * 0.5f
            val cy = h * 0.5f
            val radius = min(w, h) * 0.36f
            val dist = hypot((event.x - cx).toDouble(), (event.y - cy).toDouble()).toFloat()

            // Permite travar/destravar ao clicar no centro do indicador do giroscópio
            if (dist <= radius * 0.55f) {
                isLocked = !isLocked
                onCenterLockChanged?.invoke(isLocked)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
