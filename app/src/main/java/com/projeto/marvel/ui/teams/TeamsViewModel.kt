package com.projeto.marvel.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.Team
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TeamsUiState {
    data object Loading : TeamsUiState
    data class Success(val teams: List<Team>) : TeamsUiState
    data class Error(val message: String) : TeamsUiState
}

class TeamsViewModel(
    private val repository: ComicVineRepository = ComicVineRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<TeamsUiState>(TeamsUiState.Loading)
    val state: StateFlow<TeamsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = TeamsUiState.Loading
        viewModelScope.launch {
            _state.value = repository.listTeams().fold(
                onSuccess = { TeamsUiState.Success(it) },
                onFailure = { TeamsUiState.Error(it.message ?: "Falha ao carregar times") }
            )
        }
    }
}
