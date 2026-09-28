package com.projeto.marvel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val characters: List<CharacterSummary>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

/** Sem busca: populares da Marvel. Com busca: resultados por nome, paginados ao rolar. */
class HomeViewModel(
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val auth: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var query = ""
    private var job: Job? = null
    private var nextOffset = 0
    private var endReached = true
    private var loadingMore = false

    init { load("") }

    fun search(query: String?) {
        val trimmed = query?.trim().orEmpty()
        // O EditText reaplica o texto ao recriar a View: mesma busca já carregada não recarrega.
        if (trimmed == this.query && _state.value is HomeUiState.Success) return
        load(trimmed)
    }

    fun retry() = load(query)

    /** Chamado quando a lista chega ao fim. Falha aqui é silenciosa: rolar de novo tenta outra vez. */
    fun loadMore() {
        val current = (_state.value as? HomeUiState.Success)?.characters ?: return
        if (endReached || loadingMore) return
        loadingMore = true
        job = viewModelScope.launch {
            try {
                repository.searchCharacters(query, nextOffset).onSuccess { page ->
                    nextOffset += page.size
                    endReached = page.size < ComicVineRepository.PAGE_SIZE
                    _state.value = HomeUiState.Success(current + page)
                }
            } finally {
                loadingMore = false
            }
        }
    }

    fun signOut() = auth.signOut()

    private fun load(query: String) {
        this.query = query
        job?.cancel()
        job = viewModelScope.launch {
            // Espera parar de digitar: sem isso, cada letra virava uma requisição (limite da API).
            if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
            _state.value = HomeUiState.Loading
            val result = if (query.isEmpty()) repository.popularCharacters() else repository.searchCharacters(query)
            _state.value = result.fold(
                onSuccess = { page ->
                    nextOffset = page.size
                    endReached = query.isEmpty() || page.size < ComicVineRepository.PAGE_SIZE
                    HomeUiState.Success(page)
                },
                onFailure = { HomeUiState.Error(it.message ?: "Falha ao carregar personagens") }
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
