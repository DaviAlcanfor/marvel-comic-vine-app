package com.projeto.marvel.data.remote

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/** Envelope de lista da Comic Vine (mesmo formato em todo endpoint de lista). */
data class ApiList<T>(
    @SerializedName("error") val error: String?,
    @SerializedName("results") val results: List<T>?
)

/** Time na linha do tempo: a data vem da 1ª edição dele; [members] mede o tamanho/importância. */
data class TeamDebut(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("count_of_team_members") val members: Int?,
    @SerializedName("first_appeared_in_issue") val firstIssue: IssueRef?
)

/** Envelope de detalhe (um recurso só). */
data class ApiItem<T>(
    @SerializedName("error") val error: String?,
    @SerializedName("results") val result: T?
)

/** Referência a outro recurso (filme, criador…) com o necessário para abrir o detalhe dele. */
data class ResourceRef(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("api_detail_url") val apiDetailUrl: String?
)

/** Criador (roteirista, desenhista…). Campos de detalhe vêm nulos na lista. */
data class Person(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("deck") val deck: String?,
    @SerializedName("image") val image: ComicVineImage?,
    @SerializedName("api_detail_url") val apiDetailUrl: String?,
    @SerializedName("description") val description: String? = null,
    @SerializedName("birth") val birth: String? = null,
    // Às vezes objeto ({"date": ...}), às vezes texto: lido com [deathDate].
    @SerializedName("death") val death: JsonElement? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("hometown") val hometown: String? = null,
    @SerializedName("created_characters") val createdCharacters: List<ResourceRef>? = null
) {
    val deathDate: String?
        get() {
            val value = death?.takeUnless { it.isJsonNull } ?: return null
            if (!value.isJsonObject) return value.asString
            return value.asJsonObject.get("date")?.takeUnless { it.isJsonNull }?.asString
        }
}

/** Filme. Campos de detalhe vêm nulos na lista. */
data class Movie(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("deck") val deck: String?,
    @SerializedName("image") val image: ComicVineImage?,
    @SerializedName("api_detail_url") val apiDetailUrl: String?,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("runtime") val runtime: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("box_office_revenue") val boxOffice: String? = null,
    @SerializedName("budget") val budget: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("characters") val characters: List<ResourceRef>? = null
)

/** Só os filmes de um time (ver [CatalogService.getTeamMovies]). */
data class TeamMovies(@SerializedName("movies") val movies: List<ResourceRef>?)

/** Lugar das HQs (real ou fictício). Campos de detalhe vêm nulos na lista. */
data class Location(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("deck") val deck: String?,
    @SerializedName("image") val image: ComicVineImage?,
    @SerializedName("api_detail_url") val apiDetailUrl: String?,
    @SerializedName("count_of_issue_appearances") val appearances: Int? = null,
    @SerializedName("start_year") val startYear: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("first_appeared_in_issue") val firstIssue: IssueRef? = null
)
