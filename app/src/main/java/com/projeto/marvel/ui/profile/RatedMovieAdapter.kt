package com.projeto.marvel.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.RatedMovie
import com.projeto.marvel.databinding.ItemComicBinding
import com.projeto.marvel.ui.comics.stars

/** Estante de filmes do Perfil: pôster, nome e a sua avaliação (mesmo card das HQs). */
class RatedMovieAdapter(
    private val cardWidth: Int,
    private val onClick: (RatedMovie) -> Unit
) : ListAdapter<RatedMovie, RatedMovieAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.updateLayoutParams { width = cardWidth }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(movie: RatedMovie) {
            val context = binding.root.context
            binding.title.text = movie.title
            binding.cover.load(movie.posterUrl) { crossfade(true) }
            binding.rating.text = when {
                !movie.watched -> context.getString(R.string.movie_status_want)
                movie.rating == 0 -> context.getString(R.string.movie_watched_unrated)
                else -> stars(movie.rating)
            }
            binding.front.setOnClickListener { onClick(movie) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RatedMovie>() {
        override fun areItemsTheSame(oldItem: RatedMovie, newItem: RatedMovie) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RatedMovie, newItem: RatedMovie) = oldItem == newItem
    }
}
