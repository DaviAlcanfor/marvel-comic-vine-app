package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CatalogService
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.TeamDebut

enum class TimelineKind { DEBUT, TEAM, DEATH }

/** Um marco na carreira do personagem; [date] é a `cover_date` da edição ("1962-08-01"). */
data class TimelineEvent(
    val kind: TimelineKind,
    val title: String,
    val issue: String,
    val date: String,
    val imageUrl: String?
)

private const val MAX_TEAMS = 8

/** A API aceita até 100 ids por filtro: uma requisição de times e uma de edições. */
private const val MAX_IDS = 100

/**
 * Estreia, estreia dos times dele (os [maxTeams] maiores: a Comic Vine não tem data de entrada
 * no time nem arcos por personagem) e mortes, em ordem de data. Edição sem data sai.
 */
fun buildTimeline(
    debutIssueId: Int?,
    teams: List<TeamDebut>,
    deathIssueIds: List<Int>,
    issues: Map<Int, Issue>,
    maxTeams: Int = MAX_TEAMS
): List<TimelineEvent> {
    fun event(kind: TimelineKind, title: String?, issue: Issue?): TimelineEvent? {
        val date = issue?.coverDate ?: return null
        return TimelineEvent(kind, title ?: issue.title, issue.title, date, issue.image?.smallUrl)
    }
    val debut = event(TimelineKind.DEBUT, null, debutIssueId?.let(issues::get))
    val teamEvents = teams.sortedByDescending { it.members ?: 0 }
        .mapNotNull { team -> event(TimelineKind.TEAM, team.name, team.firstIssue?.id?.let(issues::get)) }
        .take(maxTeams)
    val deaths = deathIssueIds.mapNotNull { event(TimelineKind.DEATH, null, issues[it]) }
    return (listOfNotNull(debut) + teamEvents + deaths).sortedBy { it.date }
}

/** Linha do tempo do Detalhe, só com dado real da Comic Vine (2 requisições no máximo). */
class TimelineRepository(
    private val catalog: CatalogService = ApiClient.catalog,
    private val comics: ComicVineService = ApiClient.comicVine
) {

    suspend fun timeline(character: CharacterSummary): Result<List<TimelineEvent>> = runCatching {
        val teamIds = character.teams.orEmpty().mapNotNull { it.id }.take(MAX_IDS)
        val teams = if (teamIds.isEmpty()) {
            emptyList()
        } else {
            catalog.getTeamDebuts("id:${teamIds.joinToString("|")}", fieldList = TEAM_FIELDS).results()
                .sortedByDescending { it.members ?: 0 }
                .take(MAX_TEAMS)
        }
        val deaths = character.issuesDiedIn.orEmpty().mapNotNull { it.id }
        val issueIds = (listOfNotNull(character.firstIssue?.id) + teams.mapNotNull { it.firstIssue?.id } + deaths)
            .distinct()
            .take(MAX_IDS)
        if (issueIds.isEmpty()) return@runCatching emptyList()
        val filter = "id:${issueIds.joinToString("|")}"
        val response = comics.getIssues(filter = filter, limit = MAX_IDS, fieldList = ISSUE_FIELDS)
        check(response.error == null || response.error == "OK") { "Comic Vine: ${response.error}" }
        val issues = response.results.orEmpty().associateBy { it.id }
        buildTimeline(character.firstIssue?.id, teams, deaths, issues)
    }

    private companion object {
        const val TEAM_FIELDS = "id,name,count_of_team_members,first_appeared_in_issue"
        const val ISSUE_FIELDS = "id,name,issue_number,cover_date,volume,image"
    }
}
