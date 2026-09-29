package com.projeto.marvel.ui.album

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemStickerBinding

private const val FRAME_DP = 3

/** Moldura na cor da raridade; sem a figurinha, só a moldura e "?". */
fun ItemStickerBinding.bind(sticker: Sticker) {
    val context = root.context
    val owned = sticker.count > 0
    val density = context.resources.displayMetrics.density
    root.background = GradientDrawable().apply {
        cornerRadius = context.resources.getDimension(R.dimen.radius_small)
        setColor(ContextCompat.getColor(context, R.color.surface_variant))
        val frame = if (sticker.golden) R.color.card_legendary_light else sticker.rarity.frameColor()
        setStroke((FRAME_DP * density * if (sticker.golden) 2 else 1).toInt(), ContextCompat.getColor(context, frame))
    }
    image.visibility = if (owned) View.VISIBLE else View.INVISIBLE
    missing.visibility = if (owned) View.GONE else View.VISIBLE
    if (owned) image.load(sticker.character.image?.mediumUrl) { crossfade(true) } else image.setImageDrawable(null)
    name.text = when {
        !owned -> "?"
        sticker.golden -> context.getString(R.string.battle_golden_name, sticker.character.name)
        else -> sticker.character.name
    }
    name.setTextColor(ContextCompat.getColor(context, if (sticker.golden) R.color.accent else R.color.text_primary))
    number.text = context.getString(R.string.album_number, sticker.number)
    count.visibility = if (sticker.count > 1) View.VISIBLE else View.GONE
    count.text = context.getString(R.string.album_count, sticker.count)
    root.contentDescription = if (owned) {
        context.getString(R.string.album_sticker_description, sticker.character.name, sticker.count)
    } else {
        context.getString(R.string.album_missing)
    }
}

/** Grade do álbum; tocar numa figurinha que você tem abre ela grande em 3D ([onOpen]). */
class StickerAdapter(private val onOpen: (Sticker) -> Unit) : ListAdapter<Sticker, StickerAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemStickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sticker = getItem(position)
        holder.binding.bind(sticker)
        holder.binding.root.isClickable = sticker.count > 0
        holder.binding.root.setOnClickListener { if (sticker.count > 0) onOpen(sticker) }
    }

    class ViewHolder(val binding: ItemStickerBinding) : RecyclerView.ViewHolder(binding.root)

    private object Diff : DiffUtil.ItemCallback<Sticker>() {
        override fun areItemsTheSame(oldItem: Sticker, newItem: Sticker) = oldItem.character.id == newItem.character.id
        override fun areContentsTheSame(oldItem: Sticker, newItem: Sticker) = oldItem == newItem
    }
}
