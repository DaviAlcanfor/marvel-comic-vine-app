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
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val RAYS = 40
private const val RAY_WIDTH = 0.5f
private const val INNER_RADIUS_RATIO = 0.18f
private const val WOBBLE = 0.35f
private const val CENTERED = 0.5f
private const val MIN_WIDTH_FACTOR = 0.5f
private const val MIN_INNER_FACTOR = 0.6f

/**
 * Linhas de ação de HQ irradiando do centro (fundo da arena). [boil] sorteia de novo a
 * inclinação e o começo de cada raio: chamado quadro a quadro, o fundo "vibra" como desenho.
 */
class SpeedLinesDrawable(@ColorInt color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private val path = Path()
    private var seed = 0

    fun boil() {
        seed++
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val outer = hypot(bounds.width().toFloat(), bounds.height().toFloat())
        val random = Random(seed)
        val step = 2 * PI / RAYS
        path.reset()
        for (i in 0 until RAYS) {
            val angle = i * step + (random.nextFloat() - CENTERED) * step * WOBBLE
            val half = step * RAY_WIDTH / 2 * (MIN_WIDTH_FACTOR + random.nextFloat())
            val inner = outer * INNER_RADIUS_RATIO * (MIN_INNER_FACTOR + random.nextFloat())
            path.moveTo(cx + (cos(angle) * inner).toFloat(), cy + (sin(angle) * inner).toFloat())
            path.lineTo(cx + (cos(angle - half) * outer).toFloat(), cy + (sin(angle - half) * outer).toFloat())
            path.lineTo(cx + (cos(angle + half) * outer).toFloat(), cy + (sin(angle + half) * outer).toFloat())
            path.close()
        }
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
