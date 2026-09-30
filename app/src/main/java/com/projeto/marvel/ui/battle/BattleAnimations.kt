package com.projeto.marvel.ui.battle

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.projeto.marvel.R
import com.projeto.marvel.ui.BurstDrawable
import com.projeto.marvel.ui.COMIC_FPS
import com.projeto.marvel.ui.SteppedInterpolator
import com.projeto.marvel.ui.comicInterpolator
import com.projeto.marvel.ui.shake
import kotlin.random.Random

// Só a Batalha anima em degraus de COMIC_FPS (ver ui/Animations.kt): poses pulam como quadros de HQ.

/**
 * Duração de um "passo" (recuo + avanço do golpe); os efeitos de impacto começam depois dele.
 * Mais lento que o mínimo de propósito: rápido demais, não dava para ver quem fez o quê.
 */
internal const val STEP_MILLIS = 320L
private const val HURT_MILLIS = 600L
private const val POPUP_MILLIS = 1_100L
private const val ENTER_MILLIS = 500L
private const val DEFEAT_DELAY_MILLIS = 900L
private const val BURST_POP_MILLIS = 300L
private const val BURST_HOLD_MILLIS = 750L
private const val BURST_FADE_MILLIS = 200L
private const val IMPACT_MILLIS = 200L
private const val LUNGE_RETURN_DELAY_MILLIS = 150L
private const val WINDUP_FACTOR = 0.4f
private const val WINDUP_SCALE = 1.12f
private const val BIG_BURST_SCALE = 1.6f
private const val PULSE_SCALE = 1.15f
private const val BLINK_ALPHA = 0.3f
private const val DEFEATED_ALPHA = 0.35f
private const val ENTER_DISTANCE_FACTOR = 4
private const val BURST_START_SCALE = 0.2f
private const val BURST_POP_STEPS = 3
private const val BURST_MAX_TILT = 14
private const val IMPACT_ALPHA = 0.28f
private const val MILLIS_PER_SECOND = 1000
private const val GLOW_MILLIS = 800L
private const val GLOW_MAX_ALPHA = 140
private const val SIDESTEP_FACTOR = 2

/**
 * Recua e cresce um pouco (antecipação: mostra quem vai bater), avança na direção do alvo e
 * volta. [direction] -1 = cima/esquerda, 1 = baixo/direita.
 */
fun View.lunge(direction: Float) {
    val distance = resources.getDimension(R.dimen.space_xl) * direction
    val half = STEP_MILLIS / 2
    animate().translationX(-distance * WINDUP_FACTOR).translationY(-distance * WINDUP_FACTOR)
        .scaleX(WINDUP_SCALE).scaleY(WINDUP_SCALE)
        .setStartDelay(0).setDuration(half).setInterpolator(comicInterpolator(half))
        .withEndAction {
            animate().translationX(distance).translationY(distance).scaleX(1f).scaleY(1f)
                .setStartDelay(0).setDuration(half).setInterpolator(comicInterpolator(half))
                .withEndAction {
                    animate().translationX(0f).translationY(0f)
                        .setStartDelay(LUNGE_RETURN_DELAY_MILLIS).setDuration(STEP_MILLIS)
                        .setInterpolator(comicInterpolator(STEP_MILLIS))
                }
        }
}

/** Treme e pisca: recebeu dano. Começa depois do [lunge] de quem atacou. */
fun View.hurt() {
    shake(startDelay = STEP_MILLIS, interpolator = comicInterpolator(HURT_MILLIS))
    ObjectAnimator.ofFloat(this, View.ALPHA, 1f, BLINK_ALPHA, 1f, BLINK_ALPHA, 1f).apply {
        duration = HURT_MILLIS
        startDelay = STEP_MILLIS
        interpolator = comicInterpolator(HURT_MILLIS)
    }.start()
}

/** Cresce e volta: cura, defesa ou esquiva. */
fun View.pulse() {
    animate().scaleX(PULSE_SCALE).scaleY(PULSE_SCALE).setStartDelay(0).setDuration(STEP_MILLIS)
        .setInterpolator(comicInterpolator(STEP_MILLIS))
        .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(STEP_MILLIS) }
}

/** Aura na cor do golpe (cura, defesa, veneno): o lutador se tinge e volta ao normal. */
fun ImageView.glow(@ColorInt tint: Int, startDelay: Long = 0) {
    ValueAnimator.ofInt(0, GLOW_MAX_ALPHA, 0).apply {
        duration = GLOW_MILLIS
        this.startDelay = startDelay
        interpolator = comicInterpolator(GLOW_MILLIS)
        addUpdateListener {
            val alpha = it.animatedValue as Int
            setColorFilter(ColorUtils.setAlphaComponent(tint, alpha), PorterDuff.Mode.SRC_ATOP)
        }
        doOnEnd { clearColorFilter() }
        start()
    }
}

