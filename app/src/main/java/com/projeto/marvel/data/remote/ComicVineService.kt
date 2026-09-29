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
        @Query("offset") offset: Int = 0,
        @Query("field_list") fieldList: String? = null
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
        /** Campos a devolver, separados por vírgula. Sem isso vem tudo (inclusive HTML longo). */
        @Query("field_list") fieldList: String? = null
    ): CharacterListResponse

    /** [url] é o `api_detail_url` já absoluto devolvido pela listagem de personagens. */
    @GET
    suspend fun getCharacterDetail(
        @Url url: String,
        @Query("field_list") fieldList: String? = null
    ): CharacterDetailResponse

    /** [url] é o `api_detail_url` já absoluto devolvido pela listagem de times. */
    @GET
    suspend fun getTeamDetail(@Url url: String, @Query("field_list") fieldList: String? = null): TeamDetailResponse

    /** Busca textual da Comic Vine. [resources]: tipo do resultado (ex. `issue`); mesmo formato de lista. */
    @GET("search/")
    suspend fun search(
        @Query("query") query: String,
        @Query("resources") resources: String,
        @Query("limit") limit: Int = 30,
        @Query("field_list") fieldList: String? = null
    ): IssueResponse

    @GET("teams/")
    suspend fun getTeams(
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): TeamListResponse
}
