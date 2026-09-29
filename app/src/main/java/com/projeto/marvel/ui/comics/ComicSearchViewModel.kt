package com.projeto.marvel.ui.comics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.RecentSearchStore
import com.projeto.marvel.data.remote.Issue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.projeto.marvel.ui.SearchHistory
import kotlinx.coroutines.launch

sealed interface ComicSearchUiState {
    /** Campo vazio: ainda não buscou nada. */
    data object Idle : ComicSearchUiState
    data object Loading : ComicSearchUiState
    data class Success(val comics: List<ComicItem>) : ComicSearchUiState
    data class Error(val message: String) : ComicSearchUiState
}

class ComicSearchViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val store: ReadingStore = ReadingStore(application, AuthRepository().currentUser?.uid),
    recents: RecentSearchStore = RecentSearchStore(application, RecentSearchStore.SCOPE_COMICS)
) : AndroidViewModel(application) {

    private val history = SearchHistory(recents, viewModelScope)
    val recentSearches: StateFlow<List<String>> = history.queries

    fun forgetSearch(query: String) = history.forget(query)


    private val _state = MutableStateFlow<ComicSearchUiState>(ComicSearchUiState.Idle)
    val state: StateFlow<ComicSearchUiState> = _state.asStateFlow()

    private var query = ""
    private var job: Job? = null

    /** Busca séries (volumes) em vez de HQs: modo de escolher a série preferida do Perfil. */
    var series = false

    fun search(query: String?) {
        val trimmed = query?.trim().orEmpty()
        if (trimmed == this.query) return
        this.query = trimmed
        job?.cancel()
        if (trimmed.isEmpty()) {
            _state.value = ComicSearchUiState.Idle
            return
        }
        job = viewModelScope.launch {
            // Espera parar de digitar: sem isso, cada letra virava uma requisição (limite da API).
            delay(SEARCH_DEBOUNCE_MILLIS)
            _state.value = ComicSearchUiState.Loading
            _state.value = repository.searchComics(trimmed, series).fold(
                onSuccess = { issues ->
                    if (issues.isNotEmpty()) history.rememberLater(trimmed)
                    ComicSearchUiState.Success(withShelf(issues.map { it.toReadComic() }))
                },
                onFailure = { ComicSearchUiState.Error(it.message ?: "Falha ao buscar HQs") }
            )
        }
    }

    fun retry() {
        val current = query
        query = ""
        search(current)
    }

    fun save(comic: ReadComic) = refreshShelf {
        store.save(comic)
        if (!comic.review.isNullOrBlank()) getApplication<Application>().mission(MissionEvent.REVIEW)
    }

    fun remove(id: Int) = refreshShelf { store.remove(id) }

    private fun refreshShelf(change: () -> Unit) {
        change()
        _state.update { state ->
            if (state is ComicSearchUiState.Success) {
                ComicSearchUiState.Success(withShelf(state.comics.map { it.comic }))
            } else {
                state
            }
        }
    }

    /** Resultado da busca com a nota da estante para as HQs já lidas. */
    private fun withShelf(comics: List<ReadComic>): List<ComicItem> {
        val shelf = store.get().associateBy { it.id }
        return comics.map { comic ->
            shelf[comic.id]?.let { ComicItem(it, onShelf = true) } ?: ComicItem(comic, onShelf = false)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}

private fun Issue.toReadComic() = ReadComic(id, title, image?.mediumUrl)
