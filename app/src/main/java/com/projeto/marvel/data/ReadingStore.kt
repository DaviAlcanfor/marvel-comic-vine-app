package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

/** Estado da HQ na estante, como no Skoob. */
enum class ReadingStatus { WANT_TO_READ, READING, READ }

/**
 * HQ na estante. [rating] (0 = sem nota) e [review] só fazem sentido quando já foi lida.
 * Guarda o necessário para listar sem ir à API.
 */
data class ReadComic(
    val id: Int,
    val title: String,
    val coverUrl: String?,
    val rating: Int = 0,
    val review: String? = null,
    // Nulo nas HQs salvas antes de existir status (todas eram "lidas"): o Gson não usa o default.
    @SerializedName("status") private val savedStatus: ReadingStatus? = null
) {
    val status: ReadingStatus get() = savedStatus ?: ReadingStatus.READ

    fun withStatus(status: ReadingStatus) = copy(savedStatus = status)
}

/** Filme na estante de filmes: [watched] = já viu (nota e resenha só nesse caso). */
data class RatedMovie(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val apiDetailUrl: String?,
    val watched: Boolean,
    val rating: Int = 0,
    val review: String? = null
)

/** Algo escolhido como favorito no Perfil (herói ou série): o suficiente para mostrar o card. */
data class Favorite(val id: Int, val name: String, val imageUrl: String?, val apiDetailUrl: String? = null)

data class ProfilePreferences(
    val hero: Favorite? = null,
    val series: Favorite? = null,
    val genres: List<String> = emptyList()
)

/**
 * Estante de HQs (tipo Skoob) e preferências do Perfil, salvas no aparelho via SharedPreferences,
 * separadas por usuário. ponytail: só local; migrar para Firestore se precisar sincronizar.
 */
class ReadingStore(context: Context, userId: String?) {

    private val prefs = context.getSharedPreferences("$PREFS_PREFIX${userId ?: "local"}", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Mais recente primeiro. */
    fun get(): List<ReadComic> =
        prefs.getString(KEY_COMICS, null)?.let { gson.fromJson<List<ReadComic>>(it, LIST_TYPE) }.orEmpty()

    fun save(comic: ReadComic) = write(get().withRead(comic))

    fun remove(id: Int) = write(get().filterNot { it.id == id })

    /** Estante de filmes, separada da de HQs. Mais recente primeiro. */
    fun movies(): List<RatedMovie> =
        prefs.getString(KEY_MOVIES, null)?.let { gson.fromJson<List<RatedMovie>>(it, MOVIES_TYPE) }.orEmpty()

    fun saveMovie(movie: RatedMovie) = writeMovies(listOf(movie) + movies().filterNot { it.id == movie.id })

    fun removeMovie(id: Int) = writeMovies(movies().filterNot { it.id == id })

    private fun writeMovies(movies: List<RatedMovie>): List<RatedMovie> {
        prefs.edit { putString(KEY_MOVIES, gson.toJson(movies)) }
        return movies
    }

    fun preferences(): ProfilePreferences =
        prefs.getString(KEY_PREFERENCES, null)?.let { gson.fromJson(it, ProfilePreferences::class.java) }
            ?: ProfilePreferences()

    fun savePreferences(preferences: ProfilePreferences) {
        prefs.edit { putString(KEY_PREFERENCES, gson.toJson(preferences)) }
    }

    private fun write(comics: List<ReadComic>): List<ReadComic> {
        prefs.edit { putString(KEY_COMICS, gson.toJson(comics)) }
        return comics
    }

    private companion object {
        const val PREFS_PREFIX = "reading_"
        const val KEY_COMICS = "comics"
        const val KEY_PREFERENCES = "preferences"
        const val KEY_MOVIES = "movies"
        val LIST_TYPE = object : TypeToken<List<ReadComic>>() {}.type
        val MOVIES_TYPE = object : TypeToken<List<RatedMovie>>() {}.type
    }
}

/** [comic] vai para o topo; se já estava na estante, substitui (novo status, nota ou resenha). */
internal fun List<ReadComic>.withRead(comic: ReadComic) = listOf(comic) + filterNot { it.id == comic.id }

/** Gêneros para o usuário marcar no Perfil. A Comic Vine não classifica HQs por gênero. */
val GENRES = listOf(
    "Ação", "Aventura", "Cósmico", "Urbano", "Terror", "Mistério", "Ficção científica",
    "Fantasia", "Humor", "Drama", "Espionagem", "Sobrenatural"
)
