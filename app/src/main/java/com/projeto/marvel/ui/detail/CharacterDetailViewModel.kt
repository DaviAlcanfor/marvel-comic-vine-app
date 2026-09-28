package com.projeto.marvel.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val character: CharacterSummary) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class CharacterDetailViewModel @JvmOverloads constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ComicVineRepository = ComicVineRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        val apiDetailUrl: String = checkNotNull(savedStateHandle["apiDetailUrl"])
        viewModelScope.launch {
            _state.value = repository.getCharacterDetail(apiDetailUrl).fold(
                onSuccess = { DetailUiState.Success(it) },
                onFailure = { DetailUiState.Error(it.message ?: "Falha ao carregar personagem") }
            )
        }
    }
}
