package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.Team

/**
 * Única porta de entrada para dados da Comic Vine. A ViewModel fala com ela, nunca com o Retrofit.
 */
class ComicVineRepository(
    private val service: ComicVineService = ApiClient.comicVine
) {

    /** [query] nula/vazia lista as issues mais recentes. */
    suspend fun searchIssues(query: String? = null, offset: Int = 0): Result<List<Issue>> =
        runCatching {
            val response = service.getIssues(
                filter = query?.takeIf { it.isNotBlank() }?.let { "name:$it" },
                offset = offset
            )
            // A Comic Vine responde 200 mesmo em erro de negócio; o status vem no corpo.
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }

    /**
     * [query] nula/vazia lista os personagens mais recentes.
     *
     * TODO: a Comic Vine não tem filtro nativo de `publisher` neste endpoint (ele existe
     * para outros recursos, mas exigiria descobrir o id numérico da editora Marvel e não
     * está documentado de forma estável). Por isso a lista pode trazer personagens de
     * outras editoras. Quando esse filtro for definido, aplicar aqui.
     */
    suspend fun searchCharacters(query: String? = null, offset: Int = 0): Result<List<CharacterSummary>> =
        runCatching {
            val response = service.getCharacters(
                filter = query?.takeIf { it.isNotBlank() }?.let { "name:$it" },
                offset = offset
            )
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }

    suspend fun getCharacterDetail(apiDetailUrl: String): Result<CharacterSummary> =
        runCatching {
            val response = service.getCharacterDetail(apiDetailUrl)
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.result ?: error("Personagem não encontrado")
        }

    /** TODO: mesma limitação de filtro por editora do [searchCharacters] se aplica aqui. */
    suspend fun listTeams(offset: Int = 0): Result<List<Team>> =
        runCatching {
            val response = service.getTeams(offset = offset)
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }
}
