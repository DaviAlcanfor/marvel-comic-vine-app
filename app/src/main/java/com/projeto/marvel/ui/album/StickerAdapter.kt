package com.projeto.marvel.ui.album

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemStickerBinding
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.EraPanelDrawable
import com.projeto.marvel.ui.era

private const val FOIL_DP = 3
private const val MODERN_RADIUS_DP = 6f

/**
 * Figurinha da grade no traço da época: margem branca de nanquim com sombra dura (Retrô), borda
 * holográfica (Anos 90) ou moldura fosca arredondada (Moderno). A Divina tem sempre o brilho por cima.
 */
fun ItemStickerBinding.bind(sticker: Sticker) {
    val context = root.context
    val owned = sticker.count > 0
    val era = context.era()
    fun color(res: Int) = ContextCompat.getColor(context, res)
    root.background = frame(context, era)
    when (era) {
        Era.RETRO -> number.styleNumber(color(R.color.ink), color(R.color.logo_yellow))
        Era.NINETIES -> number.styleNumber(color(R.color.logo_yellow), color(R.color.nineties_background))
        Era.MODERN -> number.styleNumber(color(R.color.modern_number), color(R.color.white))
    }
    name.setTextColor(color(if (era == Era.NINETIES) R.color.logo_yellow else R.color.text_primary))
    name.isAllCaps = era != Era.MODERN
    image.visibility = if (owned) View.VISIBLE else View.INVISIBLE
    missing.visibility = if (owned) View.GONE else View.VISIBLE
    if (owned) image.load(sticker.character.image?.mediumUrl) { crossfade(true) } else image.setImageDrawable(null)
    shine.visibility = if (owned && sticker.golden) View.VISIBLE else View.GONE
    shine.compact = true
    shine.setRarity(sticker.rarity, sticker.golden)
    name.text = if (owned) rarityBadge(context, sticker.rarity, sticker.golden, sticker.character.name) else "?"
    number.text = context.getString(R.string.album_number, sticker.number)
    count.visibility = if (sticker.count > 1) View.VISIBLE else View.GONE
    count.text = context.getString(R.string.album_count, sticker.count)
    root.contentDescription = if (owned) {
        context.getString(R.string.album_sticker_description, sticker.character.name, sticker.count)
    } else {
        context.getString(R.string.album_missing)
    }
}

private fun frame(context: Context, era: Era): Drawable = when (era) {
    Era.RETRO -> EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL, ContextCompat.getColor(context, R.color.white))
    Era.NINETIES -> {
        val foil = GradientDrawable().apply {
            gradientType = GradientDrawable.SWEEP_GRADIENT
            colors = FOIL.map { ContextCompat.getColor(context, it) }.toIntArray()
        }
        val inside = GradientDrawable().apply { setColor(ContextCompat.getColor(context, R.color.nineties_card)) }
        val edge = (FOIL_DP * context.resources.displayMetrics.density).toInt()
        LayerDrawable(arrayOf(foil, inside)).apply { setLayerInset(1, edge, edge, edge, edge) }
    }
    Era.MODERN -> GradientDrawable().apply {
        val density = context.resources.displayMetrics.density
        cornerRadius = MODERN_RADIUS_DP * density
        setColor(ContextCompat.getColor(context, R.color.modern_card))
        setStroke(density.toInt(), ContextCompat.getColor(context, R.color.modern_card_line))
    }
}

private val FOIL = listOf(
    R.color.nineties_outline,
    R.color.nineties_magenta,
    R.color.logo_yellow,
    R.color.foil_green,
    R.color.nineties_outline
)

private fun TextView.styleNumber(background: Int, text: Int) {
    setBackgroundColor(background)
    setTextColor(text)
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
