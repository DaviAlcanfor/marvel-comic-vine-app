package com.projeto.marvel.data.remote

import com.projeto.marvel.BuildConfig

object ApiConstants {
    const val BASE_URL = "https://comicvine.gamespot.com/api/"
    const val FORMAT = "json"

    // Vem do local.properties via buildConfigField (não fica no código/versionamento)
    val API_KEY = BuildConfig.COMIC_VINE_API_KEY
}