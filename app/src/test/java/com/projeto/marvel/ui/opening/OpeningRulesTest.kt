package com.projeto.marvel.ui.opening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class OpeningRulesTest {

    @Test
    fun `cada heroi tem a sua onomatopeia`() {
        assertEquals("SNIKT!", heroSounds("Wolverine").first())
        assertEquals("THWIP!", heroSounds("Spider-Man").first())
        assertEquals("BAMF!", heroSounds("Nightcrawler").first())
        assertEquals("SHAZZZ!", heroSounds("Doctor Strange").first())
    }

    @Test
    fun `sem heroi ou heroi desconhecido fica no classico`() {
        assertEquals(listOf("POW!", "BAM!", "ZAP!"), heroSounds(null))
        assertEquals(listOf("POW!", "BAM!", "ZAP!"), heroSounds("Squirrel Girl"))
    }

    @Test
    fun `automatico sorteia um roteiro de verdade e os outros ficam como estao`() {
        repeat(20) { assertNotEquals(OpeningScript.AUTO, OpeningScript.AUTO.resolve(Random(it))) }
        assertEquals(OpeningScript.COVER, OpeningScript.COVER.resolve())
    }
}
