package com.projeto.marvel.ui.battle

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import coil.load
import coil.transform.CircleCropTransformation
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentBattleBinding

// Batalha 3×3: retratinhos dos trios (o ativo primeiro, caídos em cinza) e o botão de trocar.

private const val FALLEN_ALPHA = 0.4f
private const val ACTIVE_SCALE = 1.25f
private val GRAYSCALE = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

fun FragmentBattleBinding.bindSquads(game: BattleUiState.Success, onSwap: () -> Unit) {
    val squad = game.playerBench.isNotEmpty() || game.cpuBench.isNotEmpty()
    playerSquad.isVisible = squad
    cpuSquad.isVisible = squad
    if (squad) {
        playerSquad.fill(listOf(game.player) + game.playerBench)
        cpuSquad.fill(listOf(game.cpu) + game.cpuBench)
    }
    swapButton.isVisible = game.playerBench.any { it.hp > 0 } && game.winner == null
    swapButton.isEnabled = !game.busy
    swapButton.setOnClickListener { onSwap() }
}

/** Reaproveita as ImageViews: só recarrega a foto quando o lutador daquela posição muda. */
private fun LinearLayout.fill(team: List<Combatant>) {
    val size = resources.getDimensionPixelSize(R.dimen.squad_portrait)
    val gap = resources.getDimensionPixelSize(R.dimen.space_xs)
    while (childCount < team.size) {
        addView(ImageView(context), LinearLayout.LayoutParams(size, size).apply { marginEnd = gap })
    }
    team.forEachIndexed { index, combatant ->
        val view = getChildAt(index) as ImageView
        if (view.tag != combatant.fighter.imageUrl) {
            view.tag = combatant.fighter.imageUrl
            view.load(combatant.fighter.imageUrl) { transformations(CircleCropTransformation()) }
        }
        val alive = combatant.hp > 0
        view.alpha = if (alive) 1f else FALLEN_ALPHA
        view.colorFilter = if (alive) null else GRAYSCALE
        val scale = if (index == 0) ACTIVE_SCALE else 1f
        view.scaleX = scale
        view.scaleY = scale
        view.contentDescription = combatant.fighter.name
    }
}
