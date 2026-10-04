package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import kotlin.random.Random

// Missões: jeitos de ganhar pacotes jogando o app inteiro. Todo dia sorteia 3 diárias e toda
// semana 2 semanais (mesmo sorteio o dia/semana todo); cada uma paga um tipo de pacote.

/** O que acontece no app e conta para as missões. */
enum class MissionEvent {
    BATTLE_WIN, SQUAD_WIN, GAUNTLET_DONE, PVP_PLAYED, ULTIMATE, PACK_OPENED, UPGRADE_POINT,
    GUESS_RIGHT, QUIZ_DONE, LOOK_ALIKE, REVIEW, GEEK_QUESTION,
    TRUNFO_WIN, MEMORY_DONE, QUOTE_RIGHT, COMIC_MADE
}

data class Mission(
    val id: String,
    val event: MissionEvent,
    val goal: Int,
    val reward: PackType,
    val weekly: Boolean = false
)

@Suppress("MagicNumber") // metas e prêmios são a própria tabela de missões
val DAILY_MISSIONS = listOf(
    Mission("win3", MissionEvent.BATTLE_WIN, 3, PackType.BASIC),
    Mission("ultimate2", MissionEvent.ULTIMATE, 2, PackType.BASIC),
    Mission("pack2", MissionEvent.PACK_OPENED, 2, PackType.BASIC),
    Mission("upgrade3", MissionEvent.UPGRADE_POINT, 3, PackType.BASIC),
    Mission("geek1", MissionEvent.GEEK_QUESTION, 1, PackType.BASIC),
    Mission("quiz1", MissionEvent.QUIZ_DONE, 1, PackType.BASIC),
    Mission("pvp1", MissionEvent.PVP_PLAYED, 1, PackType.BASIC),
    Mission("guess5", MissionEvent.GUESS_RIGHT, 5, PackType.SILVER),
    Mission("look1", MissionEvent.LOOK_ALIKE, 1, PackType.SILVER),
    Mission("review1", MissionEvent.REVIEW, 1, PackType.SILVER),
    Mission("squad1", MissionEvent.SQUAD_WIN, 1, PackType.SILVER),
    Mission("trunfo1", MissionEvent.TRUNFO_WIN, 1, PackType.SILVER),
    Mission("memory1", MissionEvent.MEMORY_DONE, 1, PackType.BASIC),
    Mission("quote5", MissionEvent.QUOTE_RIGHT, 5, PackType.BASIC),
    Mission("comic1", MissionEvent.COMIC_MADE, 1, PackType.SILVER)
)

@Suppress("MagicNumber")
val WEEKLY_MISSIONS = listOf(
    Mission("win15", MissionEvent.BATTLE_WIN, 15, PackType.GOLD, weekly = true),
    Mission("gauntlet1", MissionEvent.GAUNTLET_DONE, 1, PackType.GOLD, weekly = true),
    Mission("guess25", MissionEvent.GUESS_RIGHT, 25, PackType.GOLD, weekly = true),
    Mission("squad3", MissionEvent.SQUAD_WIN, 3, PackType.GOLD, weekly = true),
    Mission("pack10", MissionEvent.PACK_OPENED, 10, PackType.SILVER, weekly = true),
    Mission("ultimate10", MissionEvent.ULTIMATE, 10, PackType.SILVER, weekly = true),
    Mission("trunfo5", MissionEvent.TRUNFO_WIN, 5, PackType.GOLD, weekly = true),
    Mission("memory5", MissionEvent.MEMORY_DONE, 5, PackType.SILVER, weekly = true)
)

private const val DAILY_COUNT = 3
private const val WEEKLY_COUNT = 2
private const val DAYS_PER_WEEK = 7

/** Chave do período: o dia (diárias) ou a semana (semanais). Muda = missões e progresso novos. */
fun periodOf(mission: Mission, today: LocalDate) =
    if (mission.weekly) "w${today.toEpochDay() / DAYS_PER_WEEK}" else "d${today.toEpochDay()}"

/** As missões ativas hoje: sorteio fixo pela data, igual o dia (e a semana) inteiro. */
fun activeMissions(today: LocalDate): List<Mission> {
    val week = today.toEpochDay() / DAYS_PER_WEEK
    return DAILY_MISSIONS.shuffled(Random(today.toEpochDay())).take(DAILY_COUNT) +
        WEEKLY_MISSIONS.shuffled(Random(week)).take(WEEKLY_COUNT)
}

data class MissionProgress(val mission: Mission, val progress: Int, val claimed: Boolean) {
    val done get() = progress >= mission.goal
}

/** Progresso e prêmios pegos, no aparelho. Chave: período + missão (outro período = zerado). */
class MissionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val stickers = StickerStore(context)

    private fun key(mission: Mission, today: LocalDate) = "${periodOf(mission, today)}:${mission.id}"

    fun record(event: MissionEvent, today: LocalDate = LocalDate.now(), amount: Int = 1) {
        val matching = activeMissions(today).filter { it.event == event }
        if (matching.isEmpty()) return
        prefs.edit {
            matching.forEach { mission ->
                val key = key(mission, today)
                putInt(key, (prefs.getInt(key, 0) + amount).coerceAtMost(mission.goal))
            }
        }
    }

    fun missions(today: LocalDate = LocalDate.now()) = activeMissions(today).map { mission ->
        val key = key(mission, today)
        MissionProgress(mission, prefs.getInt(key, 0), prefs.getBoolean("$key:$CLAIMED", false))
    }

    /** Pega o prêmio (um pacote do tipo da missão). False se não terminou ou já pegou. */
    fun claim(mission: Mission, today: LocalDate = LocalDate.now()): Boolean {
        val state = missions(today).firstOrNull { it.mission == mission }
        if (state == null || !state.done || state.claimed) return false
        prefs.edit { putBoolean("${key(mission, today)}:$CLAIMED", true) }
        stickers.addPack(mission.reward)
        return true
    }

    private companion object {
        const val PREFS_NAME = "missions"
        const val CLAIMED = "claimed"
    }
}

/** Atalho para registrar um evento de qualquer tela. */
fun Context.mission(event: MissionEvent) = MissionStore(this).record(event)
