package com.projeto.marvel.ui

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.projeto.marvel.R

/** Época dos quadrinhos do tema atual (atributo `eraStyle`, ver `ThemeMode`). */
enum class Era { RETRO, NINETIES, MODERN }

fun Context.era(): Era {
    val value = TypedValue()
    if (!theme.resolveAttribute(R.attr.eraStyle, value, true)) return Era.RETRO
    return Era.entries.getOrElse(value.data) { Era.RETRO }
}

/** Contorno de quadros, balões e botões da época (o nanquim no Retrô, neon nos Anos 90). */
@ColorInt
fun Context.eraOutline(): Int = themeValue(R.attr.eraOutline).data

/** Espessura do contorno ([R.attr.eraInkWidth]) ou da sombra dura ([R.attr.eraShadow]), em px. */
fun Context.eraDimen(@AttrRes attr: Int): Float = themeValue(attr).getDimension(resources.displayMetrics)

private fun Context.themeValue(@AttrRes attr: Int) = TypedValue().also { theme.resolveAttribute(attr, it, true) }
