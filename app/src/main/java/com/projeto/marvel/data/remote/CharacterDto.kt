package com.projeto.marvel.data.remote

import com.google.gson.annotations.SerializedName

data class CharacterListResponse(
    @SerializedName("error") val error: String?,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("number_of_total_results") val totalResults: Int,
    @SerializedName("results") val results: List<CharacterSummary>?
)

data class CharacterDetailResponse(
    @SerializedName("error") val error: String?,
    @SerializedName("results") val result: CharacterSummary?
)

data class CharacterSummary(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("real_name") val realName: String?,
    @SerializedName("deck") val deck: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("image") val image: ComicVineImage?,
    @SerializedName("publisher") val publisher: Publisher?,
    @SerializedName("api_detail_url") val apiDetailUrl: String?,
    @SerializedName("count_of_issue_appearances") val issueAppearances: Int? = null,
    /** Só vêm no endpoint de detalhe; na listagem são sempre nulos. */
    @SerializedName("powers") val powers: List<Power>? = null,
    @SerializedName("origin") val origin: NamedResource? = null,
    @SerializedName("teams") val teams: List<NamedResource>? = null,
    @SerializedName("first_appeared_in_issue") val firstIssue: IssueRef? = null,
    /** Só no perfil completo (chat), ver `getCharacterDetail(full = true)`. */
    @SerializedName("character_enemies") val enemies: List<NamedResource>? = null,
    @SerializedName("character_friends") val friends: List<NamedResource>? = null,
    /** Data de estreia (ex. "Oct 14, 1962"); vem também na lista (Início: "estreou neste dia"). */
    @SerializedName("birth") val birth: String? = null,
    /** Só no detalhe: apelidos (um por linha), criadores, filmes e edições em que morreu. */
    @SerializedName("aliases") val aliases: String? = null,
    @SerializedName("creators") val creators: List<ResourceRef>? = null,
    @SerializedName("movies") val movies: List<ResourceRef>? = null,
    @SerializedName("issues_died_in") val issuesDiedIn: List<NamedResource>? = null
)

data class IssueRef(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String? = null,
    @SerializedName("issue_number") val issueNumber: String? = null
)

/** Referência resumida a outro recurso (ex.: origem "Human", time "Avengers"). */
data class NamedResource(@SerializedName("name") val name: String?, @SerializedName("id") val id: Int? = null)

data class Power(@SerializedName("name") val name: String)

data class Publisher(@SerializedName("name") val name: String?)

data class ComicVineImage(
    @SerializedName("original_url") val originalUrl: String?,
    @SerializedName("medium_url") val mediumUrl: String?,
    @SerializedName("small_url") val smallUrl: String?,
    @SerializedName("thumb_url") val thumbUrl: String?
)
