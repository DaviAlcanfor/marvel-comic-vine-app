package com.projeto.marvel.ui.guess

import kotlin.random.Random

// Regras puras do "Quem é esse herói?" (testadas em GuessRulesTest).

/** Níveis da foto pixelada: largura em pixels. Depois do último, a foto nítida. */
@Suppress("MagicNumber") // a tabela de níveis é a própria regra
val PIXEL_SIZES = listOf(8, 16, 32)
val CLEAR_LEVEL = PIXEL_SIZES.size

const val GUESS_OPTIONS = 4
private const val POINTS_PER_LEVEL = 10

/** Acertar com a foto mais pixelada vale mais: 40, 30, 20 e 10 na nítida. */
fun guessPoints(level: Int) = (CLEAR_LEVEL + 1 - level.coerceIn(0, CLEAR_LEVEL)) * POINTS_PER_LEVEL

/**
 * Sorteia a resposta (diferente de [avoid], a da rodada anterior) e [options] opções embaralhadas
 * com ela no meio, sem repetir. Null se o pool não tem gente suficiente.
 */
fun <T> pickRound(pool: List<T>, random: Random, avoid: T? = null, options: Int = GUESS_OPTIONS): Pair<T, List<T>>? {
    val candidates = pool.distinct()
    if (candidates.size < options) return null
    val answer = candidates.filter { it != avoid }.random(random)
    val others = candidates.filter { it != answer }.shuffled(random).take(options - 1)
    return answer to (others + answer).shuffled(random)
}
