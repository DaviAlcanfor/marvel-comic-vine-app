package com.projeto.marvel.ui.album

import com.projeto.marvel.data.Rarity
import com.projeto.marvel.data.canEvolve

private const val DOUBLE = 2
private const val TRIPLE = 3

/** Atalhos de filtro do álbum (um por vez); a busca por nome soma com qualquer um. */
enum class AlbumFilter { ALL, OWNED, MISSING, LEGENDARY, RARE, COMMON, DOUBLES, TRIPLES, EVOLVE, DIVINE }

enum class AlbumSort { NUMBER, NAME, COPIES }

data class AlbumQuery(
    val filter: AlbumFilter = AlbumFilter.ALL,
    val text: String = "",
    val sort: AlbumSort = AlbumSort.NUMBER
) {
    val active get() = filter != AlbumFilter.ALL || text.isNotBlank()
}

/** Figurinhas que passam no filtro e na busca, na ordem pedida. O número de cada uma não muda. */
fun List<Sticker>.filtered(query: AlbumQuery): List<Sticker> {
    val text = query.text.trim()
    val shown = filter {
        it.matches(query.filter) && (text.isEmpty() || it.character.name.contains(text, ignoreCase = true))
    }
    return when (query.sort) {
        AlbumSort.NUMBER -> shown.sortedBy { it.number }
        AlbumSort.NAME -> shown.sortedBy { it.character.name.lowercase() }
        AlbumSort.COPIES -> shown.sortedWith(compareByDescending<Sticker> { it.count }.thenBy { it.number })
    }
}

private fun Sticker.matches(filter: AlbumFilter) = when (filter) {
    AlbumFilter.ALL -> true
    AlbumFilter.OWNED -> count > 0
    AlbumFilter.MISSING -> count == 0
    AlbumFilter.LEGENDARY -> rarity == Rarity.LEGENDARY
    AlbumFilter.RARE -> rarity == Rarity.RARE
    AlbumFilter.COMMON -> rarity == Rarity.COMMON
    AlbumFilter.DOUBLES -> count >= DOUBLE
    AlbumFilter.TRIPLES -> count >= TRIPLE
    AlbumFilter.EVOLVE -> canEvolve(count, golden)
    AlbumFilter.DIVINE -> golden
}
