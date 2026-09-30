package com.projeto.marvel.ui.home

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.projeto.marvel.R
import com.projeto.marvel.data.Mission
import com.projeto.marvel.data.MissionProgress
import com.projeto.marvel.databinding.ItemMissionBinding
import com.projeto.marvel.ui.album.label

// Lista de missões da Início: diárias e semanais, com ícone, progresso e o pacote de prêmio.

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
    "ultimate10" to (R.string.mission_ultimate10 to R.drawable.ic_move_magic)
)

@StringRes
private fun Mission.text() = TEXTS.getValue(id).first

@DrawableRes
private fun Mission.icon() = TEXTS.getValue(id).second

private const val DONE_ALPHA = 0.55f

fun LinearLayout.bindMissions(missions: List<MissionProgress>, onClaim: (Mission) -> Unit) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    missions.forEach { state ->
        val mission = state.mission
        ItemMissionBinding.inflate(inflater, this, true).apply {
            val kind = context.getString(if (mission.weekly) R.string.mission_weekly else R.string.mission_daily)
            icon.setImageResource(mission.icon())
            title.text = context.getString(R.string.mission_title, kind, context.getString(mission.text()))
            bar.max = mission.goal
            bar.setProgressCompat(state.progress, false)
            detail.text = context.getString(
                R.string.mission_detail,
                state.progress,
                mission.goal,
                context.getString(mission.reward.label())
            )
            claim.visibility = if (state.done) View.VISIBLE else View.GONE
            claim.setText(if (state.claimed) R.string.mission_claimed else R.string.mission_claim)
            claim.isEnabled = state.done && !state.claimed
            root.alpha = if (state.claimed) DONE_ALPHA else 1f
            claim.setOnClickListener { onClaim(mission) }
        }
    }
}
