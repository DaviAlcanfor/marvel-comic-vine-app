package com.projeto.marvel.ui.comics

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.databinding.ItemComicBinding

/** Capas de HQ com a nota, na estante do Perfil e na busca (lá, nem todas estão lidas). */
class ComicAdapter(
    /** Largura fixa do card para listas horizontais (Início); null = ocupa a coluna da grade. */
    private val cardWidth: Int? = null,
    /** Estante: toque vira a HQ em 3D (verso com a resenha); "Editar" no verso chama [onClick]. */
    private val flippable: Boolean = false,
    private val onClick: (ComicItem) -> Unit
) : ListAdapter<ComicItem, ComicAdapter.ViewHolder>(Diff) {

    // Quais HQs estão mostrando o verso (sobrevive à reciclagem dos cards).
    private val flipped = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply { applyEra() }
        cardWidth?.let { width -> binding.root.updateLayoutParams { this.width = width } }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ComicItem) {
            binding.title.text = item.comic.title
            binding.cover.load(item.comic.coverUrl) { crossfade(true) }
            val context = binding.root.context
            binding.rating.text = when {
                !item.onShelf -> ""
                item.comic.status == ReadingStatus.WANT_TO_READ -> context.getString(R.string.reading_status_want)
                item.comic.status == ReadingStatus.READING -> context.getString(R.string.reading_status_reading)
                item.comic.rating == 0 -> context.getString(R.string.comics_unrated)
                else -> stars(item.comic.rating)
            }
            binding.back.visibility = View.GONE
            binding.front.visibility = View.VISIBLE
            binding.root.rotationY = 0f
            if (flippable && item.onShelf) bindBack(item) else binding.front.setOnClickListener { onClick(item) }
        }

        private fun bindBack(item: ComicItem) {
            val context = binding.root.context
            binding.backRating.text = binding.rating.text
            binding.backReview.text = item.comic.review ?: context.getString(R.string.comics_no_review)
            binding.backEdit.setOnClickListener { onClick(item) }
            showSide(item.comic.id in flipped)
            val flip = View.OnClickListener { flip(item.comic.id) }
            binding.front.setOnClickListener(flip)
            binding.back.setOnClickListener(flip)
        }

        /** Meia volta até ficar de lado, troca a face e completa a volta (3D das Views). */
        private fun flip(id: Int) {
            val toBack = id !in flipped
            if (toBack) flipped += id else flipped -= id
            val card = binding.root
            card.cameraDistance = FLIP_CAMERA_DISTANCE * card.resources.displayMetrics.density
            card.animate().rotationY(QUARTER_TURN).setDuration(FLIP_HALF_MILLIS).withEndAction {
                showSide(toBack)
                card.rotationY = -QUARTER_TURN
                card.animate().rotationY(0f).setDuration(FLIP_HALF_MILLIS)
            }
        }

        private fun showSide(back: Boolean) {
            binding.back.visibility = if (back) View.VISIBLE else View.GONE
            binding.front.visibility = if (back) View.INVISIBLE else View.VISIBLE
        }
    }

    private object Diff : DiffUtil.ItemCallback<ComicItem>() {
        override fun areItemsTheSame(oldItem: ComicItem, newItem: ComicItem) = oldItem.comic.id == newItem.comic.id
        override fun areContentsTheSame(oldItem: ComicItem, newItem: ComicItem) = oldItem == newItem
    }
}

/** [onShelf]: já está na estante (com algum status); na busca, nem todas estão. */
data class ComicItem(val comic: ReadComic, val onShelf: Boolean)

const val MAX_STARS = 5

private const val FLIP_CAMERA_DISTANCE = 8000f
private const val FLIP_HALF_MILLIS = 160L
private const val QUARTER_TURN = 90f

fun stars(rating: Int) = "★".repeat(rating) + "☆".repeat(MAX_STARS - rating)
