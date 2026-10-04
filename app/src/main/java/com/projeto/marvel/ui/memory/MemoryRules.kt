package com.projeto.marvel.ui.memory

import kotlin.random.Random

// Regras puras do Jogo da Memória (testadas em MemoryRulesTest).

const val MEMORY_PAIRS = 8

/** Jogadas para ganhar o pacote Prata em vez do Básico (cada par de viradas = 1 jogada). */
const val MEMORY_SILVER_MOVES = MEMORY_PAIRS + 6

/**
 * Mesa: [faces] = o personagem de cada carta (cada um aparece 2 vezes); [open] = viradas agora
 * (no máximo 2); [matched] = posições já casadas; [moves] = pares de viradas feitos.
 */
data class MemoryGame(
    val faces: List<Int>,
    val open: List<Int> = emptyList(),
    val matched: Set<Int> = emptySet(),
    val moves: Int = 0
) {
    val done get() = matched.size == faces.size

    /** Duas abertas que não casaram: esperam um instante viradas e depois desviram. */
    val mismatch get() = open.size == 2 && faces[open[0]] != faces[open[1]]

    /** Vira a carta [index]. Com um par errado ainda aberto, ele desvira e esta abre sozinha. */
    fun flip(index: Int): MemoryGame {
        if (index in matched || index in open || index !in faces.indices) return this
        val opening = if (open.size == 2) listOf(index) else open + index
        return when {
            opening.size < 2 -> copy(open = opening)
            faces[opening[0]] == faces[opening[1]] ->
                copy(open = emptyList(), matched = matched + opening, moves = moves + 1)
            else -> copy(open = opening, moves = moves + 1)
        }
    }

    fun hideMismatch() = if (mismatch) copy(open = emptyList()) else this
}

/** Embaralha [pairs] personagens de [ids], cada um 2 vezes. */
fun memoryDeck(ids: List<Int>, random: Random, pairs: Int = MEMORY_PAIRS): List<Int> =
    ids.distinct().shuffled(random).take(pairs).flatMap { listOf(it, it) }.shuffled(random)
