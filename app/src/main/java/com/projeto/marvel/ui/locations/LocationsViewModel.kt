package com.projeto.marvel.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.CatalogRepository
import com.projeto.marvel.data.remote.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [real]: existe no mundo real (tem "ver no mapa"). */
data class Place(val location: Location, val real: Boolean)

sealed interface LocationsUiState {
    data object Loading : LocationsUiState
    data class Success(val places: List<Place>) : LocationsUiState
    data class Error(val message: String) : LocationsUiState
}

/** Lugares em destaque (poucos): carrega uma vez e a busca filtra na memória. */
class LocationsViewModel(
    private val repository: CatalogRepository = CatalogRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<LocationsUiState>(LocationsUiState.Loading)
    val state: StateFlow<LocationsUiState> = _state.asStateFlow()

    private var all: List<Place> = emptyList()
    private var query = ""

    init { load() }

    fun load() {
        _state.value = LocationsUiState.Loading
        viewModelScope.launch {
            repository.locations().fold(
                onSuccess = { locations ->
                    all = locations.map { Place(it, repository.mapQuery(it.id) != null) }
                    _state.value = LocationsUiState.Success(filtered())
                },
                onFailure = { _state.value = LocationsUiState.Error(it.message ?: "Falha ao carregar lugares") }
            )
        }
    }

    fun search(text: String?) {
        query = text?.trim().orEmpty()
        if (_state.value is LocationsUiState.Success) _state.value = LocationsUiState.Success(filtered())
    }

    private fun filtered() = all.filter { it.location.name.contains(query, ignoreCase = true) }
}
