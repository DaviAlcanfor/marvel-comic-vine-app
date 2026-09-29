package com.projeto.marvel.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import com.projeto.marvel.ui.detail.contrast

// Caixas de HQ reaproveitáveis (batalha, Início, Descobrir): legenda retangular, balão de fala com
// rabinho e explosão pontuda. Qualquer cor: o texto vira preto ou branco pelo contraste (4,5:1).

enum class BoxStyle { CAPTION, SPEECH, BURST }

private const val MIN_TEXT_CONTRAST = 4.5
private const val SPEECH_RADIUS_DP = 18f
private const val TAIL_WIDTH_DP = 18f
private const val TAIL_HEIGHT_DP = 14f
private const val TAIL_INSET_DP = 28f
private const val SHADOW_DP = 4f
private const val INK_DP = 2f
private const val TAIL_TIP_FACTOR = 1.5f
private const val BURST_PAD_X = 3

/**
 * Veste o TextView com a caixa [style] na cor [color]. No balão de fala, [tailOnLeft] escolhe o
 * lado do rabinho (quem está falando).
 */
fun TextView.comicBox(style: BoxStyle, @ColorInt color: Int, tailOnLeft: Boolean = true) {
    val ink = ContextCompat.getColor(context, R.color.ink)
    val density = resources.displayMetrics.density
    background = when (style) {
        BoxStyle.CAPTION -> captionDrawable(color, ink, density)
        BoxStyle.SPEECH -> SpeechBubbleDrawable(color, ink, density, tailOnLeft)
        BoxStyle.BURST -> BurstDrawable(color, ink, INK_DP * density)
    }
    val base = resources.getDimensionPixelSize(R.dimen.space_md)
    when (style) {
        BoxStyle.CAPTION -> setPadding(base, base / 2, base + (SHADOW_DP * density).toInt(), base)
        BoxStyle.SPEECH -> {
            val tail = (TAIL_HEIGHT_DP * density).toInt()
            setPadding(base, base / 2, base + (SHADOW_DP * density).toInt(), base + tail)
        }
        // Retângulo do texto inscrito na elipse: ~30% de folga de cada lado.
        BoxStyle.BURST -> setPadding(base * BURST_PAD_X, base * 2, base * BURST_PAD_X, base * 2)
    }
    setTextColor(readableTextOn(color))
}

/** Só troca a cor de uma legenda já feita com `@drawable/bg_caption` (camada 1 = face). */
fun TextView.tintCaption(@ColorInt color: Int) {
    val face = (background.mutate() as? LayerDrawable)?.getDrawable(1)?.mutate() as? GradientDrawable ?: return
    face.setColor(color)
    setTextColor(readableTextOn(color))
}

private fun TextView.readableTextOn(@ColorInt color: Int): Int {
    val ink = ContextCompat.getColor(context, R.color.ink)
    return if (contrast(ink, color) >= MIN_TEXT_CONTRAST) ink else ContextCompat.getColor(context, R.color.white)
}

private fun captionDrawable(@ColorInt color: Int, @ColorInt ink: Int, density: Float): Drawable {
    val shadow = GradientDrawable().apply { setColor(ink) }
    val face = GradientDrawable().apply {
        setColor(color)
        setStroke((INK_DP * density).toInt(), ink)
    }
    val offset = (SHADOW_DP * density).toInt()
    return LayerDrawable(arrayOf(shadow, face)).apply {
        setLayerInset(0, offset, offset, 0, 0)
        setLayerInset(1, 0, 0, offset, offset)
    }
}

/** Balão de fala: retângulo arredondado com rabinho embaixo, contorno de nanquim e sombra dura. */
class SpeechBubbleDrawable(
    @ColorInt fillColor: Int,
    @ColorInt inkColor: Int,
    private val density: Float,
    private val tailOnLeft: Boolean
) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkColor }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        style = Paint.Style.STROKE
        strokeWidth = INK_DP * density
        strokeJoin = Paint.Join.ROUND
    }
    // Reaproveitados a cada desenho.
    private val path = Path()
    private val tailPath = Path()
    private val body = RectF()

    override fun draw(canvas: Canvas) {
        val shift = SHADOW_DP * density
        canvas.save()
        canvas.translate(shift, shift)
        canvas.drawPath(outline(), shadow)
        canvas.restore()
        val outline = outline()
        canvas.drawPath(outline, fill)
        canvas.drawPath(outline, ink)
    }

    private fun outline(): Path {
        val shift = SHADOW_DP * density
        val tail = TAIL_HEIGHT_DP * density
        body.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right - shift, bounds.bottom - shift - tail)
        val radius = SPEECH_RADIUS_DP * density
        path.reset()
        path.addRoundRect(body, radius, radius, Path.Direction.CW)
        val inset = TAIL_INSET_DP * density
        val width = TAIL_WIDTH_DP * density
        val start = if (tailOnLeft) body.left + inset else body.right - inset - width
        val tip = if (tailOnLeft) start - width / 2 else start + width * TAIL_TIP_FACTOR
        tailPath.reset()
        tailPath.moveTo(start, body.bottom - radius / 2)
        tailPath.lineTo(tip, body.bottom + tail)
        tailPath.lineTo(start + width, body.bottom - radius / 2)
        tailPath.close()
        // União: o contorno contorna o rabinho em vez de riscar a base dele.
        path.op(tailPath, Path.Op.UNION)
        return path
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        ink.alpha = alpha
        shadow.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
