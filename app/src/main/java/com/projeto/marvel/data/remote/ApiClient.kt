package com.projeto.marvel.data.remote

import com.projeto.marvel.BuildConfig
import android.content.Context
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
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

    private var cacheDir: File? = null

    /** Chamado no `MarvelApp`: liga o cache em disco (antes disso, e nos testes, fica sem cache). */
    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "http")
    }

    // A Comic Vine não manda cabeçalho de cache e limita requisições por hora: guarda 6 h.
    private val cacheFor = Interceptor { chain ->
        chain.proceed(chain.request()).newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", "public, max-age=$CACHE_SECONDS")
            .build()
    }

    // Sem rede, serve o que tiver guardado (até 7 dias) em vez de dar erro.
    private val offlineFallback = Interceptor { chain ->
        try {
            chain.proceed(chain.request())
        } catch (e: IOException) {
            val cached = chain.request().newBuilder()
                .cacheControl(CacheControl.Builder().onlyIfCached().maxStale(STALE_DAYS, TimeUnit.DAYS).build())
                .build()
            val response = chain.proceed(cached)
            if (!response.isSuccessful) {
                response.close()
                throw e
            }
            response
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .apply { cacheDir?.let { cache(Cache(it, CACHE_BYTES)) } }
            .addInterceptor(offlineFallback)
            .addNetworkInterceptor(cacheFor)
            .addInterceptor(authInterceptor)
        .addInterceptor(
            HttpLoggingInterceptor().setLevel(
                // BASIC: uma linha por requisição. BODY imprimia respostas de megabytes e deixava o app lento.
                if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            )
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    }

    private const val CACHE_SECONDS = 6 * 60 * 60
    private const val STALE_DAYS = 7
    private const val CACHE_BYTES = 50L * 1024 * 1024

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConstants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val comicVine: ComicVineService by lazy { retrofit.create(ComicVineService::class.java) }

    val catalog: CatalogService by lazy { retrofit.create(CatalogService::class.java) }
}
