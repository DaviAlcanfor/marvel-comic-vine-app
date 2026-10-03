package com.projeto.marvel.ui.album

import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.ChipGroup
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemFilterChipBinding
import com.projeto.marvel.databinding.ViewAlbumHeaderBinding

private val FILTER_LABELS = mapOf(
    AlbumFilter.ALL to R.string.album_filter_all,
    AlbumFilter.OWNED to R.string.album_filter_owned,
    AlbumFilter.MISSING to R.string.album_filter_missing,
    AlbumFilter.LEGENDARY to R.string.album_filter_legendary,
    AlbumFilter.RARE to R.string.album_filter_rare,
    AlbumFilter.COMMON to R.string.album_filter_common,
    AlbumFilter.DOUBLES to R.string.album_filter_doubles,
    AlbumFilter.TRIPLES to R.string.album_filter_triples,
    AlbumFilter.EVOLVE to R.string.album_filter_evolve,
    AlbumFilter.DIVINE to R.string.album_filter_divine
)

private val SORT_LABELS = mapOf(
    AlbumSort.NUMBER to R.string.album_sort_number,
    AlbumSort.NAME to R.string.album_sort_name,
    AlbumSort.COPIES to R.string.album_sort_copies
)

/** Busca, chips de filtro e de ordem do cabeçalho; cada mudança vira um [AlbumQuery] novo. */
fun ViewAlbumHeaderBinding.bindFilters(
    inflater: LayoutInflater,
    current: () -> AlbumQuery,
    onChange: (AlbumQuery) -> Unit
) {
    search.setText(current().text)
    search.doAfterTextChanged { onChange(current().copy(text = it?.toString().orEmpty())) }
    search.setOnEditorActionListener { input, actionId, _ ->
        if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
        ViewCompat.getWindowInsetsController(input)?.hide(WindowInsetsCompat.Type.ime())
        true
    }
    filterGroup.chips(inflater, FILTER_LABELS, current().filter) { onChange(current().copy(filter = it)) }
    sortGroup.chips(inflater, SORT_LABELS, current().sort) { onChange(current().copy(sort = it)) }
}

private fun <T> ChipGroup.chips(inflater: LayoutInflater, labels: Map<T, Int>, selected: T, onPick: (T) -> Unit) {
    removeAllViews()
    labels.forEach { (value, label) ->
        ItemFilterChipBinding.inflate(inflater, this, true).root.apply {
            id = View.generateViewId()
            setText(label)
            isChecked = value == selected
            setOnClickListener { onPick(value) }
        }
    }
}

/** Manda para a grade só o que passa no filtro, com o contador ("12 de 150") quando filtrado. */
fun ViewAlbumHeaderBinding.submit(adapter: StickerAdapter, stickers: List<Sticker>, query: AlbumQuery) {
    val shown = stickers.filtered(query)
    val context = root.context
    filterCount.isVisible = query.active
    filterCount.text = if (shown.isEmpty()) {
        context.getString(R.string.album_filter_empty)
    } else {
        context.getString(R.string.album_filter_count, shown.size, stickers.size)
    }
    adapter.submitList(shown)
}
