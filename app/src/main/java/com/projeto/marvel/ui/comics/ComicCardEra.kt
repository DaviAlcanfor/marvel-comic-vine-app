package com.projeto.marvel.ui.comics

import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemComicBinding
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.dressCard
import com.projeto.marvel.ui.era

/** Capa de HQ/filme no traço da época (ver [dressCard]); a nota fica vermelha (Retrô) ou amarela (Anos 90). */
fun ItemComicBinding.applyEra() {
    val context = root.context
    val era = context.era()
    dressCard(front, cover, title, rating)
    if (era == Era.MODERN) return
    rating.setTextColor(ContextCompat.getColor(context, if (era == Era.RETRO) R.color.primary else R.color.logo_yellow))
    rating.isAllCaps = true
}
