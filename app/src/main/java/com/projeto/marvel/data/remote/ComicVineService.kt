package com.projeto.marvel.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface ComicVineService {

    // Ex: https://comicvine.gamespot.com/api/issues/?api_key=KEY&format=json&filter=name:batman
    @GET("issues/")
    suspend fun getIssues(
        @Query("api_key") apiKey: String = ApiConstants.API_KEY,
        @Query("format") format: String = ApiConstants.FORMAT,
        @Query("filter") filter: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): IssueResponse
}

