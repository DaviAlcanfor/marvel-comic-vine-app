package com.projeto.marvel.data

import com.projeto.marvel.data.remote.CharacterDetailResponse
import com.projeto.marvel.data.remote.CharacterListResponse
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineImage
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
        override suspend fun getIssues(filter: String?, limit: Int, offset: Int, fieldList: String?): IssueResponse {
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

        override suspend fun search(query: String, resources: String, limit: Int, fieldList: String?): IssueResponse =
            error("não usado neste teste")
    }

    private var lastFilter: String? = "não chamado"

    private fun response(error: String?, results: List<Issue>?) =
        IssueResponse(error, limit = 20, offset = 0, totalResults = 0, results = results)

    @Test
    fun `erro de negocio com HTTP 200 vira failure`() = runTest {
        val repo = ComicVineRepository(service { response("Invalid API Key", null) })
        val result = repo.issueCover(1)
        assertTrue(result.isFailure)
        assertEquals("Comic Vine: Invalid API Key", result.exceptionOrNull()?.message)
    }

    @Test
    fun `issue sem resultado fica sem capa`() = runTest {
        val repo = ComicVineRepository(service { response("OK", null) })
        assertEquals(null, repo.issueCover(1).getOrThrow())
    }

    @Test
    fun `capa busca a issue pelo id`() = runTest {
        val cover = ComicVineImage(originalUrl = null, mediumUrl = "capa.jpg", smallUrl = null, thumbUrl = null)
        val issue = Issue(42, null, null, null, null, null, cover)
        val repo = ComicVineRepository(service { response("OK", listOf(issue)) })
        assertEquals("capa.jpg", repo.issueCover(42).getOrThrow())
        assertEquals("id:42", lastFilter)
    }

    private fun character(powers: List<String>?, appearances: Int?) = CharacterSummary(
        id = 1, name = "Thor", realName = null, deck = null, description = null, image = null,
        publisher = null, apiDetailUrl = null, issueAppearances = appearances, powers = powers?.map(::Power)
    )

    @Test
    fun `poderes repartem os pontos entre os atributos`() {
        val powers = listOf("Super Strength", "Unarmed Combat", "Flight")
        val stats = character(powers, appearances = 999).toFighter().stats
        // pesos (acertos + 1)²: ATQ 9, VEL 4, DEF 1, INT 1 → 100 pontos repartidos em 15 partes
        assertEquals(70, stats[Stat.ATTACK])
        assertEquals(36, stats[Stat.SPEED])
        assertEquals(16, stats[Stat.DEFENSE])
        assertEquals(16, stats[Stat.INTELLIGENCE])
        assertEquals(75, stats[Stat.FAME])
    }

    @Test
    fun `sem poderes fica equilibrado`() {
        val stats = character(powers = null, appearances = null).toFighter().stats
        assertEquals(Stat.entries.associateWith { if (it == Stat.FAME) 10 else 35 }, stats)
    }

    @Test
    fun `mais poderes cadastrados nao da mais pontos`() {
        fun total(powers: List<String>) =
            character(powers, appearances = 0).toFighter().stats.filterKeys { it != Stat.FAME }.values.sum().toDouble()
        val many = listOf("Super Strength", "Unarmed Combat", "Flight", "Genius", "Durability", "Speed", "Armor")
        assertEquals(total(listOf("Flight")), total(many), 3.0)
    }

    @Test
    fun `um golpe por tipo, na ordem de prioridade dos tipos`() {
        val powers = listOf("Super Strength", "Unarmed Combat", "Flight", "Healing", "Telepathy")
        val moves = character(powers, appearances = 0).toFighter().moves
        assertEquals(
            listOf(
                Move("Super Strength", MoveType.STRIKE),
                Move("Healing", MoveType.HEAL),
                Move("Flight", MoveType.DODGE)
            ),
            moves
        )
    }

    @Test
    fun `golpes tematicos tem prioridade sobre os genericos`() {
        val powers = listOf("Flight", "Super Strength", "Ice Breath", "Magic", "Water Control", "Siphon Lifeforce")
        val moves = character(powers, appearances = 0).toFighter().moves
        assertEquals(listOf(MoveType.FREEZE, MoveType.WATER, MoveType.MAGIC, MoveType.DRAIN), moves.map { it.type })
    }

    @Test
    fun `voz nao vira gelo`() {
        assertEquals(null, moveTypeOf("Voice-induced Manipulation"))
    }

    @Test
    fun `sem golpe de dano ganha um soco basico`() {
        val moves = character(listOf("Flight"), appearances = 0).toFighter().moves
        assertEquals(listOf(MoveType.STRIKE, MoveType.DODGE), moves.map { it.type })
    }
}
