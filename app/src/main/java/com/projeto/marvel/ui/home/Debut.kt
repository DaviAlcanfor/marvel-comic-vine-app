package com.projeto.marvel.ui.home

import com.projeto.marvel.data.remote.CharacterSummary
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// "birth" do personagem na Comic Vine é a data de estreia nas HQs, em inglês ("Oct 14, 1962").
private val DEBUT_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/** Estreou neste dia (qualquer ano)? Texto em outro formato conta como não. */
fun debutedOn(birth: String?, today: LocalDate): Boolean {
    val date = try {
        birth?.trim()?.let { LocalDate.parse(it, DEBUT_FORMAT) }
    } catch (_: DateTimeParseException) {
        null
    }
    return date != null && MonthDay.from(date) == MonthDay.from(today)
}

/** Sorteia entre os mais famosos: herói do dia desconhecido não empolga. */
private const val HERO_POOL = 60

/** Muda uma vez por dia e é o mesmo o dia todo; a Início e o widget mostram o mesmo. */
fun heroOfTheDay(popular: List<CharacterSummary>, today: LocalDate): CharacterSummary? =
    popular.take(HERO_POOL).takeIf { it.isNotEmpty() }?.let { it[today.toEpochDay().mod(it.size)] }
