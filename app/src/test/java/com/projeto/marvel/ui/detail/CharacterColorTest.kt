package com.projeto.marvel.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterColorTest {

    private val background = 0xFF121223.toInt()

    @Test
    fun `contraste de preto e branco e 21`() {
        assertEquals(21.0, contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun `cor escura e clareada ate ficar legivel`() {
        val venomBlack = 0xFF1A1A1A.toInt()
        assertTrue(contrast(readableOn(background, venomBlack), background) >= 4.5)
    }

    @Test
    fun `cor ja legivel nao muda`() {
        val gold = 0xFFE6B800.toInt()
        assertEquals(gold, readableOn(background, gold))
    }
}
