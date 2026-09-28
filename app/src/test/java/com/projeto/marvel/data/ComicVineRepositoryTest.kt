package com.projeto.marvel.data

import com.projeto.marvel.data.remote.CharacterDetailResponse
import com.projeto.marvel.data.remote.CharacterListResponse
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.IssueResponse
import com.projeto.marvel.data.remote.Power
import com.projeto.marvel.data.remote.TeamDetailResponse
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

        override suspend fun getCharacters(
            filter: String?,
            limit: Int,
            offset: Int,
            fieldList: String?
        ): CharacterListResponse =
            error("não usado neste teste")

        override suspend fun getCharacterDetail(url: String, fieldList: String?): CharacterDetailResponse =
            error("não usado neste teste")

        override suspend fun getTeamDetail(url: String, fieldList: String?): TeamDetailResponse =
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

    private fun character(powers: List<String>?, appearances: Int?) = CharacterSummary(
        id = 1, name = "Thor", realName = null, deck = null, description = null, image = null,
        publisher = null, apiDetailUrl = null, issueAppearances = appearances, powers = powers?.map(::Power)
    )

    @Test
    fun `poderes somam pontos no atributo correspondente`() {
        val powers = listOf("Super Strength", "Unarmed Combat", "Flight")
        val stats = character(powers, appearances = 999).toFighter().stats
        assertEquals(50, stats[Stat.ATTACK])
        assertEquals(30, stats[Stat.SPEED])
        assertEquals(10, stats[Stat.DEFENSE])
        assertEquals(75, stats[Stat.FAME])
    }

    @Test
    fun `sem poderes nem aparicoes fica tudo no minimo`() {
        val stats = character(powers = null, appearances = null).toFighter().stats
        assertEquals(Stat.entries.associateWith { 10 }, stats)
    }

    @Test
    fun `um golpe por tipo, na ordem dos poderes`() {
        val powers = listOf("Super Strength", "Unarmed Combat", "Flight", "Healing", "Telepathy")
        val moves = character(powers, appearances = 0).toFighter().moves
        assertEquals(
            listOf(
                Move("Super Strength", MoveType.STRIKE),
                Move("Flight", MoveType.DODGE),
                Move("Healing", MoveType.HEAL)
            ),
            moves
        )
    }

    @Test
    fun `sem golpe de dano ganha um soco basico`() {
        val moves = character(listOf("Flight"), appearances = 0).toFighter().moves
        assertEquals(listOf(MoveType.STRIKE, MoveType.DODGE), moves.map { it.type })
    }
}
