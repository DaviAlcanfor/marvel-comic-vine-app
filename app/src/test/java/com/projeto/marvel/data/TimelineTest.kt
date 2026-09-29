package com.projeto.marvel.data

import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.IssueRef
import com.projeto.marvel.data.remote.TeamDebut
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {

    private fun issue(id: Int, date: String?) = Issue(id, "Issue $id", "$id", date, null, null, null)

    private val issues = listOf(issue(1, "1962-08-01"), issue(2, "1990-01-01"), issue(3, null), issue(4, "1973-06-01"))
        .associateBy { it.id }

    @Test
    fun `ordena por data e ignora edicao sem data`() {
        val teams = listOf(TeamDebut(10, "Avengers", 255, IssueRef(2)), TeamDebut(11, "Sem data", 300, IssueRef(3)))
        val events = buildTimeline(debutIssueId = 1, teams = teams, deathIssueIds = listOf(4), issues = issues)
        assertEquals(listOf(TimelineKind.DEBUT, TimelineKind.DEATH, TimelineKind.TEAM), events.map { it.kind })
        assertEquals("Avengers", events.last().title)
    }

    @Test
    fun `fica com os maiores times`() {
        val teams = listOf(TeamDebut(10, "Pequeno", 3, IssueRef(1)), TeamDebut(11, "Grande", 200, IssueRef(2)))
        val events = buildTimeline(null, teams, deathIssueIds = emptyList(), issues = issues, maxTeams = 1)
        assertEquals(listOf("Grande"), events.map { it.title })
    }
}
