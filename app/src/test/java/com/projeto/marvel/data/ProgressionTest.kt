package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private val fighter = Fighter(
        id = 1,
        name = "Teste",
        imageUrl = null,
        stats = mapOf(
            Stat.ATTACK to 50,
            Stat.DEFENSE to 98,
            Stat.SPEED to 10,
            Stat.INTELLIGENCE to 20,
            Stat.FAME to 60
        ),
        moves = emptyList()
    )

    @Test
    fun `nivel sai das figurinhas`() {
        assertEquals(0, levelFor(0))
        assertEquals(1, levelFor(1))
        assertEquals(3, levelFor(3))
        assertEquals(MAX_LEVEL, levelFor(50))
    }

    @Test
    fun `sem distribuir os pontos entram divididos por igual sem passar de 99`() {
        val up = fighter.leveled(4) // 24 pontos: 6 em cada
        assertEquals(4, up.level)
        assertEquals(56, up.stats[Stat.ATTACK])
        assertEquals(99, up.stats[Stat.DEFENSE])
        assertEquals(60, up.stats[Stat.FAME])
    }

    @Test
    fun `pontos distribuidos vao onde voce mandou e o resto divide`() {
        // Nv 3 = 16 pontos: 12 em VEL, os 4 livres viram 1 em cada.
        val up = fighter.upgraded(3, mapOf(Stat.SPEED to 12))
        assertEquals(10 + 12 + 1, up.stats[Stat.SPEED])
        assertEquals(50 + 1, up.stats[Stat.ATTACK])
    }

    @Test
    fun `distribuicao maior que os pontos do nivel e ignorada`() {
        assertEquals(fighter.leveled(2).stats, fighter.upgraded(2, mapOf(Stat.ATTACK to 30)).stats)
    }

    @Test
    fun `dourada soma 5 em tudo e so evolui com 5 figurinhas`() {
        val gold = fighter.upgraded(1, golden = true)
        assertTrue(gold.golden)
        assertEquals(55, gold.stats[Stat.ATTACK])
        assertTrue(canEvolve(5, golden = false))
        assertFalse(canEvolve(4, golden = false))
        assertFalse(canEvolve(9, golden = true))
    }

    @Test
    fun `cpu vem dois niveis abaixo e o chefe no seu nivel`() {
        val team = listOf(fighter.leveled(6), fighter.leveled(4))
        assertEquals(3, rivalLevel(team, boss = false))
        assertEquals(5, rivalLevel(team, boss = true))
        assertEquals(1, rivalLevel(listOf(fighter), boss = false))
    }

    @Test
    fun `nivel 1 nao muda nada`() {
        assertEquals(fighter.stats, fighter.leveled(1).stats)
    }
}
