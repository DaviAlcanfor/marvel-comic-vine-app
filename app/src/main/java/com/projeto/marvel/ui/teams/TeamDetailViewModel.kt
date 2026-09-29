package com.projeto.marvel.ui.teams

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.TeamDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TeamDetailUiState {
    data object Loading : TeamDetailUiState
    data class Success(
        val team: TeamDetail,
        val members: List<CharacterSummary>,
        val enemies: List<CharacterSummary>
    ) : TeamDetailUiState
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
            _state.value = runCatching {
                val team = repository.getTeamDetail(apiDetailUrl).getOrThrow()
                val members = team.members.orEmpty().map { it.id }.toSet()
                val enemies = team.enemies.orEmpty().map { it.id }.toSet()
                // Uma busca só para os dois grupos: as referências do time não trazem foto.
                val people = repository.charactersByIds((members + enemies).toList()).getOrThrow()
                TeamDetailUiState.Success(team, people.filter { it.id in members }, people.filter { it.id in enemies })
            }.getOrElse { TeamDetailUiState.Error(it.message ?: "Falha ao carregar o time") }
        }
    }
}
