package com.projeto.marvel.ui.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoryRulesTest {

    @Test
    fun `baralho tem cada personagem duas vezes`() {
        val deck = memoryDeck((1..20).toList(), Random(1))
        assertEquals(MEMORY_PAIRS * 2, deck.size)
        assertTrue(deck.groupBy { it }.values.all { it.size == 2 })
    }

    @Test
    fun `par certo casa e par errado desvira na proxima virada`() {
        var game = MemoryGame(faces = listOf(7, 8, 7, 8))
        game = game.flip(0).flip(2)
        assertEquals(setOf(0, 2), game.matched)
        assertEquals(1, game.moves)
        game = game.flip(1).flip(0) // 0 já casou: ignora
        assertEquals(listOf(1), game.open)
        game = game.flip(3)
        assertTrue(game.done)
        assertEquals(2, game.moves)
    }

    @Test
    fun `erro fica aberto ate a proxima virada ou o desvirar`() {
        var game = MemoryGame(faces = listOf(1, 2, 1, 2)).flip(0).flip(1)
        assertTrue(game.mismatch)
        assertEquals(listOf(2), game.flip(2).open)
        assertEquals(emptyList<Int>(), game.hideMismatch().open)
    }
}
