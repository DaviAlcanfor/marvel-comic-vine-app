package com.projeto.marvel.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * Tema de época. Retrô é o claro ("papel de gibi") e Moderno o escuro, pelos recursos de
 * values/ e values-night/; Anos 90 é o escuro com `ThemeOverlay.Marvel.Nineties` por cima
 * ([overlay], aplicado na MainActivity). Sistema = Retrô no claro, Moderno no escuro.
 */
enum class ThemeMode(val nightMode: Int, val nineties: Boolean = false) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    NINETIES(AppCompatDelegate.MODE_NIGHT_YES, nineties = true),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/** Tema escolhido no Perfil, salvo no aparelho; aplicado ao abrir o app (MarvelApp). */
class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): ThemeMode = prefs.getString(KEY_MODE, null)
        ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
        ?: ThemeMode.SYSTEM

    /** Salva e já aplica o modo noturno; trocar só a sobreposição (Moderno ↔ Anos 90) pede recriar a tela. */
    fun set(mode: ThemeMode) {
        prefs.edit { putString(KEY_MODE, mode.name) }
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }

    /** Roteiro da abertura escolhido no Perfil (nome de `OpeningScript`); null = automático. */
    fun opening(): String? = prefs.getString(KEY_OPENING, null)

    fun setOpening(name: String) = prefs.edit { putString(KEY_OPENING, name) }

    private companion object {
        const val PREFS_NAME = "theme"
        const val KEY_MODE = "mode"
        const val KEY_OPENING = "opening"
    }
}
