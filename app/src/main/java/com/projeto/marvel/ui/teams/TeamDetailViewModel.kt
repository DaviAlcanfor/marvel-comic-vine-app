package com.projeto.marvel.ui.teams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.TeamDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TeamDetailUiState {
    data object Loading : TeamDetailUiState
    data class Success(val team: TeamDetail) : TeamDetailUiState
    data class Error(val message: String) : TeamDetailUiState
}

class TeamDetailViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ComicVineRepository = ComicVineRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<TeamDetailUiState>(TeamDetailUiState.Loading)
    val state: StateFlow<TeamDetailUiState> = _state.asStateFlow()

    private val apiDetailUrl: String = checkNotNull(savedStateHandle["apiDetailUrl"])

    init { load() }

    fun load() {
        _state.value = TeamDetailUiState.Loading
        viewModelScope.launch {
            _state.value = repository.getTeamDetail(apiDetailUrl).fold(
                onSuccess = { TeamDetailUiState.Success(it) },
                onFailure = { TeamDetailUiState.Error(it.message ?: "Falha ao carregar o time") }
            )
        }
    }
}
