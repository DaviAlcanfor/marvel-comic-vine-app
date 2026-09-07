package com.projeto.marvel.data.remote

import com.google.gson.annotations.SerializedName

data class IssueResponse(
    @SerializedName("error") val error: String,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("number_of_page_results") val numberOfPageResults: Int,
    @SerializedName("number_of_total_results") val numberOfTotalResults: Int,
    @SerializedName("results") val results: List<Issue>
)

data class Issue(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("issue_number") val issueNumber: String?,
    @SerializedName("cover_date") val coverDate: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("image") val image: IssueImage?
)

data class IssueImage(
    @SerializedName("original_url") val originalUrl: String?,
    @SerializedName("medium_url") val mediumUrl: String?,
    @SerializedName("thumb_url") val thumbUrl: String?
)

