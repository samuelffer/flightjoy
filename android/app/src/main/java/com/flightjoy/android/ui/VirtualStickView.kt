package com.flightjoy.android.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Indicador do giroscópio em formato de CRUZ (Eixo X e Eixo Y) em tema monocromático.
 * - Quando o avião sobe (cabrar, pitch > 0), a bolinha vai para BAIXO no eixo Y.
 * - Tocar no centro da cruz trava/destrava o giroscópio no centro (0%, 0%).
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

    private val horizBarRect = RectF()
    private val vertBarRect = RectF()

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

    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0E0E0E")
        style = Paint.Style.FILL
    }

    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E2E2E")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }

    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#383838")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val guideLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val axisMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A8A8A")
        style = Paint.Style.FILL
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
        val armThickness = 22f * density
        val halfThick = armThickness * 0.5f
        val corner = 5f * density

        // Braço Horizontal da Cruz (Eixo X — Roll)
        horizBarRect.set(cx - halfArm, cy - halfThick, cx + halfArm, cy + halfThick)
        // Braço Vertical da Cruz (Eixo Y — Pitch)
        vertBarRect.set(cx - halfThick, cy - halfArm, cx + halfThick, cy + halfArm)

        // Preenchimento escuro da cruz
        canvas.drawRoundRect(horizBarRect, corner, corner, trackFillPaint)
        canvas.drawRoundRect(vertBarRect, corner, corner, trackFillPaint)

        // Bordas da cruz
        canvas.drawRoundRect(horizBarRect, corner, corner, trackBorderPaint)
        canvas.drawRoundRect(vertBarRect, corner, corner, trackBorderPaint)

        // Linhas centrais dos eixos X e Y
        canvas.drawLine(cx - halfArm + 4f, cy, cx + halfArm - 4f, cy, axisLinePaint)
        canvas.drawLine(cx, cy - halfArm + 4f, cx, cy + halfArm - 4f, axisLinePaint)

        // Marcações de escala (50%) nos braços X e Y
        val tickHalf = halfThick * 0.55f
        for (sign in intArrayOf(-1, 1)) {
            val tx = cx + sign * (halfArm * 0.5f)
            val ty = cy + sign * (halfArm * 0.5f)
            canvas.drawLine(tx, cy - tickHalf, tx, cy + tickHalf, axisLinePaint)
            canvas.drawLine(cx - tickHalf, ty, cx + tickHalf, ty, axisLinePaint)
        }

        // Rótulos discretos X e Y nas extremidades da cruz
        canvas.drawText("Y", cx, cy - halfArm - 4f * density, axisLabelPaint)
        canvas.drawText("X", cx + halfArm + 8f * density, cy + 3.5f * density, axisLabelPaint)

        if (isLocked) {
            // Indicador travado no centro da cruz
            canvas.drawCircle(cx, cy, 8f * density, dotPaint)
            canvas.drawCircle(cx, cy, 14f * density, lockRingPaint)
            statusLabelPaint.color = Color.parseColor("#FFFFFF")
            canvas.drawText("TRAVADO (TOQUE NO MEIO)", cx, cy + halfArm + 14f * density, statusLabelPaint)
        } else {
            // Quando o avião sobe (pitch > 0), a bolinha vai para BAIXO (+pitch)
            val dotX = cx + roll * (halfArm - 8f * density)
            val dotY = cy + pitch * (halfArm - 8f * density)

            // Linhas guias até os eixos X e Y da cruz
            canvas.drawLine(dotX, cy, dotX, dotY, guideLinePaint)
            canvas.drawLine(cx, dotY, dotX, dotY, guideLinePaint)

            // Pequenos cursores projetados sobre o eixo X e sobre o eixo Y
            canvas.drawCircle(dotX, cy, 4.5f * density, axisMarkerPaint)
            canvas.drawCircle(cx, dotY, 4.5f * density, axisMarkerPaint)

            // Bolinha principal de posição
            canvas.drawCircle(dotX, dotY, 8.5f * density, dotPaint)

            statusLabelPaint.color = Color.parseColor("#666666")
            canvas.drawText("Toque no centro p/ travar", cx, cy + halfArm + 14f * density, statusLabelPaint)
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

            // Tocar na interseção central da cruz trava/destrava o giroscópio
            if (dist <= halfArm * 0.45f) {
                isLocked = !isLocked
                onCenterLockChanged?.invoke(isLocked)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
