package com.projeto.marvel.ui.info

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.R
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.CatalogRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.RatedMovie
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.Location
import com.projeto.marvel.data.remote.Movie
import com.projeto.marvel.data.remote.Person
import com.projeto.marvel.data.remote.ResourceRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InfoStat(@StringRes val label: Int, val value: String)

/**
 * Criador ou filme no mesmo formato de tela. [characters] chega depois (fotos vêm de outra
 * requisição); [characterCount] é o total na Comic Vine, que pode ser maior que o mostrado.
 */
data class InfoDetail(
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val stats: List<InfoStat>,
    val description: String?,
    @StringRes val charactersTitle: Int,
    val characterCount: Int,
    val characters: List<CharacterSummary> = emptyList(),
    /** Lugar real: busca para abrir no mapa (null = sem botão de mapa). */
    val mapQuery: String? = null
)

sealed interface InfoDetailUiState {
    data object Loading : InfoDetailUiState
    data class Success(val info: InfoDetail) : InfoDetailUiState
    data class Error(val message: String) : InfoDetailUiState
}

class InfoDetailViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val catalog: CatalogRepository = CatalogRepository(),
    private val characters: ComicVineRepository = ComicVineRepository(),
    private val store: ReadingStore = ReadingStore(application, AuthRepository().currentUser?.uid)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<InfoDetailUiState>(InfoDetailUiState.Loading)
    val state: StateFlow<InfoDetailUiState> = _state.asStateFlow()

    /** Só em filmes: o filme como item da estante (base do diálogo) e a avaliação salva, se houver. */
    var movieBase: RatedMovie? = null
        private set
    private val _rated = MutableStateFlow<RatedMovie?>(null)
    val rated: StateFlow<RatedMovie?> = _rated.asStateFlow()

    fun saveMovie(movie: RatedMovie) {
        store.saveMovie(movie)
        if (!movie.review.isNullOrBlank()) getApplication<Application>().mission(MissionEvent.REVIEW)
        _rated.value = movie
    }

    fun removeMovie(id: Int) {
        store.removeMovie(id)
        _rated.value = null
    }

    private val kind: String = checkNotNull(savedStateHandle[InfoDetailFragment.ARG_KIND])
    private val apiDetailUrl: String = checkNotNull(savedStateHandle[InfoDetailFragment.ARG_URL])

    init { load() }

    fun load() {
        _state.value = InfoDetailUiState.Loading
        viewModelScope.launch {
            val result = when (kind) {
                InfoDetailFragment.KIND_CREATOR ->
                    catalog.creator(apiDetailUrl).map { it.toInfo() to it.createdCharacters.orEmpty() }
                InfoDetailFragment.KIND_LOCATION ->
                    catalog.location(apiDetailUrl).map { it.toInfo(catalog.mapQuery(it.id)) to emptyList() }
                else -> catalog.movie(apiDetailUrl).map { movie ->
                    movieBase = RatedMovie(movie.id, movie.name, movie.image?.mediumUrl, apiDetailUrl, watched = true)
                    _rated.value = store.movies().firstOrNull { it.id == movie.id }
                    movie.toInfo() to movie.characters.orEmpty()
                }
            }
            result.fold(
                onSuccess = { (info, refs) ->
                    _state.value = InfoDetailUiState.Success(info)
                    loadCharacters(refs)
                },
                onFailure = { _state.value = InfoDetailUiState.Error(it.message ?: "Falha ao carregar") }
            )
        }
    }

    /**
     * Fotos dos personagens: primeiro os populares já em memória (sem requisição); se forem poucos,
     * busca até [MAX_FETCH] por id. Stan Lee criou 652 — buscar todos gastaria o limite da API.
     */
    private suspend fun loadCharacters(refs: List<ResourceRef>) {
        if (refs.isEmpty()) return
        val ids = refs.map { it.id }.toSet()
        val popular = characters.popularCharacters().getOrNull().orEmpty().filter { it.id in ids }
        val shown = if (popular.size >= MIN_POPULAR) {
            popular
        } else {
            characters.charactersByIds(refs.map { it.id }.take(MAX_FETCH)).getOrNull().orEmpty()
        }
        _state.update { state ->
            (state as? InfoDetailUiState.Success)?.let { it.copy(info = it.info.copy(characters = shown)) } ?: state
        }
    }

    private companion object {
        const val MIN_POPULAR = 6
        const val MAX_FETCH = 100
    }
}

private fun Person.toInfo() = InfoDetail(
    title = name,
    subtitle = listOfNotNull(hometown, country).distinct().joinToString(" · "),
    imageUrl = image?.mediumUrl,
    stats = listOfNotNull(
        formatDate(birth)?.let { InfoStat(R.string.info_born, it) },
        formatDate(deathDate)?.let { InfoStat(R.string.info_died, it) },
        InfoStat(R.string.info_created, createdCharacters.orEmpty().size.toString())
    ),
    description = description ?: deck,
    charactersTitle = R.string.info_section_created,
    characterCount = createdCharacters.orEmpty().size
)

private fun Movie.toInfo() = InfoDetail(
    title = name,
    subtitle = deck.orEmpty(),
    imageUrl = image?.mediumUrl,
    stats = listOfNotNull(
        rating?.takeIf { it.isNotBlank() }?.let { InfoStat(R.string.info_rating, it) },
        runtimeLabel?.let { InfoStat(R.string.info_runtime, it) },
        formatMoney(boxOffice)?.let { InfoStat(R.string.info_box_office, it) },
        formatMoney(budget)?.let { InfoStat(R.string.info_budget, it) }
    ),
    description = description,
    charactersTitle = R.string.info_section_characters,
    characterCount = characters.orEmpty().size
)

private fun Location.toInfo(mapQuery: String?) = InfoDetail(
    title = name,
    subtitle = deck.orEmpty(),
    imageUrl = image?.mediumUrl,
    stats = listOfNotNull(
        appearances?.let { InfoStat(R.string.detail_stat_appearances, it.toString()) },
        startYear?.takeIf { it.isNotBlank() }?.let { InfoStat(R.string.info_since, it) },
        InfoStat(R.string.info_kind, if (mapQuery != null) "Real" else "Fictício")
    ),
    description = description,
    charactersTitle = R.string.info_section_characters,
    characterCount = 0,
    mapQuery = mapQuery
)
