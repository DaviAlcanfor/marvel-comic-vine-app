package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit

data class BattleRecord(val wins: Int = 0, val losses: Int = 0)

/** Placar de batalhas do jogador, salvo no aparelho via SharedPreferences. */
class BattleRecordStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get() = BattleRecord(prefs.getInt(KEY_WINS, 0), prefs.getInt(KEY_LOSSES, 0))

    /** Soma uma vitória ou derrota e devolve o placar atualizado. */
    fun add(won: Boolean): BattleRecord {
        val key = if (won) KEY_WINS else KEY_LOSSES
        prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
        return get()
    }

    private companion object {
        const val PREFS_NAME = "battle_record"
        const val KEY_WINS = "wins"
        const val KEY_LOSSES = "losses"
    }
}
