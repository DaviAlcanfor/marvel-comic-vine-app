package com.projeto.marvel.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkdownTest {

    @Test
    fun `tira os asteriscos e marca o negrito`() {
        val (plain, runs) = boldRuns("Cara, o **Spider-Man** é da **Marvel**!")
        assertEquals("Cara, o Spider-Man é da Marvel!", plain)
        assertEquals(listOf("Spider-Man", "Marvel"), runs.map { plain.substring(it) })
    }

    @Test
    fun `texto sem markdown passa igual`() {
        assertEquals("oi * tudo bem" to emptyList<IntRange>(), boldRuns("oi * tudo bem"))
    }
}
