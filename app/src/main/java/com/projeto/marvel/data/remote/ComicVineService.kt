package com.projeto.marvel.data.remote

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface ComicVineService {

    /** [filter] no formato da API: `name:batman`. api_key e format vêm do interceptor. */
    @GET("issues/")
    suspend fun getIssues(
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): IssueResponse

    /**
     * [filter] no formato `name:hulk`. A Comic Vine não tem filtro nativo por editora
     * (`publisher`) neste endpoint, então a lista pode incluir personagens de outras
     * editoras além da Marvel — ver TODO em [com.projeto.marvel.data.ComicVineRepository].
     */
    @GET("characters/")
    suspend fun getCharacters(
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        /** Formato `campo:asc|desc`, ex. `count_of_issue_appearances:desc`. */
        @Query("sort") sort: String? = null
    ): CharacterListResponse

    /** [url] é o `api_detail_url` já absoluto devolvido pela listagem de personagens. */
    @GET
    suspend fun getCharacterDetail(@Url url: String): CharacterDetailResponse

    @GET("teams/")
    suspend fun getTeams(
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): TeamListResponse
}
