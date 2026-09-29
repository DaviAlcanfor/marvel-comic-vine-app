package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.ApiItem
import com.projeto.marvel.data.remote.ApiList
import com.projeto.marvel.data.remote.CatalogService
import com.projeto.marvel.data.remote.Location
import com.projeto.marvel.data.remote.Movie
import com.projeto.marvel.data.remote.Person
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Catálogo do Descobrir: criadores e filmes. Separado do [ComicVineRepository] (personagens,
 * times, batalha) para cada um continuar pequeno; os dois falam com a mesma Comic Vine.
 */
class CatalogRepository(
    private val service: CatalogService = ApiClient.catalog
) {

    /** Sem busca: criadores lendários da Marvel ([FEATURED_CREATORS]). Com busca: por nome. */
    suspend fun creators(query: String = ""): Result<List<Person>> =
        runCatching {
            val filter = if (query.isBlank()) "id:${FEATURED_CREATORS.joinToString("|")}" else "name:$query"
            service.getPeople(filter, fieldList = PERSON_LIST_FIELDS).results()
        }

    /** Cards (com foto) de criadores por id: a referência no personagem só traz o nome. */
    suspend fun creatorsByIds(ids: List<Int>): Result<List<Person>> =
        runCatching {
            if (ids.isEmpty()) return@runCatching emptyList()
            service.getPeople("id:${ids.distinct().joinToString("|")}", fieldList = PERSON_LIST_FIELDS).results()
        }

    suspend fun creator(apiDetailUrl: String): Result<Person> =
        runCatching { service.getPerson(apiDetailUrl, PERSON_DETAIL_FIELDS).result() }

    /**
     * Filmes da Marvel: a lista `movies/` mistura editoras e não filtra por estúdio, então o
     * catálogo junta os filmes dos principais times da Marvel. Guardado em memória.
     */
    suspend fun marvelMovies(): Result<List<Movie>> =
        runCatching {
            moviesCache?.let { return@runCatching it }
            val ids = coroutineScope {
                MOVIE_TEAMS.map { url -> async { service.getTeamMovies(url).result().movies.orEmpty() } }
                    .awaitAll()
                    .flatten()
                    .map { it.id }
            }
            moviesByIds(ids).getOrThrow().also { moviesCache = it }
        }

    /** Cards (pôster, nota) de filmes por id, em ordem alfabética. */
    suspend fun moviesByIds(ids: List<Int>): Result<List<Movie>> =
        runCatching {
            coroutineScope {
                ids.distinct().chunked(PAGE_SIZE).map { chunk ->
                    async {
                        service.getMovies("id:${chunk.joinToString("|")}", fieldList = MOVIE_LIST_FIELDS).results()
                    }
                }.awaitAll().flatten().sortedBy { it.name }
            }
        }

    suspend fun movie(apiDetailUrl: String): Result<Movie> =
        runCatching { service.getMovie(apiDetailUrl, MOVIE_DETAIL_FIELDS).result() }

    /** Lugares famosos da Marvel ([PLACES]), mais aparições primeiro. */
    suspend fun locations(): Result<List<Location>> =
        runCatching {
            val filter = "id:${PLACES.keys.joinToString("|")}"
            service.getLocations(filter, fieldList = LOCATION_LIST_FIELDS).results()
                .sortedByDescending { it.appearances ?: 0 }
        }

    suspend fun location(apiDetailUrl: String): Result<Location> =
        runCatching { service.getLocation(apiDetailUrl, LOCATION_DETAIL_FIELDS).result() }

    /**
     * Busca para o mapa (Google Maps) de um lugar que existe no mundo real; null = fictício. A
     * Comic Vine não tem coordenadas nem diz o que é real: a marcação é nossa, em [PLACES].
     */
    fun mapQuery(locationId: Int): String? = PLACES[locationId]

    companion object {
        private const val PAGE_SIZE = 100
        private const val PERSON_LIST_FIELDS = "id,name,deck,image,api_detail_url"
        private const val PERSON_DETAIL_FIELDS =
            "$PERSON_LIST_FIELDS,description,birth,death,country,hometown,created_characters"

        // Sem release_date: na Comic Vine ele é a data de cadastro (Homem-Aranha de 2002 aparece como 2021).
        private const val MOVIE_LIST_FIELDS = "id,name,deck,image,api_detail_url,rating,runtime"
        private const val MOVIE_DETAIL_FIELDS = "$MOVIE_LIST_FIELDS,box_office_revenue,budget,description,characters"

        private const val LOCATION_LIST_FIELDS = "id,name,deck,image,api_detail_url,count_of_issue_appearances"
        private const val LOCATION_DETAIL_FIELDS =
            "$LOCATION_LIST_FIELDS,start_year,description,first_appeared_in_issue"

        /** Lugares em destaque → busca no mapa (null = lugar fictício). */
        private val PLACES: Map<Int, String?> = mapOf(
            41183 to "New York City, NY",
            42295 to "Hell's Kitchen, Manhattan, NY",
            57856 to "Queens, NY",
            58107 to "Harlem, Manhattan, NY",
            55704 to "San Francisco, CA",
            41027 to null, // Wakanda
            40990 to null, // Asgard
            41031 to null, // Latvéria
            40967 to null, // Genosha
            42665 to null, // Madripoor
            21766 to null, // Terra Selvagem
            49201 to null, // Lugar Nenhum (Knowhere)
            44155 to null, // Attilan
            41454 to null, // Edifício Baxter
            41036 to null, // Mansão dos Vingadores
            55966 to null, // Sanctum Sanctorum
            55675 to null, // Clarim Diário
            41138 to null // Instituto Xavier
        )

        // ponytail: cache só em memória, como o de populares no ComicVineRepository.
        @Volatile private var moviesCache: List<Movie>? = null

        /** Stan Lee, Jack Kirby, Steve Ditko, John Romita, Chris Claremont, Frank Miller, John Byrne… */
        private val FEATURED_CREATORS = listOf(
            40467, 5614, 4026, 7499, 40468, 7082, 1770, 7922, 42067, 3792,
            40435, 45065, 41468, 3380, 41457, 4447, 6609, 4068, 13709
        )

        /** Vingadores, X-Men, Quarteto Fantástico e Guardiões da Galáxia. */
        private val MOVIE_TEAMS = listOf(
            "https://comicvine.gamespot.com/api/team/4060-3806/",
            "https://comicvine.gamespot.com/api/team/4060-3173/",
            "https://comicvine.gamespot.com/api/team/4060-3804/",
            "https://comicvine.gamespot.com/api/team/4060-25956/"
        )
    }
}

// A Comic Vine responde 200 mesmo em erro de negócio; o status vem no corpo.
internal fun <T> ApiList<T>.results(): List<T> {
    check(error == null || error == "OK") { "Comic Vine: $error" }
    return results.orEmpty()
}

private fun <T> ApiItem<T>.result(): T {
    check(error == null || error == "OK") { "Comic Vine: $error" }
    return result ?: error("Não encontrado")
}
