package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReleasesTest {

    @Test
    fun `semana vai de segunda a domingo`() {
        val week = weekOf(LocalDate.of(2026, 10, 4)) // domingo
        assertEquals(LocalDate.of(2026, 9, 28), week.start)
        assertEquals(LocalDate.of(2026, 10, 4), week.endInclusive)
        assertEquals(LocalDate.of(2026, 10, 5), weekOf(LocalDate.of(2026, 10, 5)).start)
    }
}
