package com.projeto.marvel.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ActivityMainBinding
import com.projeto.marvel.ui.battle.startSpeedLines
import kotlin.math.abs
import kotlin.math.sin

// Abertura de HQ: linhas de ação fervendo, o letreiro MARVEL numa explosão que pula, onomatopeias
// estourando pela tela e um balão "Carregando…" com reticências. Sai com um KAPOW!

private const val LOGO_BOB_MILLIS = 700.0
private const val LOGO_BOB_SCALE = 0.08f
private const val LOGO_TILT = -6f
private const val WORD_CYCLE_MILLIS = 1_500L
private const val WORD_STAGGER_MILLIS = 450L
private const val WORD_POP_MILLIS = 260L
private const val WORD_HOLD_MILLIS = 500L
private const val WORD_START_SCALE = 0.2f
private const val DOTS_MILLIS = 350L
private const val MAX_DOTS = 3
private const val EXIT_MILLIS = 420L
private const val EXIT_SCALE = 4f
@Suppress("MagicNumber") // inclinação de cada onomatopeia (POW, BAM, ZAP)
private val WORD_TILTS = floatArrayOf(-12f, 9f, -6f)

/** Liga a abertura; devolve quem para as animações (no fim, [finishComicLoading]). */
fun ActivityMainBinding.startComicLoading(): () -> Unit {
    val context = root.context
    fun color(res: Int) = ContextCompat.getColor(context, res)
    loading.visibility = View.VISIBLE
    val lines = loadingLines.startSpeedLines(SpeedLinesDrawable(color(R.color.speed_lines)))
    loadingLogo.comicBox(BoxStyle.BURST, color(R.color.primary))
    loadingLogo.setTextColor(color(R.color.white))
    loadingLogo.rotation = LOGO_TILT
    loadingBalloon.comicBox(BoxStyle.SPEECH, color(R.color.white))

    val words = listOf(loadingPow to R.color.accent, loadingBam to R.color.move_water, loadingZap to R.color.move_heal)
    words.forEachIndexed { index, (word, tint) ->
        word.comicBox(BoxStyle.BURST, color(tint))
        word.rotation = WORD_TILTS[index]
    }
    val popAll = { words.forEachIndexed { index, (word, _) -> word.pop(index * WORD_STAGGER_MILLIS) } }

    // Um relógio só: pulo do letreiro, reticências do balão e fervura do contorno.
    var cycles = 0
    val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = WORD_CYCLE_MILLIS
        repeatCount = ValueAnimator.INFINITE
        var lastDots = -1
        addUpdateListener {
            val elapsed = currentPlayTime + WORD_CYCLE_MILLIS * cycles
            val bob = 1f + LOGO_BOB_SCALE * abs(sin(elapsed * Math.PI / LOGO_BOB_MILLIS)).toFloat()
            loadingLogo.scaleX = bob
            loadingLogo.scaleY = bob
            val dots = ((elapsed / DOTS_MILLIS) % (MAX_DOTS + 1)).toInt()
            if (dots != lastDots) {
                lastDots = dots
                loadingBalloon.text = context.getString(R.string.loading) + ".".repeat(dots)
                (loadingLogo.background as? BurstDrawable)?.boil()
            }
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationRepeat(animation: Animator) {
                cycles++
                popAll()
            }
        })
        start()
    }
    popAll()
    return {
        clock.cancel()
        lines.cancel()
    }
}

/** Onomatopeia estoura (cresce passando do ponto), segura e some. */
private fun View.pop(delay: Long) {
    animate().cancel()
    alpha = 0f
    scaleX = WORD_START_SCALE
    scaleY = WORD_START_SCALE
    animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(WORD_POP_MILLIS)
        .setInterpolator(OvershootInterpolator())
        .withEndAction { animate().alpha(0f).setStartDelay(WORD_HOLD_MILLIS).setDuration(WORD_POP_MILLIS) }
}

/** KAPOW!: o letreiro vira "KAPOW!", explode crescendo e a tela some revelando o app. */
fun ActivityMainBinding.finishComicLoading(stop: () -> Unit) {
    loadingLogo.setText(R.string.loading_kapow)
    loadingBalloon.animate().alpha(0f).setDuration(EXIT_MILLIS / 2)
    loadingLogo.animate().scaleX(EXIT_SCALE).scaleY(EXIT_SCALE).setDuration(EXIT_MILLIS)
    loading.animate().alpha(0f).setStartDelay(EXIT_MILLIS / 2).setDuration(EXIT_MILLIS).withEndAction {
        stop()
        loading.visibility = View.GONE
    }
}
