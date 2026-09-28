package com.projeto.marvel.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val characters: List<CharacterSummary>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val auth: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var lastQuery: String? = null

    init { search(null) }

    fun search(query: String?) {
        lastQuery = query
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            _state.value = repository.searchCharacters(query).fold(
                onSuccess = { HomeUiState.Success(it) },
                onFailure = { HomeUiState.Error(it.message ?: "Falha ao carregar personagens") }
            )
        }
    }

    fun retry() = search(lastQuery)

    fun signOut() = auth.signOut()
}
