package com.projeto.marvel.ui.battle

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.drawToBitmap
import coil.load
import com.google.android.material.button.MaterialButton
import com.projeto.marvel.R
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.databinding.FragmentBattleBinding
import com.projeto.marvel.databinding.ItemResultStatBinding
import com.projeto.marvel.ui.color
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.SpeedLinesDrawable
import com.projeto.marvel.ui.SteppedInterpolator
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicInterpolator
import com.projeto.marvel.ui.shake
import com.projeto.marvel.ui.icon
import com.projeto.marvel.ui.label
import com.projeto.marvel.ui.photo.shareImage

// Cenas da Batalha além do golpe em si: botões (golpes e ultimate), legenda de turno, destaque
// de quem age, cena da ultimate e painel de resultado.

private const val BANNER_POP_MILLIS = 220L
private const val BANNER_HOLD_MILLIS = 900L
private const val BANNER_START_SCALE = 0.3f
private const val INACTIVE_ALPHA = 0.55f
private const val SCRIM_ALPHA = 0.9f
private const val SCENE_IN_MILLIS = 320L
private const val SCENE_HOLD_MILLIS = 1_400L
private const val SCENE_OUT_MILLIS = 260L

/** Cena inteira da ultimate até o impacto (entrada + pausa + saída). */
internal const val ULTIMATE_SCENE_MILLIS = SCENE_IN_MILLIS + SCENE_HOLD_MILLIS + SCENE_OUT_MILLIS
private const val LINES_ALPHA = 0x66
private const val PANEL_START_SCALE = 3f
private const val PANEL_END_SCALE = 4f
private const val PANEL_START_TILT = -12f
private const val PANEL_TILT = -3f
private const val PANEL_SLAM_STEPS = 4
private const val FACE_ZOOM = 1.25f
private const val STAMP_START_SCALE = 2.2f
private const val STAMP_DELAY_MILLIS = 180L
private const val STAMP_MILLIS = 220L
internal const val RESULT_DELAY_MILLIS = 1_900L
private const val MOVE_WASH_ALPHA = 0x40
internal const val RESULT_MILLIS = 350L

/**
 * Golpes (até 4) e, com a barra cheia, a ultimate. No 2 jogadores o P2 tem o painel de cima
 * (virado para ele) e só o painel de quem está escolhendo fica ativo.
 */
fun FragmentBattleBinding.bindMoves(game: BattleUiState.Success, onUse: (Move) -> Unit) {
    val p1Turn = !game.pvp || game.choosing == Side.PLAYER
    bindMoveSet(listOf(move1, move2, move3, move4), ultimateButton, game.player, !game.busy && p1Turn, game, onUse)
    moves.alpha = if (p1Turn || game.busy) 1f else INACTIVE_ALPHA
    p2Moves.visibility = if (game.pvp && game.winner == null) View.VISIBLE else View.GONE
    if (game.pvp) {
        val p2Buttons = listOf(p2Move1, p2Move2, p2Move3, p2Move4)
        bindMoveSet(p2Buttons, p2Ultimate, game.cpu, !game.busy && !p1Turn, game, onUse)
        p2Moves.alpha = if (!p1Turn || game.busy) 1f else INACTIVE_ALPHA
    }
}

@Suppress("LongParameterList") // os dois painéis (P1 embaixo, P2 em cima) usam o mesmo desenho
private fun FragmentBattleBinding.bindMoveSet(
    buttons: List<MaterialButton>,
    ultimateButton: MaterialButton,
    chooser: Combatant,
    enabled: Boolean,
    game: BattleUiState.Success,
    onUse: (Move) -> Unit
) {
    val context = root.context
    buttons.forEachIndexed { index, button ->
        val move = chooser.fighter.moves.getOrNull(index)
        button.visibility = if (move != null) View.VISIBLE else View.INVISIBLE
        button.isEnabled = enabled
        if (move != null) {
            button.text = context.getString(R.string.battle_move, move.name, context.getString(move.type.label))
            val color = ContextCompat.getColor(context, move.type.color)
            val tint = ColorStateList.valueOf(color)
            button.setIconResource(move.type.icon)
            button.iconTint = tint
            button.strokeColor = tint
            // Papel da época lavado na cor do golpe: cada botão com a sua, e legível sobre a arena.
            val paper = ContextCompat.getColor(context, R.color.surface)
            button.backgroundTintList = ColorStateList.valueOf(
                ColorUtils.compositeColors(ColorUtils.setAlphaComponent(color, MOVE_WASH_ALPHA), paper)
            )
            button.setOnClickListener { onUse(move) }
        }
    }
    val ultimate = chooser.fighter.ultimateMove()
    val ready = chooser.ultimateReady && game.winner == null
    ultimateButton.visibility = if (ready) View.VISIBLE else View.GONE
    ultimateButton.isEnabled = ready && enabled
    ultimateButton.text = context.getString(R.string.battle_ultimate_button, ultimate.name)
    ultimateButton.setIconResource(ultimate.type.icon)
    ultimateButton.setOnClickListener { onUse(ultimate) }
}

