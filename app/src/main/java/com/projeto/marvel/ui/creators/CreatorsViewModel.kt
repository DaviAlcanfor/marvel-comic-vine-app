package com.projeto.marvel.ui.creators

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.CatalogRepository
import com.projeto.marvel.data.RecentSearchStore
import com.projeto.marvel.data.remote.Person
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.projeto.marvel.ui.SearchHistory
import kotlinx.coroutines.launch

sealed interface CreatorsUiState {
    data object Loading : CreatorsUiState
    data class Success(val creators: List<Person>) : CreatorsUiState
    data class Error(val message: String) : CreatorsUiState
}

/** Sem busca: criadores lendários. Com busca: por nome (a Comic Vine tem milhares). */
class CreatorsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: CatalogRepository = CatalogRepository(),
    recents: RecentSearchStore = RecentSearchStore(application, RecentSearchStore.SCOPE_CREATORS)
) : AndroidViewModel(application) {

    private val history = SearchHistory(recents, viewModelScope)
    val recentSearches: StateFlow<List<String>> = history.queries

    fun forgetSearch(query: String) = history.forget(query)


    private val _state = MutableStateFlow<CreatorsUiState>(CreatorsUiState.Loading)
    val state: StateFlow<CreatorsUiState> = _state.asStateFlow()

    private var query: String? = null
    private var job: Job? = null

    init { search("") }

    fun search(text: String?) {
        val trimmed = text?.trim().orEmpty()
        if (trimmed == query && _state.value is CreatorsUiState.Success) return
        query = trimmed
        job?.cancel()
        job = viewModelScope.launch {
            // Espera parar de digitar: sem isso, cada letra virava uma requisição (limite da API).
            if (trimmed.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
            _state.value = CreatorsUiState.Loading
            _state.value = repository.creators(trimmed).fold(
                onSuccess = {
                    if (it.isNotEmpty()) history.rememberLater(trimmed)
                    CreatorsUiState.Success(it)
                },
                onFailure = { CreatorsUiState.Error(it.message ?: "Falha ao carregar criadores") }
            )
        }
    }

    fun retry() {
        val current = query.orEmpty()
        query = null
        search(current)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