/** Esquiva: pula de lado (perpendicular ao ataque) e volta. */
fun View.sidestep(direction: Float) {
    val distance = resources.getDimension(R.dimen.space_xl) * SIDESTEP_FACTOR * direction
    animate().translationX(-distance).alpha(BLINK_ALPHA).setStartDelay(0).setDuration(STEP_MILLIS)
        .setInterpolator(comicInterpolator(STEP_MILLIS))
        .withEndAction { animate().translationX(0f).alpha(1f).setDuration(STEP_MILLIS) }
}

/** Entrada no início da luta: desliza do canto [direction] (-1 = cima/esquerda) até o lugar. */
fun View.enterFromCorner(direction: Float) {
    val distance = resources.getDimension(R.dimen.space_xl) * ENTER_DISTANCE_FACTOR * direction
    translationX = distance
    translationY = distance
    alpha = 0f
    animate().translationX(0f).translationY(0f).alpha(1f).setStartDelay(0).setDuration(ENTER_MILLIS)
        .setInterpolator(comicInterpolator(ENTER_MILLIS))
}

/** Derrotado esmaece depois que a animação do golpe final termina. */
fun View.defeat() {
    animate().alpha(DEFEATED_ALPHA).setStartDelay(DEFEAT_DELAY_MILLIS).setDuration(ENTER_MILLIS)
        .setInterpolator(comicInterpolator(ENTER_MILLIS))
}

/** Texto que sobe e some sobre o lutador (dano, cura). */
fun TextView.popup(text: String, @ColorInt textColor: Int) {
    this.text = text
    setTextColor(textColor)
    visibility = View.VISIBLE
    alpha = 1f
    translationY = 0f
    animate()
        .translationY(-resources.getDimension(R.dimen.space_xl))
        .alpha(0f)
        .setStartDelay(STEP_MILLIS)
        .setDuration(POPUP_MILLIS)
        .setInterpolator(comicInterpolator(POPUP_MILLIS))
}

/**
 * Onomatopeia de HQ ("POW!") numa explosão serrilhada: surge torta, estoura em 3 quadros com
 * exagero (overshoot), segura, e some. Enquanto está na tela, o contorno "ferve" a cada quadro (COMIC_FPS).
 */
fun TextView.burst(word: String, @ColorInt fillColor: Int, startDelay: Long = STEP_MILLIS, big: Boolean = false) {
    val drawable = BurstDrawable(
        fillColor = fillColor,
        inkColor = ContextCompat.getColor(context, R.color.ink),
        inkWidth = resources.getDimension(R.dimen.ink_width)
    )
    background = drawable
    text = word
    visibility = View.VISIBLE
    alpha = 1f
    rotation = Random.nextInt(-BURST_MAX_TILT, BURST_MAX_TILT + 1).toFloat()
    scaleX = BURST_START_SCALE
    scaleY = BURST_START_SCALE
    val endScale = if (big) BIG_BURST_SCALE else 1f
    animate().scaleX(endScale).scaleY(endScale)
        .setStartDelay(startDelay)
        .setDuration(BURST_POP_MILLIS)
        .setInterpolator(SteppedInterpolator(BURST_POP_STEPS, OvershootInterpolator()))
        .withEndAction {
            animate().alpha(0f)
                .setStartDelay(BURST_HOLD_MILLIS)
                .setDuration(BURST_FADE_MILLIS)
                .setInterpolator(comicInterpolator(BURST_FADE_MILLIS))
        }

    val total = startDelay + BURST_POP_MILLIS + BURST_HOLD_MILLIS + BURST_FADE_MILLIS
    ValueAnimator.ofInt(0, (total * COMIC_FPS / MILLIS_PER_SECOND).toInt()).apply {
        duration = total
        interpolator = LinearInterpolator()
        var lastFrame = -1
        addUpdateListener {
            val frame = it.animatedValue as Int
            if (frame != lastFrame) {
                lastFrame = frame
                drawable.boil()
            }
        }
    }.start()
}

/** "Quadro de impacto": a tela pisca na cor do golpe por 2 quadros, como painel de HQ. */
fun View.impactFrame(startDelay: Long = STEP_MILLIS) {
    ObjectAnimator.ofFloat(this, View.ALPHA, IMPACT_ALPHA, IMPACT_ALPHA, 0f).apply {
        duration = IMPACT_MILLIS
        this.startDelay = startDelay
        interpolator = SteppedInterpolator(2, LinearInterpolator())
    }.start()
}
