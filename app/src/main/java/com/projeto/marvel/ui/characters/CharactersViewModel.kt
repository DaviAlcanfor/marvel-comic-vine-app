package com.projeto.marvel.ui.characters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.RecentSearchStore
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.ui.SearchHistory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CharactersUiState {
    data object Loading : CharactersUiState
    data class Success(val characters: List<CharacterSummary>) : CharactersUiState
    data class Error(val message: String) : CharactersUiState
}

/** Sem busca: populares da Marvel. Com busca: resultados por nome, paginados ao rolar. */
class CharactersViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val recents: RecentSearchStore = RecentSearchStore(application)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<CharactersUiState>(CharactersUiState.Loading)
    val state: StateFlow<CharactersUiState> = _state.asStateFlow()

    private val history = SearchHistory(recents, viewModelScope)
    val recentSearches: StateFlow<List<String>> = history.queries

    private var query = ""
    private var job: Job? = null

    // Tudo o que já veio da API (todas as páginas); a tela recebe só o que passa no filtro.
    private var all: List<CharacterSummary> = emptyList()

    /** Origem escolhida nos chips (nome em inglês, como vem da API); null = todas. */
    var origin: String? = null
        private set
    private var nextOffset = 0
    private var endReached = true
    private var loadingMore = false

    init { load("") }

    fun search(query: String?) {
        val trimmed = query?.trim().orEmpty()
        // O EditText reaplica o texto ao recriar a View: mesma busca já carregada não recarrega.
        if (trimmed == this.query && _state.value is CharactersUiState.Success) return
        load(trimmed)
    }

    fun retry() = load(query)

    /** Chamado quando a lista chega ao fim. Falha aqui é silenciosa: rolar de novo tenta outra vez. */
    fun loadMore() {
        if (_state.value !is CharactersUiState.Success || endReached || loadingMore) return
        loadingMore = true
        job = viewModelScope.launch {
            try {
                repository.searchCharacters(query, nextOffset).onSuccess { page ->
                    nextOffset += page.size
                    endReached = page.size < ComicVineRepository.PAGE_SIZE
                    all = all + page
                    _state.value = CharactersUiState.Success(filtered())
                }
            } finally {
                loadingMore = false
            }
        }
    }

    /** Guarda a busca atual quando ela foi útil (abriu um resultado ou confirmou no teclado). */
    fun rememberSearch() = history.remember(query)

    fun forgetSearch(query: String) = history.forget(query)

    fun filterOrigin(origin: String?) {
        this.origin = origin
        if (_state.value is CharactersUiState.Success) _state.value = CharactersUiState.Success(filtered())
    }

    private fun filtered() = origin?.let { wanted -> all.filter { it.origin?.name == wanted } } ?: all

    private fun load(query: String) {
        this.query = query
        job?.cancel()
        job = viewModelScope.launch {
            // Espera parar de digitar: sem isso, cada letra virava uma requisição (limite da API).
            if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
            _state.value = CharactersUiState.Loading
            val result = if (query.isEmpty()) repository.popularCharacters() else repository.searchCharacters(query)
            _state.value = result.fold(
                onSuccess = { page ->
                    nextOffset = page.size
                    endReached = query.isEmpty() || page.size < ComicVineRepository.PAGE_SIZE
                    all = page
                    if (page.isNotEmpty()) history.rememberLater(query)
                    CharactersUiState.Success(filtered())
                },
                onFailure = { CharactersUiState.Error(it.message ?: "Falha ao carregar personagens") }
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
