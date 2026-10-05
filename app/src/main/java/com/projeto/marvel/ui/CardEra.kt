package com.projeto.marvel.ui

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import com.projeto.marvel.R

private const val PAPER_MARGIN_DP = 5f
private const val INK_LINE_DP = 1.5f

/**
 * Card com foto (herói, HQ, filme, criador) no traço da época:
 * - Retrô: figurinha de banca — margem de papel creme, filete de nanquim na foto, nome numa caixa
 *   de legenda (cada nome numa tinta de [retroInk]) em letra de balão e o detalhe em itálico;
 * - Anos 90: moldura neon chanfrada (o `bg_card` da época) e nome amarelo em itálico condensado;
 * - Moderno: fica como está (limpo).
 */
fun dressCard(card: View, image: ImageView, title: TextView, detail: TextView? = null) {
    val context = card.context
    fun color(res: Int) = ContextCompat.getColor(context, res)
    when (context.era()) {
        Era.RETRO -> {
            card.background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL, color(R.color.comic_paper))
            val margin = (PAPER_MARGIN_DP * context.resources.displayMetrics.density).toInt()
            card.setPadding(margin, margin, margin + margin, margin + margin)
            image.foreground = GradientDrawable().apply {
                setStroke((INK_LINE_DP * context.resources.displayMetrics.density).toInt(), color(R.color.ink))
            }
            title.background = EraPanelDrawable(context, EraPanelDrawable.Kind.CAPTION, color(R.color.caption_yellow))
            title.setTextColor(color(R.color.ink))
            // Cada nome na sua tinta (a mesma sempre, mesmo quando a célula é reciclada).
            title.doAfterTextChanged { text -> title.tintCaption(context.retroInk(text.toString().hashCode())) }
            title.typeface = ResourcesCompat.getFont(context, R.font.comic_neue_bold)
            title.isAllCaps = true
            val gap = context.resources.getDimensionPixelSize(R.dimen.space_xs)
            title.setPadding(gap * 2, gap, gap * 2, gap + gap)
            detail?.setTextColor(color(R.color.ink))
            detail?.typeface = ResourcesCompat.getFont(context, R.font.comic_neue_bold_italic)
        }
        Era.NINETIES -> {
            title.setTextColor(color(R.color.logo_yellow))
            title.typeface = ResourcesCompat.getFont(context, R.font.barlow_condensed_bold_italic)
            title.isAllCaps = true
            detail?.setTextColor(color(R.color.nineties_chip_text))
        }
        Era.MODERN -> Unit
    }
}

/** Avatar redondo no traço da época: quadro de nanquim (Retrô), moldura neon (Anos 90) ou anel vermelho (Moderno). */
fun ImageView.eraAvatar() {
    when (context.era()) {
        Era.RETRO, Era.NINETIES -> {
            background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
            foreground = null
            val frame = context.resources.getDimensionPixelSize(R.dimen.space_xs)
            setPadding(frame, frame, frame, frame)
        }
        Era.MODERN -> foreground = ContextCompat.getDrawable(context, R.drawable.fg_modern_ring)
    }
}
