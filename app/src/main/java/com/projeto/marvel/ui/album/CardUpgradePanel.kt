package com.projeto.marvel.ui.album

import android.view.LayoutInflater
import android.view.View
import com.projeto.marvel.R
import com.projeto.marvel.data.EVOLVE_COST
import com.projeto.marvel.data.Stat
import com.projeto.marvel.data.UPGRADABLE
import com.projeto.marvel.data.canEvolve
import com.projeto.marvel.databinding.FragmentAlbumBinding
import com.projeto.marvel.databinding.ItemUpgradeStatBinding

// Painel de melhorias embaixo da carta em 3D: pontos por atributo (+/–) e evoluir para Divina.

private val LABELS = mapOf(
    Stat.ATTACK to (R.string.stat_attack to R.drawable.ic_move_strike),
    Stat.DEFENSE to (R.string.stat_defense to R.drawable.ic_move_guard),
    Stat.SPEED to (R.string.stat_speed to R.drawable.ic_move_dodge),
    Stat.INTELLIGENCE to (R.string.stat_intelligence to R.drawable.ic_geek)
)

private const val DISABLED_ALPHA = 0.35f

fun FragmentAlbumBinding.bindUpgrade(
    card: CardUpgrade,
    onAllocate: (Stat, Int) -> Unit,
    onEvolve: () -> Unit
) {
    val context = root.context
    upgradeToggle.text = context.getString(
        if (upgradePanel.visibility == View.VISIBLE) R.string.upgrade_close else R.string.upgrade_open,
        card.free
    )
    upgradeToggle.setOnClickListener {
        val open = upgradePanel.visibility != View.VISIBLE
        upgradePanel.visibility = if (open) View.VISIBLE else View.GONE
        upgradeToggle.text = context.getString(if (open) R.string.upgrade_close else R.string.upgrade_open, card.free)
    }
    upgradeTitle.text = context.getString(R.string.upgrade_title, card.level, card.free, card.points)
    val final = card.final
    upgradeHint.text = context.getString(if (final == null) R.string.upgrade_loading else R.string.upgrade_hint)
    upgradeRows.removeAllViews()
    val inflater = LayoutInflater.from(context)
    if (final != null) {
        UPGRADABLE.forEach { stat ->
            ItemUpgradeStatBinding.inflate(inflater, upgradeRows, true)
                .bindRow(stat, final.stats[stat] ?: 0, card, onAllocate)
        }
    }
    val sticker = card.sticker
    val evolvable = canEvolve(sticker.count, sticker.golden)
    evolveButton.isEnabled = evolvable
    evolveButton.alpha = if (evolvable || sticker.golden) 1f else DISABLED_ALPHA
    evolveButton.text = when {
        sticker.golden -> context.getString(R.string.upgrade_golden)
        evolvable -> context.getString(R.string.upgrade_evolve, EVOLVE_COST)
        else -> context.getString(R.string.upgrade_evolve_missing, EVOLVE_COST, sticker.count)
    }
    evolveButton.setOnClickListener { onEvolve() }
}

/** Atributo [stat] com o valor final [total] e os pontos que você pôs nele. */
private fun ItemUpgradeStatBinding.bindRow(stat: Stat, total: Int, card: CardUpgrade, onAllocate: (Stat, Int) -> Unit) {
    val (text, icon) = LABELS.getValue(stat)
    label.setText(text)
    label.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
    val spent = card.allocation[stat] ?: 0
    value.text = if (spent > 0) "$total (+$spent)" else "$total"
    minus.isEnabled = spent > 0
    minus.alpha = if (spent > 0) 1f else DISABLED_ALPHA
    plus.isEnabled = card.free > 0
    plus.alpha = if (card.free > 0) 1f else DISABLED_ALPHA
    minus.setOnClickListener { onAllocate(stat, -1) }
    plus.setOnClickListener { onAllocate(stat, 1) }
}
