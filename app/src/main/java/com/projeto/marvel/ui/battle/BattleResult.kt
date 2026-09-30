package com.projeto.marvel.ui.battle

import androidx.annotation.DrawableRes
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentBattleBinding
import com.projeto.marvel.databinding.ItemResultStatBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicInterpolator
import com.projeto.marvel.ui.photo.shareImage
import com.projeto.marvel.ui.profile.title

// Fim da luta: painel de vitória/derrota (troféu 3D, confete, resumo animado) e compartilhar.

private const val ROW_STAGGER_MILLIS = 180L
private const val COUNT_MILLIS = 900L
private const val JUMP_MILLIS = 900L
private const val JUMP_HEIGHT_FACTOR = 3
private const val KO_DELAY_MILLIS = 1_100L

/**
 * Fim de luta: o derrotado esmaece, o K.O. estoura e entra o painel — troféu 3D e confete na
 * vitória, e o resumo em linhas com contador que sobe, uma depois da outra.
 */
fun FragmentBattleBinding.showResult(game: BattleUiState.Success) {
    val context = root.context
    (if (game.winner == Side.PLAYER) cpuImage else playerImage).defeat()
    val koColor = ContextCompat.getColor(context, R.color.primary)
    koBurst.burst(context.getString(R.string.battle_ko), koColor, KO_DELAY_MILLIS)
    // No PvP sempre há um vencedor para comemorar.
    val won = game.winner == Side.PLAYER || game.pvp
    resultTitle.text = when {
        game.pvp -> context.getString(R.string.battle_pvp_winner, game.winner?.let(game::combatant)?.fighter?.name)
        won -> context.getString(R.string.battle_victory)
        else -> context.getString(R.string.battle_defeat)
    }
    resultVersus.text = context.getString(R.string.result_versus, game.player.fighter.name, game.cpu.fighter.name)
    shareResultButton.setOnClickListener { shareResult() }
    resultAchievements.text = game.newAchievements.joinToString("\n") {
        "★ " + context.getString(R.string.achievement_unlocked, context.getString(it.title))
    }
    resultAchievements.visibility = if (game.newAchievements.isEmpty()) View.GONE else View.VISIBLE
    // Cara de HQ: título numa explosão torta, confronto numa legenda, conquistas num balão.
    val titleColor = if (won) R.color.accent else R.color.primary
    resultTitle.comicBox(BoxStyle.BURST, ContextCompat.getColor(context, titleColor))
    resultTitle.rotation = TITLE_TILT
    resultVersus.comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, R.color.accent))
    resultVersus.rotation = -TITLE_TILT / 2
    resultAchievements.comicBox(BoxStyle.SPEECH, ContextCompat.getColor(context, R.color.white))
    resultPanel.alpha = 0f
    resultPanel.visibility = View.VISIBLE
    resultPanel.animate().alpha(1f).setStartDelay(RESULT_DELAY_MILLIS).setDuration(RESULT_MILLIS)
    bindResultRows(game)
    (if (game.winner == Side.PLAYER) playerImage else cpuImage).victoryJump()
    root.performHapticFeedback(if (won) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT)
    resultTrophy.visibility = if (won) View.VISIBLE else View.GONE
    if (won) {
        resultTrophy.start()
        confetti.burst(delayMillis = RESULT_DELAY_MILLIS)
    }
}

/**
 * Imagem do painel para compartilhar. A TextureView do troféu não sai num "print" comum da View,
 * então o quadro atual dela é desenhado por cima, na mesma posição. O botão não entra na imagem.
 */
private fun FragmentBattleBinding.shareResult() {
    shareResultButton.visibility = View.INVISIBLE
    val image = resultPanel.drawToBitmap()
    shareResultButton.visibility = View.VISIBLE
    if (resultTrophy.visibility == View.VISIBLE) {
        val trophy = resultTrophy.bitmap
        // O troféu fica dentro do card: a posição vem da tela, relativa ao painel.
        val (panel, cup) = IntArray(2) to IntArray(2)
        resultPanel.getLocationInWindow(panel)
        resultTrophy.getLocationInWindow(cup)
        val (x, y) = (cup[0] - panel[0]).toFloat() to (cup[1] - panel[1]).toFloat()
        if (trophy != null) Canvas(image).drawBitmap(trophy, x, y, null)
    }
    shareImage(root.context, image, root.context.getString(R.string.result_share_title))
}

fun FragmentBattleBinding.hideResult() {
    resultPanel.animate().cancel()
    resultPanel.visibility = View.GONE
    resultTrophy.stop()
    confetti.stop()
}

/** Os números da luta, 3 por fileira, no padrão dos números do Perfil. */
private fun FragmentBattleBinding.bindResultRows(game: BattleUiState.Success) {
    val stats = game.stats
    val rows = listOf(
        ResultRow(R.string.result_dealt, stats.dealt, R.drawable.ic_move_strike),
        ResultRow(R.string.result_taken, stats.taken, R.drawable.ic_move_heal),
        ResultRow(R.string.result_biggest, stats.biggestHit, R.drawable.ic_swords),
        ResultRow(R.string.result_turns, game.turn, R.drawable.ic_timer),
        ResultRow(R.string.result_criticals, stats.criticals, R.drawable.ic_bolt),
        ResultRow(R.string.result_ultimates, stats.ultimates, R.drawable.ic_move_magic)
    )
    resultRows.removeAllViews()
    val context = root.context
    val inflater = LayoutInflater.from(context)
    rows.chunked(TILES_PER_ROW).forEachIndexed { rowIndex, line ->
        val lineView = LinearLayout(context).apply { clipChildren = false }
        resultRows.addView(lineView)
        line.forEachIndexed { column, row ->
            val tile = ItemResultStatBinding.inflate(inflater, lineView, true)
            tile.label.setText(row.label)
            tile.value.setCompoundDrawablesRelativeWithIntrinsicBounds(row.icon, 0, 0, 0)
            tile.value.text = "0"
            val index = rowIndex * TILES_PER_ROW + column
            tile.root.countUp(tile, row, RESULT_DELAY_MILLIS + RESULT_MILLIS + index * ROW_STAGGER_MILLIS)
        }
    }
}

/** Contador sobe de 0 até o valor, e o quadrinho dá um "pulo" ao chegar. */
private fun View.countUp(tile: ItemResultStatBinding, row: ResultRow, delay: Long) {
    ValueAnimator.ofInt(0, row.value).apply {
        duration = COUNT_MILLIS
        startDelay = delay
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            tile.value.text = (it.animatedValue as Int).toString()
        }
        doOnEnd {
            animate().scaleX(POP_SCALE).scaleY(POP_SCALE).setDuration(POP_MILLIS)
                .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(POP_MILLIS) }
        }
        start()
    }
}

private class ResultRow(@StringRes val label: Int, val value: Int, @DrawableRes val icon: Int)

private const val TILES_PER_ROW = 3
private const val TITLE_TILT = -4f
private const val POP_SCALE = 1.12f
private const val POP_MILLIS = 120L

/** Vencedor comemora: dois pulos. */
private fun View.victoryJump() {
    val height = -resources.getDimension(R.dimen.space_xl) * JUMP_HEIGHT_FACTOR
    ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 0f, height, 0f, height / 2, 0f).apply {
        duration = JUMP_MILLIS
        startDelay = RESULT_DELAY_MILLIS
        interpolator = comicInterpolator(JUMP_MILLIS)
    }.start()
}
