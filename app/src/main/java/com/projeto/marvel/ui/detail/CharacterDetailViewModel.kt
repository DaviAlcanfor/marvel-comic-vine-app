package com.projeto.marvel.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.CatalogRepository
import com.projeto.marvel.data.TimelineEvent
import com.projeto.marvel.data.TimelineRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.Movie
import com.projeto.marvel.data.remote.Person
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    /** [coverUrl] (capa da primeira aparição) e [movies] (com pôster) chegam depois — ou nunca. */
    data class Success(
        val character: CharacterSummary,
        val coverUrl: String? = null,
        val movies: List<Movie> = emptyList(),
        val creators: List<Person> = emptyList(),
        val timeline: List<TimelineEvent> = emptyList()
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class CharacterDetailViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val catalog: CatalogRepository = CatalogRepository(),
    private val timelines: TimelineRepository = TimelineRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        val apiDetailUrl: String = checkNotNull(savedStateHandle["apiDetailUrl"])
        viewModelScope.launch {
            val detail = repository.getCharacterDetail(apiDetailUrl)
            _state.value = detail.fold(
                onSuccess = { DetailUiState.Success(it) },
                onFailure = { DetailUiState.Error(it.message ?: "Falha ao carregar personagem") }
            )
            val character = detail.getOrNull() ?: return@launch
            // Extras: se falharem, as seções só não aparecem.
            launch {
                val issueId = character.firstIssue?.id ?: return@launch
                val cover = repository.issueCover(issueId).getOrNull() ?: return@launch
                _state.update { (it as? DetailUiState.Success)?.copy(coverUrl = cover) ?: it }
            }
            launch {
                val ids = character.creators.orEmpty().map { it.id }.takeIf { it.isNotEmpty() } ?: return@launch
                val creators = catalog.creatorsByIds(ids).getOrNull().orEmpty()
                _state.update { (it as? DetailUiState.Success)?.copy(creators = creators) ?: it }
            }
            launch {
                val timeline = timelines.timeline(character).getOrNull().orEmpty()
                _state.update { (it as? DetailUiState.Success)?.copy(timeline = timeline) ?: it }
            }
            launch {
                val ids = character.movies.orEmpty().map { it.id }.takeIf { it.isNotEmpty() } ?: return@launch
                val movies = catalog.moviesByIds(ids).getOrNull().orEmpty()
                _state.update { (it as? DetailUiState.Success)?.copy(movies = movies) ?: it }
            }
        }
    }
}