/** Legenda de HQ que salta no meio da arena e some ("TURNO 3 · SUA VEZ!"). */
fun TextView.popBanner(text: String) {
    this.text = text
    animate().cancel()
    alpha = 1f
    scaleX = BANNER_START_SCALE
    scaleY = BANNER_START_SCALE
    visibility = View.VISIBLE
    animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(BANNER_POP_MILLIS)
        .setInterpolator(comicInterpolator(BANNER_POP_MILLIS))
        .withEndAction {
            animate().alpha(0f).setStartDelay(BANNER_HOLD_MILLIS).setDuration(BANNER_POP_MILLIS)
                .withEndAction { visibility = View.INVISIBLE }
        }
}

/** Durante o turno, quem não está agindo fica esmaecido: dá para ver de quem é a vez. */
fun FragmentBattleBinding.dimInactive(game: BattleUiState.Success) {
    if (game.winner != null) return
    val acting = game.event?.side?.takeIf { game.busy }
    playerImage.alpha = if (acting == Side.CPU) INACTIVE_ALPHA else 1f
    cpuImage.alpha = if (acting == Side.PLAYER) INACTIVE_ALPHA else 1f
}

/**
 * Cena da ultimate, exagerada de propósito: clarão, tela escura com linhas de ação na cor do golpe,
 * o quadro do lutador desaba na tela (tremor + vibração), o rosto aproxima, o nome do poder carimba
 * e o quadro explode na direção da câmera. Aí [onImpact] roda o golpe em si. Dura
 * [ULTIMATE_SCENE_MILLIS] (o ViewModel espera por ela).
 */
fun FragmentBattleBinding.playUltimateScene(
    imageUrl: String?,
    powerName: String,
    @ColorInt color: Int,
    onImpact: () -> Unit
) {
    ultimateImage.load(imageUrl) { crossfade(true) }
    ultimateName.text = powerName
    impactFlash.impactFrame(startDelay = 0)

    ultimateScrim.visibility = View.VISIBLE
    ultimateScrim.animate().alpha(SCRIM_ALPHA).setStartDelay(0).setDuration(SCENE_IN_MILLIS)
        .setInterpolator(comicInterpolator(SCENE_IN_MILLIS))
    ultimateLines.visibility = View.VISIBLE
    ultimateLines.alpha = 1f
    val lines = ultimateLines.startSpeedLines(SpeedLinesDrawable(ColorUtils.setAlphaComponent(color, LINES_ALPHA)))

    ultimatePanel.visibility = View.VISIBLE
    ultimatePanel.alpha = 0f
    ultimatePanel.scaleX = PANEL_START_SCALE
    ultimatePanel.scaleY = PANEL_START_SCALE
    ultimatePanel.rotation = PANEL_START_TILT
    ultimateImage.scaleX = 1f
    ultimateImage.scaleY = 1f
    ultimateName.alpha = 0f
    ultimatePanel.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(PANEL_TILT)
        .setStartDelay(0).setDuration(SCENE_IN_MILLIS)
        .setInterpolator(SteppedInterpolator(PANEL_SLAM_STEPS, OvershootInterpolator()))
        .withEndAction {
            root.shake(interpolator = comicInterpolator(SCENE_IN_MILLIS))
            arena3d.impact()
            root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            ultimateImage.animate().scaleX(FACE_ZOOM).scaleY(FACE_ZOOM)
                .setStartDelay(0).setDuration(SCENE_HOLD_MILLIS).setInterpolator(comicInterpolator(SCENE_HOLD_MILLIS))
            ultimateName.scaleX = STAMP_START_SCALE
            ultimateName.scaleY = STAMP_START_SCALE
            ultimateName.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(STAMP_DELAY_MILLIS).setDuration(STAMP_MILLIS)
                .setInterpolator(comicInterpolator(STAMP_MILLIS))
            ultimatePanel.animate().alpha(0f).scaleX(PANEL_END_SCALE).scaleY(PANEL_END_SCALE)
                .setStartDelay(SCENE_HOLD_MILLIS).setDuration(SCENE_OUT_MILLIS)
                .setInterpolator(comicInterpolator(SCENE_OUT_MILLIS))
                .withEndAction {
                    ultimatePanel.visibility = View.GONE
                    lines.cancel()
                    ultimateLines.visibility = View.GONE
                    ultimateScrim.animate().alpha(0f).setStartDelay(0).setDuration(SCENE_OUT_MILLIS)
                        .withEndAction { ultimateScrim.visibility = View.GONE }
                    onImpact()
                }
        }
}

