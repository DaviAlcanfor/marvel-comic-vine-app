package com.projeto.marvel.data

import com.projeto.marvel.data.remote.CharacterDetailResponse
import com.projeto.marvel.data.remote.CharacterListResponse
import com.projeto.marvel.data.remote.CharacterRef
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.IssueResponse
import com.projeto.marvel.data.remote.TeamDetail
import com.projeto.marvel.data.remote.TeamDetailResponse
import com.projeto.marvel.data.remote.TeamListResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GauntletTest {

    /** Membro `n` tem `n` aparições; a url é "c<n>". */
    private val members = (1..8).map { n ->
        CharacterSummary(
            id = n, name = "Membro $n", realName = null, deck = null, description = null, image = null,
            publisher = null, apiDetailUrl = "c$n", issueAppearances = n
        )
    }

    private val service = object : ComicVineService {
        override suspend fun getTeamDetail(url: String, fieldList: String?) = TeamDetailResponse(
            "OK",
            TeamDetail(
                id = 1, name = "Time", deck = null, description = null, image = null, publisher = null,
                memberCount = null,
                members = members.map { CharacterRef(it.id, it.name, it.apiDetailUrl) },
                enemies = null
            )
        )

        override suspend fun getCharacters(filter: String?, limit: Int, offset: Int, fieldList: String?) =
            CharacterListResponse("OK", limit, offset, members.size, members.shuffled())

        override suspend fun getCharacterDetail(url: String, fieldList: String?) =
            CharacterDetailResponse("OK", members.first { it.apiDetailUrl == url })

        override suspend fun getIssues(filter: String?, limit: Int, offset: Int, fieldList: String?): IssueResponse =
            error("não usado neste teste")

        override suspend fun getTeams(filter: String?, limit: Int, offset: Int): TeamListResponse =
            error("não usado neste teste")

        override suspend fun search(query: String, resources: String, limit: Int, fieldList: String?): IssueResponse =
            error("não usado neste teste")
    }

    @Test
    fun `enfrenta os mais famosos, do menor ao maior, e o ultimo e o chefe`() = runTest {
        val repository = ComicVineRepository(service)
        val (players, opponents) = repository.getFighters(listOf("c7"), teamUrl = "time").getOrThrow()

        assertEquals("Membro 7", players.single().name)
        // O jogador (7) sai da lista: os 5 mais famosos restantes são 8, 6, 5, 4, 3.
        assertEquals(listOf("Membro 3", "Membro 4", "Membro 5", "Membro 6", "Membro 8"), opponents.map { it.name })
        assertEquals(listOf(false, false, false, false, true), opponents.map { it.boss })
    }
}
