package com.projeto.marvel.ui.comics

import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemComicBinding
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.era

/** Capa de HQ/filme no traço da época: legenda em maiúsculas e nota em vermelho (Retrô) ou amarelo (Anos 90). */
fun ItemComicBinding.applyEra() {
    val context = root.context
    val era = context.era()
    if (era == Era.MODERN) return
    title.isAllCaps = true
    title.typeface = ResourcesCompat.getFont(
        context,
        if (era == Era.RETRO) R.font.comic_neue_bold else R.font.barlow_condensed_bold
    )
    rating.setTextColor(ContextCompat.getColor(context, if (era == Era.RETRO) R.color.primary else R.color.logo_yellow))
    rating.isAllCaps = true
}
