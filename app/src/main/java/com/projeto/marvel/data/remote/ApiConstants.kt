package com.projeto.marvel.data.remote

import com.projeto.marvel.BuildConfig

object ApiConstants {
    const val BASE_URL = "https://comicvine.gamespot.com/api/"

    // Vem do local.properties via buildConfigField (não fica no versionamento)
    val API_KEY: String = BuildConfig.COMIC_VINE_API_KEY
}
