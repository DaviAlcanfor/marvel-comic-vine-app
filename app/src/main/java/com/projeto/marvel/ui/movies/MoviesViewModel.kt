package com.projeto.marvel.ui.movies

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.CatalogRepository
import com.projeto.marvel.data.RecentSearchStore
import com.projeto.marvel.data.remote.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.projeto.marvel.ui.SearchHistory
import kotlinx.coroutines.launch

sealed interface MoviesUiState {
    data object Loading : MoviesUiState
    data class Success(val movies: List<Movie>) : MoviesUiState
    data class Error(val message: String) : MoviesUiState
}

/** Catálogo de filmes da Marvel (algumas dezenas): carrega uma vez e a busca filtra na memória. */
class MoviesViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: CatalogRepository = CatalogRepository(),
    recents: RecentSearchStore = RecentSearchStore(application, RecentSearchStore.SCOPE_MOVIES)
) : AndroidViewModel(application) {

    private val history = SearchHistory(recents, viewModelScope)
    val recentSearches: StateFlow<List<String>> = history.queries

    fun forgetSearch(query: String) = history.forget(query)


    private val _state = MutableStateFlow<MoviesUiState>(MoviesUiState.Loading)
    val state: StateFlow<MoviesUiState> = _state.asStateFlow()

    private var all: List<Movie> = emptyList()
    private var query = ""

    init { load() }

    fun load() {
        _state.value = MoviesUiState.Loading
        viewModelScope.launch {
            repository.marvelMovies().fold(
                onSuccess = {
                    all = it
                    _state.value = MoviesUiState.Success(filtered())
                },
                onFailure = { _state.value = MoviesUiState.Error(it.message ?: "Falha ao carregar filmes") }
            )
        }
    }

    /** Durante o carregamento só guarda o texto: o filtro é aplicado quando a lista chegar. */
    fun search(text: String?) {
        query = text?.trim().orEmpty()
        if (_state.value !is MoviesUiState.Success) return
        val movies = filtered()
        if (movies.isNotEmpty()) history.rememberLater(query)
        _state.value = MoviesUiState.Success(movies)
    }

    private fun filtered() = all.filter { it.name.contains(query, ignoreCase = true) }
}
