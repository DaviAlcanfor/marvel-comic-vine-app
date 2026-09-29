package com.projeto.marvel.ui.album

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import com.projeto.marvel.R
import com.projeto.marvel.data.Mission
import com.projeto.marvel.data.MissionProgress
import com.projeto.marvel.databinding.ItemMissionBinding

// Lista de missões do Álbum: diárias e semanais, com o progresso e o pacote de prêmio.

private val TEXTS = mapOf(
    "win3" to R.string.mission_win3,
    "ultimate2" to R.string.mission_ultimate2,
    "pack2" to R.string.mission_pack2,
    "upgrade3" to R.string.mission_upgrade3,
    "geek1" to R.string.mission_geek1,
    "quiz1" to R.string.mission_quiz1,
    "pvp1" to R.string.mission_pvp1,
    "guess5" to R.string.mission_guess5,
    "look1" to R.string.mission_look1,
    "review1" to R.string.mission_review1,
    "squad1" to R.string.mission_squad1,
    "win15" to R.string.mission_win15,
    "gauntlet1" to R.string.mission_gauntlet1,
    "guess25" to R.string.mission_guess25,
    "squad3" to R.string.mission_squad3,
    "pack10" to R.string.mission_pack10,
    "ultimate10" to R.string.mission_ultimate10
)

@StringRes
private fun Mission.text() = TEXTS.getValue(id)

private const val DONE_ALPHA = 0.55f

fun LinearLayout.bindMissions(missions: List<MissionProgress>, onClaim: (Mission) -> Unit) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    missions.forEach { state ->
        val mission = state.mission
        ItemMissionBinding.inflate(inflater, this, true).apply {
            val kind = context.getString(if (mission.weekly) R.string.mission_weekly else R.string.mission_daily)
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
