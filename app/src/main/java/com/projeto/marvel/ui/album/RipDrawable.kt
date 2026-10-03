package com.projeto.marvel.ui.album

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import kotlin.random.Random

private const val TOOTH_DP = 7f

/**
 * Borda de papel rasgado: dentes irregulares em papel claro com contorno de nanquim. Fica na linha
 * do rasgo do pacote e vai aparecendo conforme o dedo puxa a faixa.
 */
class RipDrawable(@ColorInt paper: Int, @ColorInt ink: Int, private val density: Float) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val path = Path()

    // Alturas sorteadas uma vez: o rasgo não "ferve" a cada quadro.
    private val teeth = List(TEETH) { Random.nextFloat() }

    override fun onBoundsChange(bounds: Rect) {
        path.reset()
        val tooth = TOOTH_DP * density
        val height = bounds.height().toFloat()
        path.moveTo(0f, 0f)
        var x = 0f
        var i = 0
        while (x < bounds.width()) {
            path.lineTo(x + tooth / 2, height * (MIN_DEPTH + (1 - MIN_DEPTH) * teeth[i++ % TEETH]))
            x += tooth
            path.lineTo(x, 0f)
        }
        path.close()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val TEETH = 32
        const val MIN_DEPTH = 0.35f
    }
}
