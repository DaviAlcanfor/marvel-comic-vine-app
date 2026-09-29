package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit

/**
 * Histórico de buscas de uma tela ([scope]: personagens, criadores, filmes, HQs), salvo no
 * aparelho via SharedPreferences (mais recente primeiro).
 */
class RecentSearchStore(context: Context, scope: String = SCOPE_CHARACTERS) {

    // Personagens usa o arquivo antigo ("recent_searches"): quem já tinha histórico não o perde.
    private val prefs = context.getSharedPreferences(
        if (scope == SCOPE_CHARACTERS) PREFS_NAME else "${PREFS_NAME}_$scope",
        Context.MODE_PRIVATE
    )

    fun get(): List<String> = prefs.getString(KEY_QUERIES, null)?.split(SEPARATOR).orEmpty()

    fun add(query: String) = save(get().withRecent(query))

    fun remove(query: String) = save(get() - query)

    private fun save(queries: List<String>): List<String> {
        prefs.edit { putString(KEY_QUERIES, queries.joinToString(SEPARATOR).ifEmpty { null }) }
        return queries
    }

    companion object {
        const val SCOPE_CHARACTERS = "characters"
        const val SCOPE_CREATORS = "creators"
        const val SCOPE_MOVIES = "movies"
        const val SCOPE_COMICS = "comics"
        private const val PREFS_NAME = "recent_searches"
        private const val KEY_QUERIES = "queries"
        private const val SEPARATOR = "\n" // a busca é uma linha só, então não aparece dentro de uma
    }
}

private const val MAX_RECENT = 5

/**
 * [query] vai para o topo; repetida (ignorando maiúsculas) não duplica; pedaços dela digitados
 * antes ("spi" antes de "spider") saem; guarda só [MAX_RECENT].
 */
internal fun List<String>.withRecent(query: String) =
    (listOf(query) + filterNot { query.startsWith(it, ignoreCase = true) }).take(MAX_RECENT)
