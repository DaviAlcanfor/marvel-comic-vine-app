package com.projeto.marvel.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.projeto.marvel.R

private const val COLUMNS = 2
private const val CARD_HEIGHT_DP = 200f
private const val TEXT_HEIGHT_DP = 12f
private const val TEXT_WIDTH_FRACTION = 0.66f
private const val PULSE_MILLIS = 700L
private const val MIN_ALPHA = 90
private const val MAX_ALPHA = 200

/**
 * Carregamento em forma de lista: grade de cards cinza que pulsa, no lugar do círculo. O pulso é
 * no Paint (não no alpha da View), para não brigar com o `fadeVisible` que mostra/esconde.
 */
class SkeletonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val gap = resources.getDimension(R.dimen.space_sm)
    private val radius = resources.getDimension(R.dimen.radius_large)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.surface_variant)
    }
    private val rect = RectF()
    private val pulse = ValueAnimator.ofInt(MIN_ALPHA, MAX_ALPHA).apply {
        duration = PULSE_MILLIS
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { invalidate() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.loading)
    }

    override fun onDraw(canvas: Canvas) {
        paint.alpha = pulse.animatedValue as? Int ?: MAX_ALPHA
        val cardWidth = (width - gap * (COLUMNS + 1)) / COLUMNS
        val cardHeight = CARD_HEIGHT_DP * density
        val textHeight = TEXT_HEIGHT_DP * density
        var top = gap
        while (top < height) {
            for (column in 0 until COLUMNS) {
                val left = gap + column * (cardWidth + gap)
                rect.set(left, top, left + cardWidth, top + cardHeight - textHeight * 2 - gap)
                canvas.drawRoundRect(rect, radius, radius, paint)
                val textTop = rect.bottom + gap / 2
                rect.set(left, textTop, left + cardWidth * TEXT_WIDTH_FRACTION, textTop + textHeight)
                canvas.drawRoundRect(rect, textHeight / 2, textHeight / 2, paint)
            }
            top += cardHeight + gap
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) pulse.start() else pulse.cancel()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }
}
