package com.projeto.marvel.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import com.google.android.material.chip.ChipGroup
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemRecentSearchBinding

/**
 * Histórico de buscas como chips embaixo do campo (só com o campo vazio). Toque refaz a busca;
 * o X tira do histórico. Usado em todas as telas com busca.
 */
fun bindRecentSearches(
    scroll: View,
    group: ChipGroup,
    input: EditText,
    queries: List<String>,
    onRemove: (String) -> Unit
) {
    scroll.isVisible = queries.isNotEmpty() && input.text.isNullOrBlank()
    group.removeAllViews()
    val inflater = LayoutInflater.from(group.context)
    queries.forEach { query ->
        ItemRecentSearchBinding.inflate(inflater, group, true).root.apply {
            text = query
            closeIconContentDescription = context.getString(R.string.home_recent_remove, query)
            setOnClickListener {
                input.setText(query)
                input.setSelection(query.length)
            }
            setOnCloseIconClickListener { onRemove(query) }
        }
    }
}
