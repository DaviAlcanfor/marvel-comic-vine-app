package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CatalogService
import com.projeto.marvel.data.remote.Issue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A semana das bancas que contém [today]: de segunda a domingo. */
fun weekOf(today: LocalDate): ClosedRange<LocalDate> {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return monday..monday.plusDays(DAYS_IN_WEEK - 1)
}

private const val DAYS_IN_WEEK = 7L
private const val MARVEL = "Marvel"
private const val ISSUE_FIELDS = "id,name,issue_number,cover_date,store_date,volume,image,api_detail_url"

/** Edições da Marvel que chegaram às bancas nesta semana (Comic Vine). */
class ReleasesRepository(private val service: CatalogService = ApiClient.catalog) {

    /**
     * As edições não dizem a editora: busca as da semana (todas as editoras) e depois a editora de
     * cada volume, numa segunda chamada, ficando só com as da Marvel.
     */
    suspend fun thisWeek(today: LocalDate = LocalDate.now()): Result<List<Issue>> = runCatching {
        val week = weekOf(today)
        val filter = "store_date:${week.start}|${week.endInclusive}"
        // A semana tem ~200 edições de todas as editoras; a API devolve no máximo 100 por vez.
        val issues = mutableListOf<Issue>()
        for (page in 0 until MAX_PAGES) {
            val batch = service.getIssues(filter, offset = page * PAGE, fieldList = ISSUE_FIELDS).results()
            issues += batch
            if (batch.size < PAGE) break
        }
        val volumes = issues.mapNotNull { it.volume?.id }.distinct()
        if (volumes.isEmpty()) return@runCatching emptyList()
        val marvel = volumes.chunked(PAGE).flatMap { chunk ->
            service.getVolumes("id:${chunk.joinToString("|")}").results()
        }.filter { it.publisher?.name?.contains(MARVEL, ignoreCase = true) == true }.map { it.id }.toSet()
        issues.filter { it.volume?.id in marvel }
    }

    private companion object {
        const val PAGE = 100
        const val MAX_PAGES = 5
    }
}
