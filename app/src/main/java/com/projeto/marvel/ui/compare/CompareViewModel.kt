package com.projeto.marvel.ui.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.toFighter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Contender(val character: CharacterSummary, val fighter: Fighter)

sealed interface CompareUiState {
    data object Loading : CompareUiState

    data class Success(val left: Contender, val right: Contender) : CompareUiState {
        val commonPowers get() = names(left.character.powers?.map { it.name }, right.character.powers?.map { it.name })
        val commonTeams get() = names(left.character.teams?.map { it.name }, right.character.teams?.map { it.name })

        private fun names(a: List<String?>?, b: List<String?>?) =
            a.orEmpty().filterNotNull().intersect(b.orEmpty().filterNotNull().toSet()).sorted()
    }

    data class Error(val message: String) : CompareUiState
}

/** Dois personagens lado a lado: atributos da Batalha, fama, estreia e o que têm em comum. */
class CompareViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ComicVineRepository = ComicVineRepository()
) : ViewModel() {

    private val leftUrl: String = checkNotNull(savedStateHandle["leftUrl"])
    private val rightUrl: String = checkNotNull(savedStateHandle["rightUrl"])

    private val _state = MutableStateFlow<CompareUiState>(CompareUiState.Loading)
    val state: StateFlow<CompareUiState> = _state.asStateFlow()

    val urls get() = leftUrl to rightUrl

    init { load() }

    fun load() {
        _state.value = CompareUiState.Loading
        viewModelScope.launch {
            val left = async { repository.getCharacterDetail(leftUrl) }
            val right = async { repository.getCharacterDetail(rightUrl) }
            val a = left.await().getOrNull()
            val b = right.await().getOrNull()
            _state.value = if (a != null && b != null) {
                CompareUiState.Success(Contender(a, a.toFighter()), Contender(b, b.toFighter()))
            } else {
                CompareUiState.Error("Não deu para carregar os dois personagens.")
            }
        }
    }
}
