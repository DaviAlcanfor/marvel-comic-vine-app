package com.projeto.marvel.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val SPIKES = 14

// Vales rasos e pouco tremor: o texto (retângulo) precisa caber dentro da parte "cheia".
private const val INNER_RATIO = 0.84f
private const val JITTER = 0.06f

/**
 * Explosão serrilhada de onomatopeia de HQ ("POW!"), elíptica para caber no texto.
 * [boil] sorteia de novo a ponta de cada pico: chamado a cada quadro, o contorno "ferve"
 * como desenho à mão em stop motion.
 */
class BurstDrawable(
    @ColorInt fillColor: Int,
    @ColorInt inkColor: Int,
    inkWidth: Float
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
        style = Paint.Style.FILL
    }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = inkWidth
        strokeJoin = Paint.Join.MITER
    }
    private val path = Path()
    private var seed = 0

    fun boil() {
        seed++
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val margin = ink.strokeWidth
        val radiusX = bounds.width() / 2f - margin
        val radiusY = bounds.height() / 2f - margin
        val random = Random(seed)
        path.reset()
        for (i in 0 until SPIKES * 2) {
            val angle = i * PI / SPIKES
            val reach = if (i % 2 == 0) 1f else INNER_RATIO
            val wobble = 1f - random.nextFloat() * JITTER
            val x = bounds.exactCenterX() + (cos(angle) * radiusX * reach * wobble).toFloat()
            val y = bounds.exactCenterY() + (sin(angle) * radiusY * reach * wobble).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, ink)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        ink.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        ink.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
