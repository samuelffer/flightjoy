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
 * - Opera na Vertical (Throttle / Reverso) ou Horizontal (Rudder / Pedais).
 * - Trava contra toques acidentais: SÓ move se o toque iniciar (ACTION_DOWN)
 *   em cima do quadradinho (handle) e for arrastado.
 * - Acompanhamento 1:1 imediato do dedo (sem atraso visual entre o toque e o quadradinho).
 * - Suporta modo invertido vertical (isInvertedVertical = true) para o Modo Reverso,
 *   onde 0% começa no TOPO e puxa-se o quadradinho para BAIXO até 100%.
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

    /**
     * Quando true (Modo Reverso), o 0% (-1.0) fica no TOPO da barra vertical
     * e o 100% (+1.0) fica na BASE (puxando a alavanca para trás/baixo).
     */
    var isInvertedVertical: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var returnToCenterOnRelease: Boolean = false

    private var currentNormalized: Float = -1.0f
    private var isDraggingThumb: Boolean = false

    // Offset exato entre o ponto de toque e o topo/esquerda do quadradinho para rastreamento 1:1 sem atraso
    private var grabOffsetFromThumbStartPx: Float = 0f

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
        color = Color.parseColor("#2E2E2E")
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

        // progress: 0.0 (0% = -1.0) até 1.0 (100% = +1.0)
        val progress = ((currentNormalized + 1.0f) * 0.5f).coerceIn(0.0f, 1.0f)

        val thumbTop = if (isInvertedVertical) {
            // Modo Reverso: 0% começa no TOPO e puxa para baixo até 100%
            trackRect.top + (progress * usableRange)
        } else {
            // Modo Normal: 0% começa na BASE e empurra para cima até 100%
            trackRect.bottom - thumbHeight - (progress * usableRange)
        }
        val thumbBottom = thumbTop + thumbHeight
        val thumbCenterY = (thumbTop + thumbBottom) * 0.5f

        if (isInvertedVertical) {
            // Preenchimento do TOPO até o quadradinho
            fillRect.set(
                trackRect.left + 2f,
                trackRect.top + 2f,
                trackRect.right - 2f,
                thumbCenterY
            )
        } else {
            // Preenchimento da BASE até o quadradinho
            fillRect.set(
                trackRect.left + 2f,
                thumbCenterY,
                trackRect.right - 2f,
                trackRect.bottom - 2f
            )
        }
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
                // Salva a posição exata onde o dedo pegou no quadradinho para acompanhamento 1:1
                grabOffsetFromThumbStartPx = if (isVertical) {
                    event.y - thumbRect.top
                } else {
                    event.x - thumbRect.left
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDraggingThumb) return false

                if (isVertical) {
                    val thumbHeight = thumbRect.height()
                    val usableRange = (trackRect.height() - thumbHeight).coerceAtLeast(1f)
                    val desiredTop = (event.y - grabOffsetFromThumbStartPx)
                        .coerceIn(trackRect.top, trackRect.bottom - thumbHeight)

                    val progress = if (isInvertedVertical) {
                        // Topo = 0.0 (0%), Base = 1.0 (100%)
                        ((desiredTop - trackRect.top) / usableRange).coerceIn(0.0f, 1.0f)
                    } else {
                        // Base = 0.0 (0%), Topo = 1.0 (100%)
                        ((trackRect.bottom - thumbHeight - desiredTop) / usableRange).coerceIn(0.0f, 1.0f)
                    }

                    currentNormalized = (progress * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                } else {
                    val thumbWidth = thumbRect.width()
                    val usableRange = (trackRect.width() - thumbWidth).coerceAtLeast(1f)
                    val desiredLeft = (event.x - grabOffsetFromThumbStartPx)
                        .coerceIn(trackRect.left, trackRect.right - thumbWidth)

                    val progress = ((desiredLeft - trackRect.left) / usableRange).coerceIn(0.0f, 1.0f)
                    var norm = (progress * 2.0f - 1.0f).coerceIn(-1.0f, 1.0f)
                    if (isBipolarCenter && abs(norm) < 0.025f) {
                        norm = 0.0f
                    }
                    currentNormalized = norm
                }

                onValueChanged?.invoke(currentNormalized)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingThumb) {
                    isDraggingThumb = false
                    if (returnToCenterOnRelease) {
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

    fun getDisplayPercentage(): Int {
        return if (isBipolarCenter) {
            (currentNormalized * 100.0f).roundToInt()
        } else {
            (((currentNormalized + 1.0f) * 0.5f) * 100.0f).roundToInt()
        }
    }
}
