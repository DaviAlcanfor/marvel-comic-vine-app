package com.projeto.marvel.ui

import com.projeto.marvel.data.RecentSearchStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Histórico de buscas de uma tela, para a ViewModel. [rememberLater] salva a busca que "assentou"
 * (sem digitar por [SETTLE_MILLIS] e com resultado); os pedaços digitados antes somem sozinhos
 * (ver [RecentSearchStore]).
 */
class SearchHistory(private val store: RecentSearchStore, private val scope: CoroutineScope) {

    private val _queries = MutableStateFlow(store.get())
    val queries: StateFlow<List<String>> = _queries.asStateFlow()

    private var pending: Job? = null

    fun remember(query: String) {
        if (query.isNotBlank()) _queries.value = store.add(query.trim())
    }

    fun rememberLater(query: String) {
        pending?.cancel()
        if (query.isBlank()) return
        pending = scope.launch {
            delay(SETTLE_MILLIS)
            remember(query)
        }
    }

    fun forget(query: String) {
        _queries.value = store.remove(query)
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
