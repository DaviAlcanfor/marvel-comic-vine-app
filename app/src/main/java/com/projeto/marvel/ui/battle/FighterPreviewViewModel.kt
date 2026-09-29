package com.projeto.marvel.ui.battle

import androidx.lifecycle.ViewModel
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.toFighter

/**
 * Ficha de lutador na seleção da Batalha. Os atributos saem dos poderes, que só vêm no detalhe
 * do personagem — por isso a ficha abre ao tocar no card (uma requisição), e não na lista toda.
 */
class FighterPreviewViewModel(
    private val repository: ComicVineRepository = ComicVineRepository()
) : ViewModel() {

    // Reabrir a ficha do mesmo personagem não gasta outra requisição.
    private val cache = mutableMapOf<String, Fighter>()

    suspend fun fighter(apiDetailUrl: String): Result<Fighter> {
        cache[apiDetailUrl]?.let { return Result.success(it) }
        return repository.getCharacterDetail(apiDetailUrl)
            .map { it.toFighter() }
            .onSuccess { cache[apiDetailUrl] = it }
    }
}
