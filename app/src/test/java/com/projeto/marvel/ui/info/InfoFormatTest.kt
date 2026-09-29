package com.projeto.marvel.ui.info

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
}
