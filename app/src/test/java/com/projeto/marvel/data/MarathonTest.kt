package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarathonTest {

    @Test
    fun `comeca pelo Primeiro Vingador e soma tudo`() {
        val progress = marathonProgress(emptySet())
        assertEquals(0, progress.watched)
        assertEquals(MCU_MARATHON.size, progress.total)
        assertEquals(MCU_MARATHON.sumOf { it.minutes }, progress.minutesLeft)
        assertEquals(927, progress.next?.movieId)
    }

    @Test
    fun `proximo e o primeiro nao visto, mesmo vendo fora de ordem`() {
        val progress = marathonProgress(setOf(927, 17))
        assertEquals(2, progress.watched)
        assertEquals(2328, progress.next?.movieId)
        assertEquals(MCU_MARATHON.sumOf { it.minutes } - 124 - 126, progress.minutesLeft)
    }

    @Test
    fun `tudo visto acaba a maratona`() {
        val progress = marathonProgress(MCU_MARATHON.map { it.movieId }.toSet())
        assertNull(progress.next)
        assertEquals(0, progress.minutesLeft)
    }

    @Test
    fun `sem filme repetido na tabela`() {
        assertEquals(MCU_MARATHON.size, MCU_MARATHON.map { it.movieId }.toSet().size)
    }
}
