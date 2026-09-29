package com.projeto.marvel.ui.lookalike

import android.graphics.Bitmap
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.LookAlike
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.LookAlikeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LookAlikeUiState {
    data object Idle : LookAlikeUiState

    data class Analyzing(val photo: Bitmap) : LookAlikeUiState

    data class Done(val photo: Bitmap, val result: LookAlikeResult) : LookAlikeUiState

    data class Error(val photo: Bitmap, val message: String) : LookAlikeUiState
}

class LookAlikeViewModel @JvmOverloads constructor(
    application: Application,
    private val lookAlike: LookAlike = LookAlike()
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<LookAlikeUiState>(LookAlikeUiState.Idle)
    val state: StateFlow<LookAlikeUiState> = _state.asStateFlow()

    fun analyze(photo: Bitmap) {
        if (_state.value is LookAlikeUiState.Analyzing) return
        _state.value = LookAlikeUiState.Analyzing(photo)
        viewModelScope.launch {
            _state.value = lookAlike.match(photo).fold(
                onSuccess = {
                    getApplication<Application>().mission(MissionEvent.LOOK_ALIKE)
                    LookAlikeUiState.Done(photo, it)
                },
                onFailure = { LookAlikeUiState.Error(photo, it.message ?: "Não deu para analisar agora.") }
            )
        }
    }
}
