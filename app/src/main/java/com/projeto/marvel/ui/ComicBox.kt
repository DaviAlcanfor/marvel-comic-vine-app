package com.projeto.marvel.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.projeto.marvel.R
import com.projeto.marvel.ui.detail.contrast

// Caixas de HQ reaproveitáveis (batalha, Início, Descobrir): legenda retangular, balão de fala com
// rabinho e explosão pontuda. Qualquer cor: o texto vira preto ou branco pelo contraste (4,5:1).
// O traço segue a época ([Era]): Retrô = nanquim grosso, sombra dura e balão oval; Anos 90 = neon,
// cantos chanfrados; Moderno = traço fino, sem sombra, balão arredondado.

enum class BoxStyle { CAPTION, SPEECH, BURST }

private const val MIN_TEXT_CONTRAST = 4.5
private const val SPEECH_RADIUS_DP = 18f
private const val TAIL_WIDTH_DP = 18f
private const val TAIL_HEIGHT_DP = 14f
private const val TAIL_INSET_DP = 28f
private const val TAIL_TIP_FACTOR = 1.5f
private const val BURST_PAD_X = 3
private const val CHAMFER_DP = 10f
private const val OVAL_TAIL_FACTOR = 1.6f

/**
 * Veste o TextView com a caixa [style] na cor [color]. No balão de fala, [tailOnLeft] escolhe o
 * lado do rabinho (quem está falando).
 */
fun TextView.comicBox(style: BoxStyle, @ColorInt color: Int, tailOnLeft: Boolean = true) {
    val outline = context.eraOutline()
    val inkWidth = context.eraDimen(R.attr.eraInkWidth)
    val shadow = context.eraDimen(R.attr.eraShadow)
    val density = resources.displayMetrics.density
    background = when (style) {
        BoxStyle.CAPTION -> EraPanelDrawable(context, EraPanelDrawable.Kind.CAPTION, color)
        BoxStyle.SPEECH -> SpeechBubbleDrawable(context, color, tailOnLeft)
        BoxStyle.BURST -> BurstDrawable(color, outline, inkWidth)
    }
    val base = resources.getDimensionPixelSize(R.dimen.space_md)
    val oval = if (style == BoxStyle.SPEECH && context.era() == Era.RETRO) base / 2 else 0
    when (style) {
        BoxStyle.CAPTION -> setPadding(base, base / 2, base + shadow.toInt(), base)
        BoxStyle.SPEECH -> {
            val tail = (TAIL_HEIGHT_DP * density).toInt()
            setPadding(base + oval, base / 2 + oval / 2, base + oval, base + tail + oval / 2)
        }
        // Retângulo do texto inscrito na elipse: ~30% de folga de cada lado.
        BoxStyle.BURST -> setPadding(base * BURST_PAD_X, base * 2, base * BURST_PAD_X, base * 2)
    }
    setTextColor(readableTextOn(color))
}

/** Só troca a cor de uma legenda já feita com `@drawable/bg_caption`. */
fun TextView.tintCaption(@ColorInt color: Int) {
    (background as? EraPanelDrawable)?.setFill(color) ?: return
    setTextColor(readableTextOn(color))
}

private fun TextView.readableTextOn(@ColorInt color: Int): Int {
    val ink = ContextCompat.getColor(context, R.color.ink)
    return if (contrast(ink, color) >= MIN_TEXT_CONTRAST) ink else ContextCompat.getColor(context, R.color.white)
}

/**
 * Balão de fala com rabinho embaixo, no traço da [era]: oval de nanquim com sombra dura (Retrô),
 * caixa de cantos chanfrados (Anos 90) ou retângulo arredondado limpo (Moderno).
 */
class SpeechBubbleDrawable(context: Context, @ColorInt fillColor: Int, private val tailOnLeft: Boolean) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val era = context.era()
    // Balão de fala nunca tem sombra dura (no canvas aprovado ele é só traço e papel).
    private val shadowSize = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.ink) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.eraOutline()
        style = Paint.Style.STROKE
        strokeWidth = context.eraDimen(R.attr.eraInkWidth)
        strokeJoin = Paint.Join.ROUND
    }
    // Reaproveitados a cada desenho.
    private val path = Path()
    private val tailPath = Path()
    private val body = RectF()

    override fun draw(canvas: Canvas) {
        if (shadowSize > 0f) {
            canvas.save()
            canvas.translate(shadowSize, shadowSize)
            canvas.drawPath(outline(), shadow)
            canvas.restore()
        }
        val outline = outline()
        canvas.drawPath(outline, fill)
        canvas.drawPath(outline, ink)
    }

    private fun outline(): Path {
        val tail = TAIL_HEIGHT_DP * density
        val bottom = bounds.bottom - shadowSize - tail
        body.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right - shadowSize, bottom)
        val radius = SPEECH_RADIUS_DP * density
        path.reset()
        when (era) {
            Era.RETRO -> path.addOval(body, Path.Direction.CW)
            Era.NINETIES -> chamfer(path, body, CHAMFER_DP * density)
            Era.MODERN -> path.addRoundRect(body, radius, radius, Path.Direction.CW)
        }
        val inset = TAIL_INSET_DP * density
        val width = TAIL_WIDTH_DP * density
        // No oval a base é curva: o rabinho sai mais para dentro, onde a borda ainda está embaixo.
        val edge = if (era == Era.RETRO) inset * OVAL_TAIL_FACTOR else inset
        val start = if (tailOnLeft) body.left + edge else body.right - edge - width
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

    private fun chamfer(path: Path, box: RectF, cut: Float) {
        path.moveTo(box.left + cut, box.top)
        path.lineTo(box.right, box.top)
        path.lineTo(box.right, box.bottom - cut)
        path.lineTo(box.right - cut, box.bottom)
        path.lineTo(box.left, box.bottom)
        path.lineTo(box.left, box.top + cut)
        path.close()
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

/** Diálogo como painel de HQ: o tema não tem atributo de contorno, então o fundo vai aqui. */
fun Context.comicDialog(): MaterialAlertDialogBuilder =
    MaterialAlertDialogBuilder(this).setBackground(ContextCompat.getDrawable(this, R.drawable.bg_dialog))
