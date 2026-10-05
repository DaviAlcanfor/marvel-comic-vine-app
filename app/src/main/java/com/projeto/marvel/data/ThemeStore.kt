package com.projeto.marvel.data

import android.content.Context
import androidx.annotation.StyleRes
import com.projeto.marvel.R
import androidx.core.content.edit

/**
 * Tema de época, sempre sobre o tema escuro (values-night/): Retrô e Anos 90 são uma sobreposição
 * ([overlay], aplicada na MainActivity) e o Moderno é o escuro puro. O Retrô claro (values/) saiu:
 * quem tinha "claro" ou "sistema" salvo cai no Retrô.
 */
enum class ThemeMode(@StyleRes val overlay: Int?) {
    RETRO(R.style.ThemeOverlay_Marvel_RetroDark),
    NINETIES(R.style.ThemeOverlay_Marvel_Nineties),
    DARK(null)
}

/** Tema escolhido no Perfil, salvo no aparelho; aplicado ao abrir o app (MarvelApp). */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ThemeMode = prefs.getString(KEY_MODE, null)
        ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
        ?: ThemeMode.RETRO

    /** Só salva: a sobreposição vale na próxima criação da Activity (quem troca chama `recreate`). */
    fun set(mode: ThemeMode) = prefs.edit { putString(KEY_MODE, mode.name) }

    /** Roteiro da abertura escolhido no Perfil (nome de `OpeningScript`); null = automático. */
    fun opening(): String? = prefs.getString(KEY_OPENING, null)

    fun setOpening(name: String) = prefs.edit { putString(KEY_OPENING, name) }

    private companion object {
        const val PREFS_NAME = "theme"
        const val KEY_MODE = "mode"
        const val KEY_OPENING = "opening"
    }
}
