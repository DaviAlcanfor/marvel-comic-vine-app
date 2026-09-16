package com.projeto.marvel.data

import com.projeto.marvel.data.remote.CharacterDetailResponse
import com.projeto.marvel.data.remote.CharacterListResponse
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.IssueResponse
import com.projeto.marvel.data.remote.TeamListResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicVineRepositoryTest {

    private fun service(response: () -> IssueResponse) = object : ComicVineService {
        override suspend fun getIssues(filter: String?, limit: Int, offset: Int): IssueResponse {
            lastFilter = filter
            return response()
        }

        override suspend fun getCharacters(filter: String?, limit: Int, offset: Int): CharacterListResponse =
            error("não usado neste teste")

        override suspend fun getCharacterDetail(url: String): CharacterDetailResponse =
            error("não usado neste teste")

        override suspend fun getTeams(filter: String?, limit: Int, offset: Int): TeamListResponse =
            error("não usado neste teste")
    }

    private var lastFilter: String? = "não chamado"

    private fun response(error: String?, results: List<Issue>?) =
        IssueResponse(error, limit = 20, offset = 0, totalResults = 0, results = results)

    @Test
    fun `erro de negocio com HTTP 200 vira failure`() = runTest {
        val repo = ComicVineRepository(service { response("Invalid API Key", null) })
        val result = repo.searchIssues("batman")
        assertTrue(result.isFailure)
        assertEquals("Comic Vine: Invalid API Key", result.exceptionOrNull()?.message)
    }

    @Test
    fun `results nulo vira lista vazia`() = runTest {
        val repo = ComicVineRepository(service { response("OK", null) })
        assertEquals(emptyList<Issue>(), repo.searchIssues("batman").getOrNull())
    }

    @Test
    fun `query em branco nao vira filtro`() = runTest {
        val repo = ComicVineRepository(service { response("OK", emptyList()) })
        repo.searchIssues("   ")
        assertEquals(null, lastFilter)
        repo.searchIssues("batman")
        assertEquals("name:batman", lastFilter)
    }
}
