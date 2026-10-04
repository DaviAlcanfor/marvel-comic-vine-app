package com.projeto.marvel.ui.album

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
    dress(era, owned)
    image.visibility = if (owned) View.VISIBLE else View.INVISIBLE
    missing.visibility = if (owned) View.GONE else View.VISIBLE
    if (owned) image.load(sticker.character.image?.mediumUrl) { crossfade(true) } else image.setImageDrawable(null)
    shine.visibility = if (owned && sticker.golden) View.VISIBLE else View.GONE
    shine.compact = true
    shine.setRarity(sticker.rarity, sticker.golden)
    name.text = stickerName(context, sticker, era, owned)
    number.text = context.getString(R.string.album_number, sticker.number)
    count.visibility = if (sticker.count > 1) View.VISIBLE else View.GONE
    count.text = context.getString(R.string.album_count, sticker.count)
    root.contentDescription = if (owned) {
        context.getString(R.string.album_sticker_description, sticker.character.name, sticker.count)
    } else {
        context.getString(R.string.album_missing)
    }
}

/** Moldura, placa do número, letra do nome e o vazio da que falta, no traço da época. */
private fun ItemStickerBinding.dress(era: Era, owned: Boolean) {
    val context = root.context
    fun color(res: Int) = ContextCompat.getColor(context, res)
    root.background = frame(context, era)
    when (era) {
        Era.RETRO -> number.styleNumber(color(R.color.ink), color(R.color.logo_yellow))
        Era.NINETIES -> number.styleNumber(color(R.color.logo_yellow), color(R.color.nineties_background))
        Era.MODERN -> number.styleNumber(color(R.color.modern_number), color(R.color.white))
    }
    // Retrô: a figurinha é papel branco (no claro e no escuro), então o nome é sempre nanquim.
    name.setTextColor(
        color(
            when (era) {
                Era.RETRO -> R.color.ink
                Era.NINETIES -> R.color.logo_yellow
                Era.MODERN -> R.color.text_primary
            }
        )
    )
    name.isAllCaps = era != Era.MODERN
    missing.background = when (era) {
        Era.RETRO -> dashedBox(context)
        Era.NINETIES -> ContextCompat.getDrawable(context, R.drawable.bg_missing_stripes)
        Era.MODERN -> null
    }
    image.foreground = if (era == Era.RETRO && owned) inkLine(context) else null
}

private fun stickerName(context: Context, sticker: Sticker, era: Era, owned: Boolean): CharSequence = when {
    owned && era == Era.MODERN -> rarityBadge(context, sticker.rarity, sticker.golden, sticker.character.name)
    owned -> eraBadge(context, sticker, era)
    era == Era.MODERN -> context.getString(R.string.album_missing_short)
    else -> MISSING_NAME
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

/** Estrelas na tinta da época (vermelho no Retrô, amarelo nos Anos 90) antes do nome; ✦ na Divina. */
private fun eraBadge(context: Context, sticker: Sticker, era: Era): CharSequence {
    val ink = ContextCompat.getColor(context, if (era == Era.RETRO) R.color.primary else R.color.logo_yellow)
    val stars = (if (sticker.golden) "✦" else "") + "★".repeat(sticker.rarity.ordinal + 1)
    return SpannableStringBuilder()
        .append(stars, ForegroundColorSpan(ink), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        .append(" ")
        .append(sticker.character.name)
}

/** Figurinha que falta no Retrô: caixa tracejada, como o espaço vazio de um álbum de banca. */
private fun dashedBox(context: Context) = GradientDrawable().apply {
    val density = context.resources.displayMetrics.density
    val dash = DASH_DP * density
    setStroke((DASH_WIDTH_DP * density).toInt(), ContextCompat.getColor(context, R.color.missing_ink), dash, dash)
}

/** Filete de nanquim em volta da foto (Retrô). */
private fun inkLine(context: Context) = GradientDrawable().apply {
    val density = context.resources.displayMetrics.density
    setStroke((INK_LINE_DP * density).toInt(), ContextCompat.getColor(context, R.color.ink))
}

private const val MISSING_NAME = "???"
private const val DASH_WIDTH_DP = 2f
private const val DASH_DP = 5f
private const val INK_LINE_DP = 1.5f

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
