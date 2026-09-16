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
    @SerializedName("count_of_team_members") val memberCount: Int?
)
