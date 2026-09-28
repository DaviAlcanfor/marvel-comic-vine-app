package com.projeto.marvel.ui.battle

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.LinearInterpolator
import com.projeto.marvel.ui.SpeedLinesDrawable
import com.projeto.marvel.ui.comicInterpolator

// Ambiente da arena: fundo vivo, respiração dos lutadores, projétil e zoom de crítico.

private const val SWAY_MILLIS = 1400L
private const val SWAY_DEGREES = 2.5f
private const val PROJECTILE_MILLIS = 170L
private const val PUNCH_MILLIS = 240L
private const val PUNCH_SCALE = 1.05f
private const val BACKDROP_FPS = 8
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

/** Bola de energia da Rajada: sai do centro de [from] e voa até o centro de [to]. */
fun View.fireProjectile(from: View, to: View) {
    x = from.x + from.width / 2f - width / 2f
    y = from.y + from.height / 2f - height / 2f
    alpha = 1f
    visibility = View.VISIBLE
    animate()
        .x(to.x + to.width / 2f - width / 2f)
        .y(to.y + to.height / 2f - height / 2f)
        .setStartDelay(0)
        .setDuration(PROJECTILE_MILLIS)
        .setInterpolator(comicInterpolator(PROJECTILE_MILLIS))
        .withEndAction { visibility = View.INVISIBLE }
}

/** Crítico: a cena inteira dá um "soco" de zoom e volta. */
fun View.punchZoom(startDelay: Long = STEP_MILLIS) {
    animate().scaleX(PUNCH_SCALE).scaleY(PUNCH_SCALE)
        .setStartDelay(startDelay)
        .setDuration(PUNCH_MILLIS / 2)
        .setInterpolator(comicInterpolator(PUNCH_MILLIS / 2))
        .withEndAction { animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(PUNCH_MILLIS / 2) }
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
