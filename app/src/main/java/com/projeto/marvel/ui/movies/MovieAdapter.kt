package com.projeto.marvel.ui.movies

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.data.remote.Movie
import com.projeto.marvel.databinding.ItemComicBinding

/** Pôster com nome e "PG-13 · 121 min" (mesmo card das capas de HQ). */
class MovieAdapter(
    /** Largura fixa para listas horizontais (Detalhe, Início); null = ocupa a coluna da grade. */
    private val cardWidth: Int? = null,
    private val onClick: (Movie) -> Unit
) : ListAdapter<Movie, MovieAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        cardWidth?.let { width -> binding.root.updateLayoutParams { this.width = width } }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(movie: Movie) {
            binding.title.text = movie.name
            binding.cover.load(movie.image?.mediumUrl) { crossfade(true) }
            binding.rating.text = listOfNotNull(
                movie.rating?.takeIf { it.isNotBlank() },
                movie.runtimeLabel
            ).joinToString(" · ")
            binding.root.setOnClickListener { onClick(movie) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Movie, newItem: Movie) = oldItem == newItem
    }
}