/**
 * Trilha do time antes da luta [Stage.number]: vencidos com ✓, o atual pulsando, o resto apagado.
 * O chefe fica no topo; a rolagem começa embaixo (primeiro adversário).
 */
fun FragmentBattleBinding.showTrail(stage: Stage) {
    trailTitle.text = root.context.getString(R.string.trail_title, stage.teamName.orEmpty())
    trailView.setNodes(
        stage.rivals.mapIndexed { index, rival ->
            val state = when {
                index < stage.number - 1 -> TrailNode.State.DONE
                index == stage.number - 1 -> TrailNode.State.CURRENT
                else -> TrailNode.State.LOCKED
            }
            TrailNode(rival.name, rival.imageUrl, rival.boss, state)
        }
    )
    trailOverlay.visibility = View.VISIBLE
    trailOverlay.alpha = 0f
    trailOverlay.animate().alpha(1f).setStartDelay(0).setDuration(RESULT_MILLIS)
    trailScroll.post { trailScroll.fullScroll(View.FOCUS_DOWN) }
    trailFightButton.setOnClickListener {
        trailOverlay.animate().alpha(0f).setDuration(RESULT_MILLIS)
            .withEndAction { trailOverlay.visibility = View.GONE }
    }
}


/**
 * Falas do turno com gramática de HQ fixa: golpe comum é balão branco com o rabinho para quem
 * age; crítico e ultimate estouram numa explosão na cor do golpe; narração (vez do jogador, fim
 * de luta) é a legenda. O balão pende um pouco para o lado de quem fala.
 */
fun FragmentBattleBinding.styleLog(game: BattleUiState.Success) {
    val context = root.context
    val event = game.event
    val density = root.resources.displayMetrics.density
    if (game.winner != null || event == null || !game.busy) {
        val color = when (game.winner) {
            Side.PLAYER -> R.color.move_heal
            Side.CPU -> R.color.primary
            null -> R.color.accent
        }
        logText.comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, color))
        logText.animate().rotation(0f).translationX(0f).translationY(0f).setDuration(LOG_MOVE_MILLIS)
        return
    }
    val cpuActs = event.side == Side.CPU
    val impact = event.critical || event.outcome == Outcome.ULTIMATE
    if (impact) {
        val color = event.moveType?.color ?: R.color.accent
        logText.comicBox(BoxStyle.BURST, ContextCompat.getColor(context, color))
    } else {
        logText.comicBox(BoxStyle.SPEECH, ContextCompat.getColor(context, R.color.paper), tailOnLeft = cpuActs)
    }
    val side = if (cpuActs) -1 else 1
    logText.animate()
        .rotation(side * LOG_TILT)
        .translationX(0f)
        .translationY(side * LOG_SHIFT_Y_DP * density)
        .setDuration(LOG_MOVE_MILLIS)
}

private const val LOG_MOVE_MILLIS = 180L
private const val LOG_TILT = 2f
private const val LOG_SHIFT_Y_DP = 18f
