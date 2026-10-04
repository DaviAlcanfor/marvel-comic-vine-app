package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit

/** Jogos com recorde guardado; [lowerIsBetter] = menos é melhor (jogadas da Memória). */
enum class GameRecord(val lowerIsBetter: Boolean = false) {
    TRUNFO_WINS,
    MEMORY_MOVES(lowerIsBetter = true),
    QUOTE_STREAK
}

/** [new] bate o recorde [old] (sem recorde ainda, qualquer resultado bate). */
fun isNewRecord(old: Int?, new: Int, lowerIsBetter: Boolean) =
    old == null || if (lowerIsBetter) new < old else new > old

/** Recordes dos jogos da aba Jogos, no aparelho. */
class GameRecordStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun best(game: GameRecord): Int? = prefs.getInt(game.name, NONE).takeIf { it != NONE }

    /** Guarda [value] se bater o recorde; true = recorde novo. */
    fun record(game: GameRecord, value: Int): Boolean {
        if (!isNewRecord(best(game), value, game.lowerIsBetter)) return false
        prefs.edit { putInt(game.name, value) }
        return true
    }

    /** Soma 1 (vitórias no Trunfo). */
    fun increment(game: GameRecord): Int =
        ((best(game) ?: 0) + 1).also { value -> prefs.edit { putInt(game.name, value) } }

    private companion object {
        const val PREFS_NAME = "game_records"
        const val NONE = Int.MIN_VALUE
    }
}
