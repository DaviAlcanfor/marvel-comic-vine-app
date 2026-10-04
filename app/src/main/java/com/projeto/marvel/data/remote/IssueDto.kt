package com.projeto.marvel.data.remote

import com.google.gson.annotations.SerializedName

data class IssueResponse(
    @SerializedName("error") val error: String?,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("number_of_total_results") val totalResults: Int,
    @SerializedName("results") val results: List<Issue>?
)

data class Issue(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("issue_number") val issueNumber: String?,
    @SerializedName("cover_date") val coverDate: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("volume") val volume: Volume?,
    @SerializedName("image") val image: ComicVineImage?,
    /** Dia em que chegou às bancas (só vem quando pedido no field_list). */
    @SerializedName("store_date") val storeDate: String? = null
) {
    /** "Amazing Spider-Man #12": como se acha na banca (o `name` da edição às vezes é a lista das histórias). */
    val seriesTitle: String
        get() = volume?.name?.let { series -> listOfNotNull(series, issueNumber?.let { "#$it" }).joinToString(" ") }
            ?: title

    /** "Batman #12" — o `name` da issue costuma vir nulo; o do volume não. */
    val title: String
        get() = listOfNotNull(name ?: volume?.name, issueNumber?.let { "#$it" })
            .joinToString(" ")
            .ifBlank { "Issue $id" }
}

data class Volume(@SerializedName("name") val name: String?, @SerializedName("id") val id: Int? = null)
