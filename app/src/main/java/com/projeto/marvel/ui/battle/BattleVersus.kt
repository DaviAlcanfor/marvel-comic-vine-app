package com.projeto.marvel.ui.battle

import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import coil.transform.CircleCropTransformation
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentBattleBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.BurstDrawable
import com.projeto.marvel.ui.album.badged
import com.projeto.marvel.ui.album.cardMetal
import com.projeto.marvel.ui.album.metalBackground
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.compare.bindComparison
import com.projeto.marvel.ui.compare.fighterRows
import com.projeto.marvel.ui.staggerIn

// Tela de VS antes de cada luta: lados entram, "VS" carimba, atributos lado a lado, "LUTAR!".

private const val SLIDE_MILLIS = 420L
private const val STAMP_DELAY_MILLIS = 380L
private const val STAMP_MILLIS = 360L
private const val STAMP_START_SCALE = 3f
private const val STAMP_TILT = -12f
private const val BOIL_FRAMES = 6
private const val BOIL_FRAME_MILLIS = 90L
private const val FADE_MILLIS = 220L

fun FragmentBattleBinding.showVersus(game: BattleUiState.Success) {
    val v = versus
    val context = root.context
    val stage = game.stage
    v.versusTitle.text = when {
        game.pvp -> context.getString(R.string.battle_pvp_label)
        game.playerBench.isNotEmpty() -> context.getString(R.string.versus_squads)
        stage == null -> context.getString(R.string.versus_title)
        stage.isLast -> context.getString(R.string.battle_stage_boss, stage.number, stage.total)
        else -> context.getString(R.string.battle_stage, stage.number, stage.total)
    }
    v.versusLeftImage.load(game.player.fighter.imageUrl) { crossfade(true) }
    v.versusRightImage.load(game.cpu.fighter.imageUrl) { crossfade(true) }
    v.versusLeftName.text = game.player.fighter.badged(v.root.context, game.player.fighter.name)
    v.versusRightName.text = game.cpu.fighter.badged(v.root.context, game.cpu.fighter.name)
    // Moldura da carta na raridade (a Divina em ouro), como no álbum.
    val ring = v.root.resources.getDimensionPixelSize(R.dimen.rarity_ring)
    val portraits = listOf(v.versusLeftImage to game.player.fighter, v.versusRightImage to game.cpu.fighter)
    portraits.forEach { (image, fighter) ->
        image.background = fighter.cardMetal.metalBackground(v.root.context)
        image.setPadding(ring, ring, ring, ring)
    }
    v.versusLeftSquad.bindSquad(game.playerBench)
    v.versusRightSquad.bindSquad(game.cpuBench)
    bindComparison(v.versusRows, fighterRows(game.player.fighter, game.cpu.fighter))
    v.versusBadge.comicBox(BoxStyle.BURST, ContextCompat.getColor(context, R.color.primary))
    v.versusFight.setOnClickListener {
        v.versusRoot.animate().alpha(0f).setDuration(FADE_MILLIS).withEndAction { v.versusRoot.visibility = View.GONE }
    }

    v.versusRoot.animate().cancel()
    v.versusRoot.alpha = 1f
    v.versusRoot.visibility = View.VISIBLE
    val width = root.width.toFloat().takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels.toFloat()
    listOf(v.versusLeft to -width, v.versusRight to width).forEach { (side, from) ->
        side.translationX = from
        side.animate().translationX(0f).setDuration(SLIDE_MILLIS).setInterpolator(OvershootInterpolator())
    }
    stamp(v.versusBadge)
    staggerIn((0 until v.versusRows.childCount).map(v.versusRows::getChildAt) + v.versusFight)
}

/** Os outros dois do trio: foto redonda e nome embaixo, lado a lado. */
private fun LinearLayout.bindSquad(bench: List<Combatant>) {
    visibility = if (bench.isEmpty()) View.GONE else View.VISIBLE
    removeAllViews()
    val size = resources.getDimensionPixelSize(R.dimen.versus_squad_portrait)
    val gap = resources.getDimensionPixelSize(R.dimen.space_xs)
    bench.forEach { combatant ->
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        item.addView(
            ImageView(context).apply {
                contentDescription = combatant.fighter.name
                load(combatant.fighter.imageUrl) { transformations(CircleCropTransformation()) }
            },
            LinearLayout.LayoutParams(size, size)
        )
        item.addView(
            TextView(context).apply {
                text = combatant.fighter.name
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = size + gap * 2
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = SQUAD_NAME_SP
            }
        )
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        addView(
            item,
            LinearLayout.LayoutParams(wrap, wrap).apply {
                marginStart = gap
                marginEnd = gap
            }
        )
    }
}

private const val SQUAD_NAME_SP = 10f

/** O "VS" cai grande e girado, bate no lugar (vibra) e o contorno ferve por uns quadros. */
private fun stamp(badge: View) {
    badge.scaleX = STAMP_START_SCALE
    badge.scaleY = STAMP_START_SCALE
    badge.alpha = 0f
    badge.rotation = 0f
    badge.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(STAMP_TILT)
        .setStartDelay(STAMP_DELAY_MILLIS).setDuration(STAMP_MILLIS)
        .setInterpolator(OvershootInterpolator())
        .withEndAction {
            badge.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val burst = badge.background as? BurstDrawable ?: return@withEndAction
            repeat(BOIL_FRAMES) { frame -> badge.postDelayed({ burst.boil() }, frame * BOIL_FRAME_MILLIS) }
        }
}
