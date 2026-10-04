package com.projeto.marvel.ui.home

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import com.projeto.marvel.data.Mission
import com.projeto.marvel.data.MissionProgress
import com.projeto.marvel.databinding.ItemMissionBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.album.colors
import com.projeto.marvel.ui.album.label
import com.projeto.marvel.ui.comicBox

// Missões da Início como página de HQ: diárias e semanais, com ícone, progresso e o pacote de prêmio.

private val TEXTS = mapOf(
    "win3" to (R.string.mission_win3 to R.drawable.ic_swords),
    "ultimate2" to (R.string.mission_ultimate2 to R.drawable.ic_move_magic),
    "pack2" to (R.string.mission_pack2 to R.drawable.ic_album),
    "upgrade3" to (R.string.mission_upgrade3 to R.drawable.ic_edit),
    "geek1" to (R.string.mission_geek1 to R.drawable.ic_geek),
    "quiz1" to (R.string.mission_quiz1 to R.drawable.ic_mask),
    "pvp1" to (R.string.mission_pvp1 to R.drawable.ic_group),
    "guess5" to (R.string.mission_guess5 to R.drawable.ic_search),
    "look1" to (R.string.mission_look1 to R.drawable.ic_face_scan),
    "review1" to (R.string.mission_review1 to R.drawable.ic_shelf),
    "squad1" to (R.string.mission_squad1 to R.drawable.ic_move_guard),
    "win15" to (R.string.mission_win15 to R.drawable.ic_swords),
    "gauntlet1" to (R.string.mission_gauntlet1 to R.drawable.ic_explore),
    "guess25" to (R.string.mission_guess25 to R.drawable.ic_search),
    "squad3" to (R.string.mission_squad3 to R.drawable.ic_move_guard),
    "pack10" to (R.string.mission_pack10 to R.drawable.ic_album),
    "ultimate10" to (R.string.mission_ultimate10 to R.drawable.ic_move_magic),
    "trunfo1" to (R.string.mission_trunfo1 to R.drawable.ic_album),
    "memory1" to (R.string.mission_memory1 to R.drawable.ic_games),
    "quote5" to (R.string.mission_quote5 to R.drawable.ic_search),
    "comic1" to (R.string.mission_comic1 to R.drawable.ic_edit),
    "trunfo5" to (R.string.mission_trunfo5 to R.drawable.ic_album),
    "memory5" to (R.string.mission_memory5 to R.drawable.ic_games)
)

@StringRes
private fun Mission.text() = TEXTS.getValue(id).first

@DrawableRes
private fun Mission.icon() = TEXTS.getValue(id).second

private const val DONE_ALPHA = 0.55f
private const val MAX_PIPS = 10
private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

/**
 * Missões como uma página de HQ: as diárias numa tira de quadros e as semanais noutra. Cada quadro
 * mostra o progresso em blocos (até [MAX_PIPS]) e o prêmio numa etiqueta no metal do pacote; completa,
 * a etiqueta vira "PEGAR!" numa explosão.
 */
fun LinearLayout.bindMissions(missions: List<MissionProgress>, onClaim: (Mission) -> Unit) {
    removeAllViews()
    missions.partition { !it.mission.weekly }.toList().filter { it.isNotEmpty() }.forEach { strip ->
        val row = LinearLayout(context).apply { clipChildren = false }
        addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, WRAP))
        strip.forEach { ItemMissionBinding.inflate(LayoutInflater.from(context), row, true).bind(it, onClaim) }
    }
}

private fun ItemMissionBinding.bind(state: MissionProgress, onClaim: (Mission) -> Unit) {
    val context = root.context
    val mission = state.mission
    kind.setText(if (mission.weekly) R.string.mission_weekly else R.string.mission_daily)
    icon.setImageResource(mission.icon())
    title.setText(mission.text())
    count.text = context.getString(R.string.mission_count, state.progress, mission.goal)
    pips.bindPips(state.progress, mission.goal)
    root.alpha = if (state.claimed) DONE_ALPHA else 1f
    claim.setOnClickListener(null)
    claim.isClickable = false
    when {
        state.claimed -> {
            claim.background = null
            claim.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            claim.setText(R.string.mission_claimed)
        }
        state.done -> {
            claim.setText(R.string.mission_claim)
            claim.comicBox(BoxStyle.BURST, ContextCompat.getColor(context, R.color.primary))
            // Explosão compacta: o quadro é estreito para o padding padrão dela.
            val pad = context.resources.getDimensionPixelSize(R.dimen.space_md)
            claim.setPadding(pad, pad / 2, pad, pad / 2)
            claim.setOnClickListener { onClaim(mission) }
        }
        else -> {
            claim.text = context.getString(mission.reward.label())
            claim.setTextColor(ContextCompat.getColor(context, R.color.ink))
            claim.background = rewardTag(context, mission.reward.colors()[1])
        }
    }
}

/** Blocos com contorno de nanquim: cheios em dourado até o progresso. */
private fun LinearLayout.bindPips(progress: Int, goal: Int) {
    removeAllViews()
    val count = goal.coerceIn(1, MAX_PIPS)
    val filled = progress.coerceAtMost(goal) * count / goal.coerceAtLeast(1)
    val gap = resources.getDimensionPixelSize(R.dimen.mission_gutter)
    repeat(count) { i ->
        val pip = View(context).apply { background = pipDrawable(context, filled = i < filled) }
        addView(pip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = gap })
    }
}

private fun pipDrawable(context: Context, filled: Boolean) = GradientDrawable().apply {
    setColor(ContextCompat.getColor(context, if (filled) R.color.accent else R.color.surface))
    setStroke(context.resources.getDimensionPixelSize(R.dimen.ink_width), ContextCompat.getColor(context, R.color.ink))
}

/** Etiqueta do prêmio no metal claro do pacote, contornada de nanquim. */
private fun rewardTag(context: Context, @ColorRes metal: Int) = GradientDrawable().apply {
    setColor(ContextCompat.getColor(context, metal))
    cornerRadius = context.resources.getDimension(R.dimen.radius_small)
    setStroke(context.resources.getDimensionPixelSize(R.dimen.ink_width), ContextCompat.getColor(context, R.color.ink))
}
