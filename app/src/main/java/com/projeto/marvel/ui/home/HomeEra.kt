package com.projeto.marvel.ui.home

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentHomeBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.EraPanelDrawable
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.era

// A Início vestida de cada época (proposta aprovada no canvas "Épocas dos quadrinhos"):
// - Retrô: avatar no canto da capa (12¢ · Nº 1), "Enquanto isso…" amarelo, nome do herói numa
//   explosão, resumo numa caixa de narração e títulos de seção em balões brancos ovais.
// - Anos 90: avatar com moldura neon chanfrada, etiqueta magenta, nome com extrusão azul e
//   títulos em balão "rádio" lilás.
// - Moderno: avatar redondo com anel vermelho, herói de ponta a ponta (cinema), etiqueta vermelha
//   e títulos de seção limpos com um traço vermelho.

private const val NINETIES_SKEW = -0.18f
private const val NINETIES_SHADOW_DP = 3f
private const val MODERN_NAME_SP = 44f
private const val RULE_GAP_DP = 10f

/** Títulos de seção e a inclinação de cada um (o formato muda com a época). */
@Suppress("MagicNumber") // a inclinação de cada título é a própria tabela
private fun FragmentHomeBinding.sections() = listOf(
    heroMoviesTitle to 1.5f,
    dailyTrailTitle to -3f,
    debutsTitle to -1.5f,
    readingTitle to 2f,
    missionsTitle to 2.5f
)

fun FragmentHomeBinding.applyEra() {
    val context = root.context
    val era = context.era()
    fun color(res: Int) = ContextCompat.getColor(context, res)
    styleAvatar(era)
    styleHeroCard(era)
    sections().forEachIndexed { index, (view, tilt) ->
        when (era) {
            Era.RETRO -> view.comicBox(BoxStyle.SPEECH, color(R.color.white), tailOnLeft = index % 2 == 0)
            Era.NINETIES -> {
                view.comicBox(BoxStyle.SPEECH, color(R.color.nineties_balloon), tailOnLeft = index % 2 == 0)
                view.isAllCaps = true
            }
            Era.MODERN -> view.ruleTitle()
        }
        view.rotation = if (era == Era.MODERN) 0f else tilt
    }
    when (era) {
        Era.RETRO -> favoriteLine.comicBox(BoxStyle.CAPTION, color(R.color.accent))
        Era.NINETIES -> {
            favoriteLine.isAllCaps = true
            favoriteLine.setTextColor(color(R.color.nineties_text_secondary))
        }
        Era.MODERN -> Unit
    }
}

private fun FragmentHomeBinding.styleAvatar(era: Era) {
    val context = root.context
    when (era) {
        Era.RETRO -> {
            // Canto da capa: quadrado com nanquim, à esquerda do título, com preço e número.
            header.removeView(avatarBox)
            header.addView(avatarBox, 0)
            avatarBox.updateLayoutParams<LinearLayout.LayoutParams> {
                marginStart = 0
                marginEnd = context.resources.getDimensionPixelSize(R.dimen.space_md)
            }
            avatarBox.background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
            avatar.background = null
            avatar.foreground = null
            coverPrice.visibility = View.VISIBLE
        }
        Era.NINETIES -> {
            avatar.background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
            avatar.foreground = null
            val frame = context.resources.getDimensionPixelSize(R.dimen.space_xs)
            avatar.setPadding(frame, frame, frame, frame)
        }
        Era.MODERN -> avatar.foreground = ContextCompat.getDrawable(context, R.drawable.fg_modern_ring)
    }
}

private fun FragmentHomeBinding.styleHeroCard(era: Era) {
    val context = root.context
    // O resumo cabe inteiro (3 linhas) em vez de cortar com "…" na 2ª.
    heroDeck.maxLines = DECK_LINES
    fun color(res: Int) = ContextCompat.getColor(context, res)
    when (era) {
        Era.RETRO -> {
            heroCaption.setText(R.string.home_hero_of_the_day_retro)
            heroCaption.comicBox(BoxStyle.CAPTION, color(R.color.accent))
            heroFade.visibility = View.GONE
            heroName.comicBox(BoxStyle.BURST, color(R.color.logo_yellow))
            heroName.typeface = ResourcesCompat.getFont(context, R.font.bangers)
            heroName.updateLayoutParams<LinearLayout.LayoutParams> {
                width = LinearLayout.LayoutParams.WRAP_CONTENT
                gravity = Gravity.END
            }
            heroName.rotation = RETRO_NAME_TILT
            heroDeck.comicBox(BoxStyle.CAPTION, color(R.color.paper))
            heroDeck.isAllCaps = true
            heroDeck.textSize = RETRO_DECK_SP
        }
        Era.NINETIES -> {
            heroCaption.comicBox(BoxStyle.CAPTION, color(R.color.nineties_magenta))
            heroName.typeface = ResourcesCompat.getFont(context, R.font.bungee)
            heroName.setTextColor(color(R.color.logo_yellow))
            heroName.paint.textSkewX = NINETIES_SKEW
            val depth = NINETIES_SHADOW_DP * context.resources.displayMetrics.density
            heroName.setShadowLayer(HARD_SHADOW_RADIUS, depth, depth, color(R.color.nineties_extrusion))
        }
        Era.MODERN -> {
            heroCaption.comicBox(BoxStyle.CAPTION, color(R.color.primary))
            heroCaption.rotation = 0f
            heroName.typeface = ResourcesCompat.getFont(context, R.font.bebas_neue)
            heroName.textSize = MODERN_NAME_SP
            // Herói de ponta a ponta, como um quadro de cinema: sai do recuo da tela.
            val gutter = context.resources.getDimensionPixelSize(R.dimen.space_lg)
            heroCard.updateLayoutParams<LinearLayout.LayoutParams> {
                marginStart = -gutter
                marginEnd = -gutter
            }
            heroCard.background = null
            // Sem isso o recuo da coluna recorta o quadro de volta (e o texto) nas bordas.
            (heroCard.parent as ViewGroup).clipToPadding = false
            (heroCard.parent as ViewGroup).clipChildren = false
        }
    }
}

/** Moderno: título de seção sem caixa, em letreiro, com um traço vermelho na frente. */
private fun TextView.ruleTitle() {
    background = null
    setPadding(0, 0, 0, 0)
    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
    setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_title_rule, 0, 0, 0)
    compoundDrawablePadding = (RULE_GAP_DP * resources.displayMetrics.density).toInt()
}

private const val RETRO_NAME_TILT = 8f
private const val RETRO_DECK_SP = 12f
private const val DECK_LINES = 3

// Raio ~0: sombra dura, sem desfoque (como a extrusão do letreiro).
private const val HARD_SHADOW_RADIUS = 0.01f
