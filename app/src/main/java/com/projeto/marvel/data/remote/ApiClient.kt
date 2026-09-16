package com.projeto.marvel.data.remote

import com.projeto.marvel.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // api_key/format são iguais em toda chamada: entram aqui, não na assinatura de cada endpoint.
    // A Comic Vine bloqueia (403) requests sem User-Agent próprio.
    private val authInterceptor = Interceptor { chain ->
        val url = chain.request().url.newBuilder()
            .addQueryParameter("api_key", ApiConstants.API_KEY)
            .addQueryParameter("format", "json")
            .build()
        chain.proceed(
            chain.request().newBuilder()
                .url(url)
                .header("User-Agent", "MarvelApp/${BuildConfig.VERSION_NAME}")
                .build()
        )
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().setLevel(
                if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
            )
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val comicVine: ComicVineService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ComicVineService::class.java)
    }
}
