package com.projeto.marvel.ui.album

import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.ChipGroup
import com.projeto.marvel.R
import com.projeto.marvel.data.TRADE_COST
import com.projeto.marvel.databinding.ItemFilterChipBinding
import com.projeto.marvel.databinding.ViewAlbumHeaderBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.era
import com.projeto.marvel.ui.eraOutline

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
    sortChip.setText(SORT_LABELS.getValue(current().sort))
    sortChip.setOnClickListener { chip ->
        PopupMenu(chip.context, chip).apply {
            SORT_LABELS.forEach { (sort, label) -> menu.add(0, sort.ordinal, sort.ordinal, label) }
            setOnMenuItemClickListener { item ->
                val sort = AlbumSort.entries[item.itemId]
                sortChip.setText(SORT_LABELS.getValue(sort))
                onChange(current().copy(sort = sort))
                true
            }
        }.show()
    }
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

/**
 * Cabeçalho no traço da época (canvas "Épocas dos quadrinhos"): legenda amarela (Retrô), etiqueta
 * azul (Anos 90) ou barra fina (Moderno); busca com "BUSCAR:" escrito (Retrô, Anos 90) ou a lupa (Moderno).
 */
fun ViewAlbumHeaderBinding.applyEra() {
    val context = root.context
    val era = context.era()
    fun color(res: Int) = ContextCompat.getColor(context, res)
    when (era) {
        Era.RETRO -> Unit
        Era.NINETIES -> progress.comicBox(BoxStyle.CAPTION, color(R.color.nineties_extrusion))
        Era.MODERN -> {
            progress.visibility = View.GONE
            progressSpace.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        }
    }
    when (era) {
        Era.MODERN -> {
            searchLabel.visibility = View.GONE
            search.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0)
            search.setHint(R.string.album_search_hint_modern)
        }
        Era.NINETIES -> {
            searchLabel.setTextColor(color(R.color.nineties_outline))
            searchLabel.text = context.getString(R.string.album_search_label).trimEnd(':')
        }
        Era.RETRO -> searchLabel.setTextColor(context.eraOutline())
    }
}


/** ⓘ abre as regras dos pacotes; o selo de troca pede confirmação antes de gastar as repetidas. */
fun ViewAlbumHeaderBinding.bindActions(onTrade: () -> Unit) {
    val context = root.context
    packsInfo.setOnClickListener {
        context.comicDialog()
            .setTitle(R.string.album_packs_info)
            .setMessage(R.string.album_packs_hint)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    tradeButton.setOnClickListener {
        context.comicDialog()
            .setTitle(R.string.album_trade_title)
            .setMessage(context.getString(R.string.album_trade_message, TRADE_COST))
            .setPositiveButton(R.string.album_trade_confirm) { _, _ -> onTrade() }
            .setNegativeButton(R.string.album_trade_cancel, null)
            .show()
    }
}
