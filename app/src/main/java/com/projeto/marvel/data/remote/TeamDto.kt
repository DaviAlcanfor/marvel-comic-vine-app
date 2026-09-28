package com.projeto.marvel.data.remote

import com.google.gson.annotations.SerializedName

data class TeamListResponse(
    @SerializedName("error") val error: String?,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("number_of_total_results") val totalResults: Int,
    @SerializedName("results") val results: List<Team>?
)

data class Team(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("deck") val deck: String?,
    @SerializedName("image") val image: ComicVineImage?,
    @SerializedName("count_of_team_members") val memberCount: Int?,
    @SerializedName("api_detail_url") val apiDetailUrl: String? = null
)

data class TeamDetailResponse(
    @SerializedName("error") val error: String?,
    @SerializedName("results") val result: TeamDetail?
)

data class TeamDetail(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("deck") val deck: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("image") val image: ComicVineImage?,
    @SerializedName("publisher") val publisher: Publisher?,
    @SerializedName("count_of_team_members") val memberCount: Int?,
    @SerializedName("characters") val members: List<CharacterRef>?,
    @SerializedName("character_enemies") val enemies: List<CharacterRef>?
)

/** Referência a um personagem dentro de outro recurso (membros de um time, inimigos). */
data class CharacterRef(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("api_detail_url") val apiDetailUrl: String?
)
