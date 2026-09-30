package com.projeto.marvel.ui.info

import com.projeto.marvel.data.remote.Movie
import org.junit.Assert.assertEquals
import org.junit.Test

class InfoFormatTest {

    @Test
    fun `data da api vira dia mes ano`() {
        assertEquals("28/12/1922", formatDate("1922-12-28 00:00:00"))
        assertEquals("Oct 14, 1962", formatDate("Oct 14, 1962"))
        assertEquals(null, formatDate(null))
    }

    @Test
    fun `valor com virgulas e tab vira milhoes`() {
        assertEquals("US$ 890,9 mi", formatMoney("890,871,626\t"))
        assertEquals("US$ 403,7 mi", formatMoney("403706375"))
        assertEquals(null, formatMoney("0"))
        assertEquals(null, formatMoney(null))
    }

    @Test
    fun `duracao so ganha min quando vem numero puro`() {
        fun label(runtime: String?) = Movie(1, "X", null, null, null, runtime = runtime).runtimeLabel
        assertEquals("121 min", label("121"))
        assertEquals("140 min", label("140 min"))
        assertEquals("2 hr 11 min", label("2 hr 11 min"))
        assertEquals(null, label("0"))
        assertEquals(null, label(null))
    }
}
