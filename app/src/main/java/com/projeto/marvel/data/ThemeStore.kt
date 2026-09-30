package com.projeto.marvel.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/** Tema do app: segue o sistema, ou fixo claro ("papel de gibi") ou escuro. */
enum class ThemeMode(val nightMode: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/** Tema escolhido no Perfil, salvo no aparelho; aplicado ao abrir o app (MarvelApp). */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ThemeMode = prefs.getString(KEY_MODE, null)
        ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
        ?: ThemeMode.SYSTEM

    /** Salva e já aplica (a Activity é recriada com o tema novo). */
    fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    private companion object {
        const val PREFS_NAME = "theme"
        const val KEY_MODE = "mode"
    }
}
