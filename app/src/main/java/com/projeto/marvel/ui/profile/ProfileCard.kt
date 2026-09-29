package com.projeto.marvel.ui.profile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import coil.imageLoader
import coil.request.ImageRequest
import com.projeto.marvel.R
import com.projeto.marvel.data.Achievement
import com.projeto.marvel.databinding.ViewProfileCardBinding
import com.projeto.marvel.ui.photo.shareImage

private const val CARD_WIDTH_DP = 360
private const val MAX_MEDALS = 6

/**
 * Monta o cartão do perfil fora da tela (herói favorito, placar, HQs, gêneros e medalhas) e abre o
 * compartilhar. A foto é baixada antes, num bitmap comum: o desenho em bitmap não espera o Coil.
 */
suspend fun shareProfileCard(context: Context, state: ProfileUiState) {
    val card = ViewProfileCardBinding.inflate(LayoutInflater.from(context))
    val hero = state.preferences.hero
    hero?.imageUrl?.let { url ->
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.let { card.heroImage.setImageDrawable(it) }
    }
    card.heroImage.visibility = if (hero?.imageUrl != null) View.VISIBLE else View.GONE
    card.userName.text = state.user?.name?.takeIf { it.isNotBlank() } ?: context.getString(R.string.profile_title)
    card.heroName.text = hero?.let { context.getString(R.string.profile_card_hero, it.name) }.orEmpty()
    val unlocked = Achievement.entries.filter { it.unlocked(state.progress) }
    card.stats.text = context.getString(
        R.string.profile_card_stats,
        state.read.size,
        state.record.wins,
        state.record.losses,
        unlocked.size,
        Achievement.entries.size
    )
    card.genres.text = state.preferences.genres.joinToString(" · ")
    bindMedals(card.medals, unlocked.take(MAX_MEDALS))

    val width = (CARD_WIDTH_DP * context.resources.displayMetrics.density).toInt()
    card.root.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    card.root.layout(0, 0, card.root.measuredWidth, card.root.measuredHeight)
    shareImage(context, card.root.drawToBitmap(), context.getString(R.string.profile_card_share))
}

private fun bindMedals(row: LinearLayout, medals: List<Achievement>) {
    val context = row.context
    val size = context.resources.getDimensionPixelSize(R.dimen.medal_size)
    val padding = context.resources.getDimensionPixelSize(R.dimen.space_sm)
    medals.forEach { achievement ->
        row.addView(
            ImageView(context).apply {
                setBackgroundResource(R.drawable.bg_medal)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, achievement.color))
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ink))
                setImageResource(achievement.icon)
                setPadding(padding, padding, padding, padding)
            },
            LinearLayout.LayoutParams(size, size).apply { marginEnd = padding }
        )
    }
}
