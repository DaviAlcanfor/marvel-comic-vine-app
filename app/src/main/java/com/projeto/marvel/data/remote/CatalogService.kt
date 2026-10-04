package com.projeto.marvel.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/** Endpoints do catálogo do Descobrir (criadores e filmes). api_key/format vêm do interceptor. */
interface CatalogService {

    /** [filter] no formato `name:stan lee` ou `id:1|2|3`. */
    @GET("people/")
    suspend fun getPeople(
        @Query("filter") filter: String,
        @Query("limit") limit: Int = 100,
        @Query("field_list") fieldList: String? = null
    ): ApiList<Person>

    /** [url] é o `api_detail_url` absoluto do criador. */
    @GET
    suspend fun getPerson(@Url url: String, @Query("field_list") fieldList: String? = null): ApiItem<Person>

    /** [filter] no formato `id:1|2|3`. */
    @GET("movies/")
    suspend fun getMovies(
        @Query("filter") filter: String,
        @Query("limit") limit: Int = 100,
        @Query("field_list") fieldList: String? = null
    ): ApiList<Movie>

    @GET
    suspend fun getMovie(@Url url: String, @Query("field_list") fieldList: String? = null): ApiItem<Movie>

    /** Lançamentos: [filter] no formato `store_date:2026-09-28|2026-10-04`. */
    @GET("issues/")
    suspend fun getIssues(
        @Query("filter") filter: String,
        @Query("offset") offset: Int = 0,
        @Query("sort") sort: String = "store_date:desc",
        @Query("limit") limit: Int = 100,
        @Query("field_list") fieldList: String? = null
    ): ApiList<Issue>

    /** [filter] no formato `id:1|2|3`; serve para saber a editora de cada volume. */
    @GET("volumes/")
    suspend fun getVolumes(
        @Query("filter") filter: String,
        @Query("limit") limit: Int = 100,
        @Query("field_list") fieldList: String = "id,publisher"
    ): ApiList<VolumePublisher>

    /** [filter] no formato `id:1|2|3`. */
    @GET("teams/")
    suspend fun getTeamDebuts(
        @Query("filter") filter: String,
        @Query("limit") limit: Int = 100,
        @Query("field_list") fieldList: String? = null
    ): ApiList<TeamDebut>

    /** [filter] no formato `id:1|2|3`. */
    @GET("locations/")
    suspend fun getLocations(
        @Query("filter") filter: String,
        @Query("limit") limit: Int = 100,
        @Query("field_list") fieldList: String? = null
    ): ApiList<Location>

    @GET
    suspend fun getLocation(@Url url: String, @Query("field_list") fieldList: String? = null): ApiItem<Location>

    /** Só os filmes de um time (lista de referências), para montar o catálogo da Marvel. */
    @GET
    suspend fun getTeamMovies(@Url url: String, @Query("field_list") fieldList: String = "movies"): ApiItem<TeamMovies>
}
