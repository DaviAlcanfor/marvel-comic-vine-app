package com.projeto.marvel.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebutTest {

    private val october14 = LocalDate.of(2026, 10, 14)

    @Test
    fun `mesmo dia e mes de qualquer ano conta`() {
        assertTrue(debutedOn("Oct 14, 1962", october14))
    }

    @Test
    fun `outro dia ou formato desconhecido nao conta`() {
        assertFalse(debutedOn("Oct 15, 1962", october14))
        assertFalse(debutedOn("1962", october14))
        assertFalse(debutedOn(null, october14))
    }
}
