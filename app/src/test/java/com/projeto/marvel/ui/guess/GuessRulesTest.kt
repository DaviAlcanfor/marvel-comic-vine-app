package com.projeto.marvel.ui.guess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GuessRulesTest {

    @Test
    fun `mais pixelado vale mais pontos`() {
        assertEquals(listOf(40, 30, 20, 10), (0..CLEAR_LEVEL).map(::guessPoints))
    }

    @Test
    fun `rodada tem a resposta entre opcoes distintas`() {
        repeat(50) { seed ->
            val (answer, options) = requireNotNull(pickRound((1..10).toList(), Random(seed)))
            assertEquals(GUESS_OPTIONS, options.size)
            assertEquals(options.size, options.toSet().size)
            assertTrue(answer in options)
        }
    }

    @Test
    fun `nao repete a resposta da rodada anterior`() {
        repeat(50) { seed ->
            assertNotEquals(3, pickRound(listOf(1, 2, 3, 4, 5), Random(seed), avoid = 3)?.first)
        }
    }

    @Test
    fun `pool pequeno nao gera rodada`() {
        assertNull(pickRound(listOf(1, 2, 2, 3), Random(0)))
    }
}
