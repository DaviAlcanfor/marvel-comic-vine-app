package com.projeto.marvel.ui.album

import com.projeto.marvel.data.Rarity
import com.projeto.marvel.data.remote.CharacterSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumFilterTest {

    private fun sticker(number: Int, name: String, rarity: Rarity, count: Int, golden: Boolean = false) = Sticker(
        CharacterSummary(number, name, null, null, null, null, null, null),
        rarity,
        count,
        number,
        golden
    )

    private val album = listOf(
        sticker(1, "Thor", Rarity.LEGENDARY, 3),
        sticker(2, "Wolverine", Rarity.LEGENDARY, 0),
        sticker(3, "Storm", Rarity.RARE, 5),
        sticker(4, "Beast", Rarity.COMMON, 1, golden = true),
        sticker(5, "Thing", Rarity.COMMON, 2)
    )

    private fun numbers(query: AlbumQuery) = album.filtered(query).map { it.number }

    @Test
    fun `atalhos de filtro`() {
        assertEquals(listOf(1, 2, 3, 4, 5), numbers(AlbumQuery()))
        assertEquals(listOf(1, 2), numbers(AlbumQuery(AlbumFilter.LEGENDARY)))
        assertEquals(listOf(2), numbers(AlbumQuery(AlbumFilter.MISSING)))
        assertEquals(listOf(1, 3, 5), numbers(AlbumQuery(AlbumFilter.DOUBLES)))
        assertEquals(listOf(1, 3), numbers(AlbumQuery(AlbumFilter.TRIPLES)))
        assertEquals(listOf(3), numbers(AlbumQuery(AlbumFilter.EVOLVE)))
        assertEquals(listOf(4), numbers(AlbumQuery(AlbumFilter.DIVINE)))
    }

    @Test
    fun `busca soma com o filtro e ignora caixa`() {
        assertEquals(listOf(1, 5), numbers(AlbumQuery(text = " th ")))
        assertEquals(listOf(1), numbers(AlbumQuery(AlbumFilter.TRIPLES, text = "TH")))
    }

    @Test
    fun `ordem por nome e por repetidas`() {
        assertEquals(listOf(4, 3, 5, 1, 2), numbers(AlbumQuery(sort = AlbumSort.NAME)))
        assertEquals(listOf(3, 1, 5, 4, 2), numbers(AlbumQuery(sort = AlbumSort.COPIES)))
    }
}
