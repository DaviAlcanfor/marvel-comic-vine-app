package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class StickerRulesTest {

    @Test
    fun `raridade sai das aparicoes`() {
        assertEquals(Rarity.COMMON, rarity(null))
        assertEquals(Rarity.COMMON, rarity(499))
        assertEquals(Rarity.RARE, rarity(500))
        assertEquals(Rarity.LEGENDARY, rarity(2_000))
    }

    @Test
    fun `pacote tem o tamanho certo e so gente do pool`() {
        val pool = (1..20).toList()
        val pack = openPack(pool, { Rarity.COMMON }, Random(1))
        assertEquals(PACK_SIZE, pack.size)
        assertTrue(pack.all { it in pool })
    }

    @Test
    fun `lendaria sai bem menos que comum`() {
        // 1 = lendária, 2 = comum: pesos 10 × 60 → ~14% de lendárias.
        val rarityOf = { id: Int -> if (id == 1) Rarity.LEGENDARY else Rarity.COMMON }
        val picks = (0 until 500).flatMap { openPack(listOf(1, 2), rarityOf, Random(it)) }
        val legendary = picks.count { it == 1 }.toDouble() / picks.size
        assertTrue("lendárias: $legendary", legendary in 0.08..0.2)
    }

    @Test
    fun `ouro garante lendaria na primeira e da mais sorte que o basico`() {
        val rarityOf = { id: Int -> if (id == 1) Rarity.LEGENDARY else Rarity.COMMON }
        val gold = (0 until 300).map { openPack(listOf(1, 2), rarityOf, Random(it), PackType.GOLD) }
        assertTrue(gold.all { it.first() == 1 })
        val basic = (0 until 300).flatMap { openPack(listOf(1, 2), rarityOf, Random(it), PackType.BASIC) }
        val goldRest = gold.flatMap { it.drop(1) }
        val goldRate = goldRest.count { it == 1 }.toDouble() / goldRest.size
        assertTrue(goldRate > basic.count { it == 1 }.toDouble() / basic.size)
    }

    @Test
    fun `garantia sem ninguem daquela raridade cai para o que existe`() {
        val pack = openPack(listOf(1, 2, 3), { Rarity.COMMON }, Random(0), PackType.GOLD)
        assertEquals(PACK_SIZE, pack.size)
    }

    @Test
    fun `pool vazio da pacote vazio`() {
        assertTrue(openPack(emptyList<Int>(), { Rarity.COMMON }, Random(0)).isEmpty())
    }

    @Test
    fun `dois pacotes gratis por dia`() {
        val today = LocalDate.of(2026, 9, 29)
        assertEquals(2, freePacksLeft(null, 0, today))
        assertEquals(1, freePacksLeft(today, 1, today))
        assertEquals(0, freePacksLeft(today, 2, today))
        assertEquals(2, freePacksLeft(today.minusDays(1), 2, today))
    }

    @Test
    fun `troca tira de quem tem mais e nunca a ultima copia`() {
        val counts = mapOf(1 to 4, 2 to 3, 3 to 1)
        assertEquals(5, tradableExtras(counts))
        assertEquals(mapOf(1 to 3, 2 to 2), tradePicks(counts))
        assertEquals(null, tradePicks(mapOf(1 to 5, 2 to 1)))
    }
}
