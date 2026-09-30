package com.projeto.marvel.ui.battle

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import android.view.animation.LinearInterpolator
import com.projeto.marvel.R
import com.projeto.marvel.ui.SpeedLinesDrawable
import com.projeto.marvel.ui.comicInterpolator

// Ambiente da arena: fundo vivo, respiração dos lutadores, projétil e zoom de crítico.

private const val SWAY_MILLIS = 1400L
private const val SWAY_DEGREES = 2.5f
private const val SPIN_DEGREES = 720f
private const val SPIN_SCALE = 1.8f
private const val FROST_ALPHA = 110
private const val PUNCH_MILLIS = 240L
private const val PUNCH_SCALE = 1.05f
private const val BACKDROP_FPS = 8
private const val KNOCKBACK_MILLIS = 250L
private const val KNOCKBACK_HOLD_MILLIS = 300L
private const val KNOCKBACK_FACTOR = 1.5f
private const val KNOCKBACK_SCALE = 0.8f
private const val ONE_SECOND = 1000L

/** "Respiração" de espera: o lutador balança de leve, sem parar, até a View sumir. */
fun View.idleSway(startDelay: Long = 0): Animator =
    ObjectAnimator.ofFloat(this, View.ROTATION, -SWAY_DEGREES, SWAY_DEGREES).apply {
        duration = SWAY_MILLIS
        this.startDelay = startDelay
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = comicInterpolator(SWAY_MILLIS)
        start()
    }

/**
 * Projétil (rajada, gelo, água, magia…): sai do centro de [from] e voa até o centro de [to].
 * [spin]: gira e cresce no caminho (círculo místico da magia).
 */
fun View.fireProjectile(from: View, to: View, startDelay: Long = 0, spin: Boolean = false) {
    x = from.x + from.width / 2f - width / 2f
    y = from.y + from.height / 2f - height / 2f
    alpha = 1f
    rotation = 0f
    scaleX = 1f
    scaleY = 1f
    visibility = View.VISIBLE
    val scale = if (spin) SPIN_SCALE else 1f
    animate()
        .x(to.x + to.width / 2f - width / 2f)
        .y(to.y + to.height / 2f - height / 2f)
        .rotation(if (spin) SPIN_DEGREES else 0f)
        .scaleX(scale)
        .scaleY(scale)
        .setStartDelay(startDelay)
        .setDuration(if (spin) STEP_MILLIS * 2 else STEP_MILLIS)
        .setInterpolator(comicInterpolator(STEP_MILLIS))
        .withEndAction { visibility = View.INVISIBLE }
}

/** Gelo que dura: tinge o lutador enquanto ele estiver congelado ([tint] null = descongelado). */
fun ImageView.frost(tint: Int?) {
    if (tint == null) {
        clearColorFilter()
    } else {
        setColorFilter(ColorUtils.setAlphaComponent(tint, FROST_ALPHA), PorterDuff.Mode.SRC_ATOP)
    }
}

/** Crítico (e, mais forte, a ultimate): a cena inteira dá um "soco" de zoom e volta. */
fun View.punchZoom(startDelay: Long = STEP_MILLIS, scale: Float = PUNCH_SCALE) {
    animate().scaleX(scale).scaleY(scale)
        .setStartDelay(startDelay)
        .setDuration(PUNCH_MILLIS / 2)
        .setInterpolator(comicInterpolator(PUNCH_MILLIS / 2))
        .withEndAction { animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(PUNCH_MILLIS / 2) }
}

/**
 * Golpe forte (ultimate): o alvo é arremessado para o fundo (desliza e encolhe) e volta.
 * Só no eixo Y e na escala: o X é do tremor de dano, a rotação é da respiração.
 */
fun View.knockback(direction: Float) {
    val distance = resources.getDimension(R.dimen.space_xl) * KNOCKBACK_FACTOR * direction
    animate().translationY(distance).scaleX(KNOCKBACK_SCALE).scaleY(KNOCKBACK_SCALE)
        .setStartDelay(STEP_MILLIS).setDuration(KNOCKBACK_MILLIS)
        .setInterpolator(comicInterpolator(KNOCKBACK_MILLIS))
        .withEndAction {
            animate().translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(KNOCKBACK_HOLD_MILLIS).setDuration(KNOCKBACK_MILLIS * 2)
                .setInterpolator(comicInterpolator(KNOCKBACK_MILLIS * 2))
        }
}

/** Fundo da arena: linhas de ação que "vibram" a [BACKDROP_FPS], sem parar, até a View sumir. */
fun View.startSpeedLines(drawable: SpeedLinesDrawable): Animator {
    background = drawable
    return ValueAnimator.ofInt(0, BACKDROP_FPS).apply {
        duration = ONE_SECOND
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        var lastFrame = -1
        addUpdateListener {
            val frame = it.animatedValue as Int
            if (frame != lastFrame) {
                lastFrame = frame
                drawable.boil()
            }
        }
        start()
    }
}
