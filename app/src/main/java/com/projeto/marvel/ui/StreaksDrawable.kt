package com.projeto.marvel.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projeto.marvel.R
import org.xmlpull.v1.XmlPullParser
import kotlin.math.tan

private const val GAP_DP = 46f
private const val LINE_DP = 2f
private const val ANGLE_DEGREES = 25.0
private const val LINE_ALPHA = 16

/**
 * Anos 90: faixas diagonais. Sem atributos, as finas de energia ciano do fundo (X-Men '92); com
 * `streakGap`/`streakColor`, listras densas (o vazio da figurinha que falta).
 */
class StreaksDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var density = 1f
    private var gap = 0f

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, attrs, theme)
        density = r.displayMetrics.density
        val values = theme?.obtainStyledAttributes(attrs, R.styleable.StreaksDrawable, 0, 0)
            ?: r.obtainAttributes(attrs, R.styleable.StreaksDrawable)
        val neon = ResourcesCompat.getColor(r, R.color.nineties_outline, theme)
        val cyan = ColorUtils.setAlphaComponent(neon, LINE_ALPHA)
        paint.color = values.getColor(R.styleable.StreaksDrawable_streakColor, cyan)
        gap = values.getDimension(R.styleable.StreaksDrawable_streakGap, GAP_DP * density)
        values.recycle()
        paint.strokeWidth = (if (gap < GAP_DP * density) gap / 2 else LINE_DP * density)
    }

    override fun draw(canvas: Canvas) {
        val lean = bounds.height() * tan(Math.toRadians(ANGLE_DEGREES)).toFloat()
        var x = bounds.left - lean
        while (x < bounds.right) {
            canvas.drawLine(x, bounds.bottom.toFloat(), x + lean, bounds.top.toFloat(), paint)
            x += gap
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
