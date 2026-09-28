package com.projeto.marvel.ui

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/** Chama [action] quando a lista não consegue mais rolar para baixo (paginação infinita). */
fun RecyclerView.onEndReached(action: () -> Unit) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy > 0 && !recyclerView.canScrollVertically(1)) action()
        }
    })
}

/**
 * Entrega a lista ao adapter; a cascata de entrada só roda para uma lista nova (outra busca),
 * não quando a mesma lista ganha mais uma página no fim.
 */
fun <T> ListAdapter<T, *>.submitAnimated(list: List<T>, recyclerView: RecyclerView) {
    val isNewList = currentList.firstOrNull() != list.firstOrNull()
    submitList(list) { if (isNewList) recyclerView.animateItemsIn() }
}
