package com.projeto.marvel.ui.battle

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
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
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicInterpolator
import com.projeto.marvel.ui.icon
import com.projeto.marvel.ui.label
import com.projeto.marvel.ui.photo.shareImage
import kotlin.random.Random

// Cenas da Batalha além do golpe em si: botões (golpes e ultimate), legenda de turno, destaque
// de quem age, cena da ultimate e painel de resultado.

private const val BANNER_POP_MILLIS = 220L
private const val BANNER_HOLD_MILLIS = 900L
private const val BANNER_START_SCALE = 0.3f
private const val INACTIVE_ALPHA = 0.55f
private const val SCRIM_ALPHA = 0.85f
private const val SCENE_IN_MILLIS = 250L
private const val SCENE_HOLD_MILLIS = 650L
private const val SCENE_OUT_MILLIS = 200L
internal const val RESULT_DELAY_MILLIS = 1_300L
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
            val tint = ColorStateList.valueOf(ContextCompat.getColor(context, move.type.color))
            button.setIconResource(move.type.icon)
            button.iconTint = tint
            button.strokeColor = tint
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
 * Cena da ultimate: a tela escurece, entra o quadro do lutador com o nome do poder e, quando ele
 * sai, [onImpact] roda o golpe em si.
 */
fun FragmentBattleBinding.playUltimateScene(imageUrl: String?, powerName: String, onImpact: () -> Unit) {
    ultimateImage.load(imageUrl) { crossfade(true) }
    ultimateName.text = powerName
    ultimateScrim.visibility = View.VISIBLE
    ultimatePanel.visibility = View.VISIBLE
    ultimatePanel.translationX = -root.width.toFloat()
    ultimateScrim.animate().alpha(SCRIM_ALPHA).setStartDelay(0).setDuration(SCENE_IN_MILLIS)
    ultimatePanel.animate().translationX(0f).setStartDelay(0).setDuration(SCENE_IN_MILLIS)
        .setInterpolator(comicInterpolator(SCENE_IN_MILLIS))
        .withEndAction {
            ultimatePanel.animate().translationX(root.width.toFloat()).setStartDelay(SCENE_HOLD_MILLIS)
                .setDuration(SCENE_OUT_MILLIS)
                .withEndAction {
                    ultimatePanel.visibility = View.GONE
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
 * Caixa de fala da batalha. Na sua vez, legenda dourada; no fim, verde (vitória) ou vermelha.
 * Durante o turno, cada fala sorteia caixa (legenda, balão, explosão), cor, inclinação e um leve
 * deslocamento para o lado de quem age. O sorteio usa o id do evento como semente: a mesma fala
 * não muda de cara se a tela for redesenhada.
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
    val random = Random(event.id)
    val cpuActs = event.side == Side.CPU
    logText.comicBox(
        style = BoxStyle.entries[random.nextInt(BoxStyle.entries.size)],
        color = ContextCompat.getColor(context, LOG_COLORS[random.nextInt(LOG_COLORS.size)]),
        tailOnLeft = cpuActs
    )
    val towardActor = (if (cpuActs) -LOG_SHIFT_Y_DP else LOG_SHIFT_Y_DP) * density
    logText.animate()
        .rotation(random.nextInt(-LOG_MAX_TILT, LOG_MAX_TILT + 1).toFloat())
        .translationX(random.nextInt(-LOG_MAX_SHIFT_X_DP, LOG_MAX_SHIFT_X_DP + 1) * density)
        .translationY(towardActor)
        .setDuration(LOG_MOVE_MILLIS)
}

private const val LOG_MOVE_MILLIS = 180L
private const val LOG_MAX_TILT = 4
private const val LOG_MAX_SHIFT_X_DP = 20
private const val LOG_SHIFT_Y_DP = 18f

/** Cores das falas: a paleta dos golpes (já testada em contraste) mais o papel. */
private val LOG_COLORS = listOf(
    R.color.move_strike, R.color.move_blast, R.color.move_water, R.color.move_heal, R.color.move_magic,
    R.color.move_dodge, R.color.move_freeze, R.color.move_poison, R.color.move_drain, R.color.paper
)
