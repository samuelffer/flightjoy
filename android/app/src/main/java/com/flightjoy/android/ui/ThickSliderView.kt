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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Slider grosso customizado em estilo monocromático (Preto, Branco e Cinza).
 * - Pode operar na Vertical (Throttle / Potência) ou Horizontal (Rudder / Pedais).
 * - Trava contra toques acidentais: SÓ move se o toque iniciar (ACTION_DOWN)
 *   em cima do quadradinho (handle) e for arrastado.
 * - Possui suavização exponencial e sensibilidade controlada por delta.
 */
class ThickSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isVertical: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var isBipolarCenter: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var enableSmoothDamping: Boolean = true
    var dragSensitivityFactor: Float = 0.88f
    var returnToCenterOnRelease: Boolean = false

    private var targetNormalized: Float = -1.0f
    private var currentNormalized: Float = -1.0f
    private var isDraggingThumb: Boolean = false
    private var dragStartCoord: Float = 0f
    private var dragStartValue: Float = -1.0f

    var onValueChanged: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val thumbRect = RectF()
    private val trackRect = RectF()
    private val fillRect = RectF()

    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#121212")
        style = Paint.Style.FILL
    }

    private val trackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E2E2E")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B2B2B")
        style = Paint.Style.FILL
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D6D6D6")
        style = Paint.Style.FILL
    }

    private val thumbActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.FILL
    }

    private val thumbGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    fun setNormalizedValueImmediate(value: Float) {
        val clamped = value.coerceIn(-1.0f, 1.0f)
        targetNormalized = clamped
        currentNormalized = clamped
        onValueChanged?.invoke(currentNormalized)
        invalidate()
    }

    fun getNormalizedValue(): Float = currentNormalized

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val pad = 4f * density
        trackRect.set(pad, pad, w - pad, h - pad)
        val cornerRadius = 8f * density

        // Fundo grosso da trilha
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackBgPaint)

        if (isVertical) {
            drawVerticalSlider(canvas, cornerRadius)
        } else {
            drawHorizontalSlider(canvas, cornerRadius)
        }

        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackBorderPaint)

        // Desenha o "quadradinho" (handle)
        val handlePaint = if (isDraggingThumb) thumbActivePaint else thumbPaint
        val thumbCorner = 6f * density
        canvas.drawRoundRect(thumbRect, thumbCorner, thumbCorner, handlePaint)

        // Linhas de aderência no quadradinho
        val cx = thumbRect.centerX()
        val cy = thumbRect.centerY()
        if (isVertical) {
            val gripHalfW = thumbRect.width() * 0.28f
            val offset = 4f * density
            canvas.drawLine(cx - gripHalfW, cy - offset, cx + gripHalfW, cy - offset, thumbGripPaint)
            canvas.drawLine(cx - gripHalfW, cy, cx + gripHalfW, cy, thumbGripPaint)
            canvas.drawLine(cx - gripHalfW, cy + offset, cx + gripHalfW, cy + offset, thumbGripPaint)
        } else {
            val gripHalfH = thumbRect.height() * 0.28f
            val offset = 4f * density
            canvas.drawLine(cx - offset, cy - gripHalfH, cx - offset, cy + gripHalfH, thumbGripPaint)
            canvas.drawLine(cx, cy - gripHalfH, cx, cy + gripHalfH, thumbGripPaint)
            canvas.drawLine(cx + offset, cy - gripHalfH, cx + offset, cy + gripHalfH, thumbGripPaint)
        }
    }

    private fun drawVerticalSlider(canvas: Canvas, cornerRadius: Float) {
        val thumbHeight = (38f * density).coerceAtMost(trackRect.height() * 0.25f)
        val usableRange = (trackRect.height() - thumbHeight).coerceAtLeast(1f)

        // progress 0.0 (base = -1.0) até 1.0 (topo = +1.0)
        val progress = ((currentNormalized + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
        val thumbTop = trackRect.bottom - thumbHeight - (progress * usableRange)
        val thumbBottom = thumbTop + thumbHeight

        // Preenchimento da base até o centro do quadradinho
        fillRect.set(
            trackRect.left + 2f,
            thumbRect.centerY().takeIf { it > 0 } ?: thumbBottom,
            trackRect.right - 2f,
            trackRect.bottom - 2f
        )
        fillRect.top = (thumbTop + thumbBottom) * 0.5f
        canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint)

        // Marcações de escala (25%, 50%, 75%)
        for (i in 1..3) {
            val y = trackRect.top + (trackRect.height() * i / 4f)
            canvas.drawLine(trackRect.left + 8f * density, y, trackRect.right - 8f * density, y, tickPaint)
        }

        val inset = 3f * density
        thumbRect.set(
            trackRect.left + inset,
            thumbTop,
            trackRect.right - inset,
            thumbBottom
        )
    }

    private fun drawHorizontalSlider(canvas: Canvas, cornerRadius: Float) {
        val thumbWidth = (42f * density).coerceAtMost(trackRect.width() * 0.25f)
        val usableRange = (trackRect.width() - thumbWidth).coerceAtLeast(1f)

        val progress = ((currentNormalized + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)
        val thumbLeft = trackRect.left + (progress * usableRange)
        val thumbRight = thumbLeft + thumbWidth

        val centerX = trackRect.centerX()
        val thumbCenterX = (thumbLeft + thumbRight) * 0.5f

        if (isBipolarCenter) {
            val leftFill = minOf(centerX, thumbCenterX)
            val rightFill = maxOf(centerX, thumbCenterX)
            fillRect.set(leftFill, trackRect.top + 2f, rightFill, trackRect.bottom - 2f)
            canvas.drawRect(fillRect, fillPaint)
            // Linha central 0%
            canvas.drawLine(centerX, trackRect.top + 4f, centerX, trackRect.bottom - 4f, tickPaint)
        } else {
            fillRect.set(trackRect.left + 2f, trackRect.top + 2f, thumbCenterX, trackRect.bottom - 2f)
            canvas.drawRoundRect(fillRect, cornerRadius, cornerRadius, fillPaint)
        }

        val inset = 3f * density
        thumbRect.set(
            thumbLeft,
            trackRect.top + inset,
            thumbRight,
            trackRect.bottom - inset
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Só aceita o toque se começar EM CIMA do quadradinho (handle)
                val tolerance = 14f * density
                val hitRect = RectF(
                    thumbRect.left - tolerance,
                    thumbRect.top - tolerance,
                    thumbRect.right + tolerance,
                    thumbRect.bottom + tolerance
                )
                if (!hitRect.contains(event.x, event.y)) {
                    isDraggingThumb = false
                    return false
                }

                parent?.requestDisallowInterceptTouchEvent(true)
                isDraggingThumb = true
                dragStartCoord = if (isVertical) event.y else event.x
                dragStartValue = targetNormalized
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDraggingThumb) return false

                val usablePixels = if (isVertical) {
                    (trackRect.height() - thumbRect.height()).coerceAtLeast(1f)
                } else {
                    (trackRect.width() - thumbRect.width()).coerceAtLeast(1f)
                }

                val currentCoord = if (isVertical) event.y else event.x
                val deltaPx = if (isVertical) {
                    dragStartCoord - currentCoord // Para cima aumenta
                } else {
                    currentCoord - dragStartCoord // Para a direita aumenta
                }

                // Converte delta de pixels para faixa [-1.0, +1.0] (amplitude 2.0) com fator de suavidade
                val deltaNorm = (deltaPx / usablePixels) * 2.0f * dragSensitivityFactor
                var newTarget = (dragStartValue + deltaNorm).coerceIn(-1.0f, 1.0f)

                // Pequena zona de encaixe central no Rudder (bipolar)
                if (isBipolarCenter && abs(newTarget) < 0.025f) {
                    newTarget = 0.0f
                }

                targetNormalized = newTarget
                if (enableSmoothDamping) {
                    scheduleSmoothStep()
                } else {
                    currentNormalized = targetNormalized
                    onValueChanged?.invoke(currentNormalized)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingThumb) {
                    isDraggingThumb = false
                    if (returnToCenterOnRelease) {
                        targetNormalized = 0.0f
                        currentNormalized = 0.0f
                        onValueChanged?.invoke(0.0f)
                    }
                    invalidate()
                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleSmoothStep() {
        val diff = targetNormalized - currentNormalized
        if (abs(diff) <= 0.002f) {
            currentNormalized = targetNormalized
            onValueChanged?.invoke(currentNormalized)
            invalidate()
            return
        }

        // Suavização leve para evitar saltos bruscos no Throttle
        currentNormalized += diff * 0.30f
        onValueChanged?.invoke(currentNormalized)
        invalidate()

        postOnAnimation {
            if (abs(targetNormalized - currentNormalized) > 0.002f) {
                scheduleSmoothStep()
            } else {
                currentNormalized = targetNormalized
                onValueChanged?.invoke(currentNormalized)
                invalidate()
            }
        }
    }

    fun getDisplayPercentage(): Int {
        return if (isBipolarCenter) {
            (currentNormalized * 100.0f).roundToInt()
        } else {
            (((currentNormalized + 1.0f) * 0.5f) * 100.0f).roundToInt()
        }
    }
}
