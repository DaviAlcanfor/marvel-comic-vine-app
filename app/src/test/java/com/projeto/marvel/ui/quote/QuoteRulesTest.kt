package com.projeto.marvel.ui.quote

import com.projeto.marvel.data.isNewRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteRulesTest {

    @Test
    fun `esconde nome e nome real no resumo`() {
        assertEquals(
            "??? ??? is ???-??? from Queens.",
            maskNames("Peter Parker is Spider-Man from Queens.", "Spider-Man", "Peter Parker")
        )
        assertEquals("??? leads the X-Men.", maskNames("cyclops leads the X-Men.", "Cyclops", null))
    }

    @Test
    fun `pacote a cada cinco seguidos`() {
        assertFalse(earnsPack(0))
        assertFalse(earnsPack(4))
        assertTrue(earnsPack(5))
        assertTrue(earnsPack(10))
    }

    @Test
    fun `recorde respeita se menos ou mais e melhor`() {
        assertTrue(isNewRecord(null, 30, lowerIsBetter = true))
        assertTrue(isNewRecord(20, 15, lowerIsBetter = true))
        assertFalse(isNewRecord(20, 25, lowerIsBetter = true))
        assertTrue(isNewRecord(3, 4, lowerIsBetter = false))
    }
}
