package com.projeto.marvel.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.projeto.marvel.R

private const val RETRO_STROKE_DP = 3f
private const val RETRO_SHADOW_DP = 3f
private const val RETRO_TILT = -2f
private const val NINETIES_DEPTH = 6
private const val NINETIES_SKEW = -0.18f
private const val RULE_WIDTH_DP = 28f
private const val RULE_HEIGHT_DP = 3f
private const val RULE_GAP_DP = 8f

/**
 * Letreiro de título no estilo da época ([Era]): amarelo com contorno vermelho e sombra de nanquim
 * (Retrô, capa dos anos 60), amarelo com extrusão 3D azul e inclinado (Anos 90, X-Men '92) ou
 * limpo com um traço vermelho em cima (Moderno). O texto é desenhado à mão, em camadas, a partir
 * do layout do próprio TextView (autoajuste e quebra continuam valendo).
 */
class LogoTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val era = context.era()
    private val density = resources.displayMetrics.density
    private val yellow = ContextCompat.getColor(context, R.color.logo_yellow)
    private val red = ContextCompat.getColor(context, R.color.primary)
    private val ink = ContextCompat.getColor(context, R.color.ink)
    private val extrusion = ContextCompat.getColor(context, R.color.nineties_extrusion)
    private val extrusionDark = ContextCompat.getColor(context, R.color.nineties_extrusion_dark)
    private val rule = Paint().apply { color = red }

    init {
        // A sombra do estilo (uma camada só) fica para quem não é letreiro; aqui as camadas são nossas.
        setShadowLayer(0f, 0f, 0f, 0)
        val extra = when (era) {
            Era.RETRO -> (RETRO_SHADOW_DP + RETRO_STROKE_DP) * density
            Era.NINETIES -> NINETIES_DEPTH * density
            Era.MODERN -> 0f
        }
        val top = if (era == Era.MODERN) ((RULE_HEIGHT_DP + RULE_GAP_DP) * density) else 0f
        // O contorno do Retrô passa meio traço para fora do texto: sem esta folga, a primeira
        // letra e o topo ficavam cortados.
        val stroke = if (era == Era.RETRO) (RETRO_STROKE_DP * density).toInt() else 0
        setPadding(
            paddingLeft + stroke,
            paddingTop + top.toInt() + stroke,
            paddingRight + extra.toInt(),
            paddingBottom + extra.toInt()
        )
        if (era == Era.RETRO) rotation = RETRO_TILT
        if (era == Era.NINETIES) paint.textSkewX = NINETIES_SKEW
    }

    override fun onDraw(canvas: Canvas) {
        val layout = layout ?: return super.onDraw(canvas)
        val paint = paint
        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        fun pass(color: Int, style: Paint.Style, dx: Float = 0f, dy: Float = 0f) {
            paint.color = color
            paint.style = style
            canvas.save()
            canvas.translate(dx, dy)
            layout.draw(canvas)
            canvas.restore()
        }
        when (era) {
            Era.RETRO -> {
                paint.strokeWidth = RETRO_STROKE_DP * density
                paint.strokeJoin = Paint.Join.ROUND
                val shadow = RETRO_SHADOW_DP * density
                pass(ink, Paint.Style.FILL_AND_STROKE, shadow, shadow)
                pass(red, Paint.Style.STROKE)
                pass(yellow, Paint.Style.FILL)
            }
            Era.NINETIES -> {
                for (step in NINETIES_DEPTH downTo 1) {
                    val offset = step * density
                    pass(if (step > NINETIES_DEPTH / 2) extrusionDark else extrusion, Paint.Style.FILL, offset, offset)
                }
                pass(yellow, Paint.Style.FILL)
            }
            Era.MODERN -> {
                val top = -(RULE_HEIGHT_DP + RULE_GAP_DP) * density
                canvas.drawRect(0f, top, RULE_WIDTH_DP * density, -RULE_GAP_DP * density, rule)
                pass(currentTextColor, Paint.Style.FILL)
            }
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
}
