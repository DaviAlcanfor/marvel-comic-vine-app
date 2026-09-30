package com.projeto.marvel.ui.album

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/** Uma View fixa como item de uma lista (cabeçalho numa grade que recicla o resto). */
class SingleViewAdapter(private val view: View) : RecyclerView.Adapter<SingleViewAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // A mesma View volta sempre: se o RecyclerView já a tinha anexado, solta antes.
        (view.parent as? ViewGroup)?.removeView(view)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit
}
