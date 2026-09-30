package com.projeto.marvel.ui.album

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Rarity
import com.projeto.marvel.databinding.ItemTradingCardBinding

// Carta grande estilo trading card (abertura do pacote e visão 3D): moldura metalizada da
// raridade, arte grande, holográfico. A grade do álbum usa a figurinha simples (StickerAdapter).

/** Metal da moldura (claro → escuro) e cor da plaquinha, por raridade. */
private fun Rarity.metal(): Pair<Int, Int> = when (this) {
    Rarity.COMMON -> R.color.card_common_light to R.color.card_common_dark
    Rarity.RARE -> R.color.card_rare_light to R.color.card_rare_dark
    Rarity.LEGENDARY -> R.color.card_legendary_light to R.color.card_legendary_dark
}

/** Cor da moldura simples da grade do álbum. */
@ColorRes
fun Rarity.frameColor() = when (this) {
    Rarity.COMMON -> R.color.text_secondary
    Rarity.RARE -> R.color.move_water
    Rarity.LEGENDARY -> R.color.accent
}

/** Selo do canto: "✦ DOURADA" na dourada revelada, senão as estrelas da raridade. */
private fun Sticker.badge(context: Context, revealed: Boolean) =
    if (golden && revealed) context.getString(R.string.album_golden_badge) else "★".repeat(rarity.ordinal + 1)

/** Cor da aura atrás da carta em 3D. */
@ColorRes
fun Rarity.auraColor() = when (this) {
    Rarity.COMMON -> R.color.card_common_light
    Rarity.RARE -> R.color.card_rare_light
    Rarity.LEGENDARY -> R.color.card_legendary_light
}

/** Metal da carta do lutador: a dourada é sempre ouro; as outras, o da raridade. */
val Fighter.cardMetal get() = if (golden) Rarity.LEGENDARY else rarity

/** Moldura redonda do lutador (arena, prévia): metal da carta com contorno de nanquim. */
fun Fighter.rarityRing(context: Context) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(ContextCompat.getColor(context, cardMetal.metal().first))
    setStroke(context.resources.getDimensionPixelSize(R.dimen.ink_width), ContextCompat.getColor(context, R.color.ink))
}

/** Espessura da moldura: a dourada é mais grossa. */
fun Fighter.ringPadding(context: Context) =
    context.resources.getDimensionPixelSize(if (golden) R.dimen.rarity_ring_golden else R.dimen.rarity_ring)

/** Selo antes do nome, como no álbum: estrelas da raridade, ou "✦" na dourada. */
fun Fighter.badged(name: String) = (if (golden) GOLDEN_MARK else STAR.repeat(rarity.ordinal + 1)) + " " + name

private const val GOLDEN_MARK = "✦"
private const val STAR = "★"

/** Metal da raridade com contorno de nanquim: frente da carta e o verso da carta em 3D. */
fun Rarity.metalBackground(context: Context): GradientDrawable {
    val (lightRes, darkRes) = metal()
    val light = ContextCompat.getColor(context, lightRes)
    val dark = ContextCompat.getColor(context, darkRes)
    val ink = ContextCompat.getColor(context, R.color.ink)
    return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(light, dark, light)).apply {
        cornerRadius = context.resources.getDimension(R.dimen.radius_small)
        setStroke(context.resources.getDimensionPixelSize(R.dimen.ink_width), ink)
    }
}

private const val MISSING_ALPHA = 0.55f
private const val HOLO_TRAVEL = 6f

/**
 * [revealed] = mostra a arte (no álbum, só se tiver a figurinha); [large] = arte em alta (abertura
 * do pacote). Sem a figurinha: moldura apagada e o número grande no lugar da arte.
 */
fun ItemTradingCardBinding.bind(sticker: Sticker, revealed: Boolean = sticker.count > 0, large: Boolean = false) {
    val context = root.context
    val metal = if (sticker.golden) Rarity.LEGENDARY else sticker.rarity
    root.background = metal.metalBackground(context)
    root.alpha = if (revealed) 1f else MISSING_ALPHA
    plate.setBackgroundColor(ContextCompat.getColor(context, metal.metal().first))
    val number = context.getString(R.string.album_number, sticker.number)
    this.number.text = number
    stars.text = sticker.badge(context, revealed)
    image.visibility = if (revealed) View.VISIBLE else View.INVISIBLE
    missing.visibility = if (revealed) View.GONE else View.VISIBLE
    missing.text = number
    holo.visibility = if (revealed && (sticker.golden || sticker.rarity != Rarity.COMMON)) View.VISIBLE else View.GONE
    val art = sticker.character.image
    val artUrl = if (large) art?.originalUrl else art?.mediumUrl
    if (revealed) image.load(artUrl) { crossfade(true) } else image.setImageDrawable(null)
    name.text = if (revealed) sticker.character.name else "?"
    realName.text = if (revealed) sticker.character.realName.orEmpty() else ""
    count.visibility = if (sticker.count > 1) View.VISIBLE else View.GONE
    count.text = context.getString(R.string.album_count, sticker.count)
    root.contentDescription = if (revealed) {
        context.getString(R.string.album_sticker_description, sticker.character.name, sticker.count)
    } else {
        context.getString(R.string.album_missing)
    }
}

/**
 * Reflexo holográfico correndo com a inclinação do celular, como carta de verdade. (A rotação
 * fica no grupo de cartas: a de cada carta é da animação de virar.)
 */
fun ItemTradingCardBinding.tilt(pitch: Float, roll: Float) {
    // A faixa é 3× maior que a carta: andando no máximo metade da carta, a borda dela nunca aparece.
    val limit = root.width / 2f
    val density = root.resources.displayMetrics.density
    holo.translationX = (roll * HOLO_TRAVEL * density).coerceIn(-limit, limit)
    holo.translationY = (pitch * HOLO_TRAVEL * density).coerceIn(-limit, limit)
}
