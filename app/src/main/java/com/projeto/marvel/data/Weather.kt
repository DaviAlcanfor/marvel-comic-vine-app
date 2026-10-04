package com.projeto.marvel.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/** O herói que combina com o tempo agora: [name] = como está na Comic Vine; [weather] = "chuva", "31°"… */
data class WeatherHero(val name: String, val weather: String)

private const val HOT_C = 28.0
private const val COLD_C = 12.0

/** Códigos de tempo da OMM (WMO), usados pela Open-Meteo. */
@Suppress("MagicNumber") // os códigos são a própria tabela da OMM
private object Wmo {
    val STORM = 95..99
    val SNOW = (71..77) + (85..86)
    val RAIN = (51..67) + (80..82)
    val FOG = setOf(45, 48)
    val CLOUDS = 1..3
}

/**
 * Herói do clima (regra pura, testada em WeatherTest): trovoada = Thor, chuva = Storm, neve = Iceman,
 * neblina = Mulher Invisível; céu aberto: calor = Tocha Humana, frio = Iceman, nublado = Doutor
 * Estranho, ameno = Homem-Aranha (dia bom para balançar pela cidade).
 */
fun weatherHero(code: Int, temperature: Double): WeatherHero = when {
    code in Wmo.STORM -> WeatherHero("Thor", "trovoada")
    code in Wmo.SNOW -> WeatherHero("Iceman", "neve")
    code in Wmo.RAIN -> WeatherHero("Storm", "chuva")
    code in Wmo.FOG -> WeatherHero("Invisible Woman", "neblina")
    temperature >= HOT_C -> WeatherHero("Human Torch", "calor")
    temperature <= COLD_C -> WeatherHero("Iceman", "frio")
    code in Wmo.CLOUDS -> WeatherHero("Doctor Strange", "céu nublado")
    else -> WeatherHero("Spider-Man", "céu aberto")
}

private data class OpenMeteoResponse(@SerializedName("current") val current: Current?) {
    data class Current(
        @SerializedName("temperature_2m") val temperature: Double?,
        @SerializedName("weather_code") val code: Int?
    )
}

/** Tempo agora na coordenada: Open-Meteo (grátis, sem chave). Par (código WMO, °C). */
class WeatherRepository(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun current(latitude: Double, longitude: Double): Result<Pair<Int, Double>> =
        withContext(Dispatchers.IO) { runCatching { fetch(latitude, longitude) } }

    private fun fetch(latitude: Double, longitude: Double): Pair<Int, Double> {
        val url = String.format(Locale.US, URL, latitude, longitude)
        val body = client.newCall(Request.Builder().url(url).build()).execute().use { it.body?.string() }
        val current = Gson().fromJson(body, OpenMeteoResponse::class.java).current
        return requireNotNull(current?.code) to requireNotNull(current?.temperature)
    }

    private companion object {
        const val URL =
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code"
    }
}
