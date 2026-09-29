package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingStoreTest {

    private fun comic(id: Int, rating: Int = 3) = ReadComic(id, "HQ $id", coverUrl = null, rating = rating)

    @Test
    fun `hq lida vai para o topo`() {
        assertEquals(listOf(comic(2), comic(1)), listOf(comic(1)).withRead(comic(2)))
    }

    @Test
    fun `marcar de novo troca a nota sem duplicar`() {
        assertEquals(listOf(comic(1, rating = 5), comic(2)), listOf(comic(2), comic(1)).withRead(comic(1, rating = 5)))
    }
}
