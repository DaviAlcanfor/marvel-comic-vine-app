package com.projeto.marvel.data

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.projeto.marvel.R
import androidx.core.content.edit

/**
 * Tema de época. Retrô é o claro ("papel de gibi") e Moderno o escuro, pelos recursos de
 * values/ e values-night/; Retrô escuro e Anos 90 são o escuro com uma sobreposição ([overlay],
 * aplicada na MainActivity). Sistema = Retrô claro de dia, Retrô escuro à noite.
 */
enum class ThemeMode(val nightMode: Int, @StyleRes val overlay: Int? = null) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    RETRO_DARK(AppCompatDelegate.MODE_NIGHT_YES, R.style.ThemeOverlay_Marvel_RetroDark),
    NINETIES(AppCompatDelegate.MODE_NIGHT_YES, R.style.ThemeOverlay_Marvel_Nineties),
    DARK(AppCompatDelegate.MODE_NIGHT_YES)
}

/** A sobreposição que vale agora: no "Seguir o sistema" com o celular escuro, o Retrô escuro. */
@StyleRes
fun ThemeMode.overlayFor(night: Boolean): Int? =
    overlay ?: R.style.ThemeOverlay_Marvel_RetroDark.takeIf { this == ThemeMode.SYSTEM && night }

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
