package com.projeto.marvel.ui

import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LayoutAnimationController
import androidx.recyclerview.widget.RecyclerView
import com.projeto.marvel.R
import kotlin.math.ceil

// O app anima suave (taxa da tela). Só a Batalha usa movimento de HQ: animação em degraus a
// COMIC_FPS, como flipbook/stop motion — no app inteiro isso parecia travamento.
//
// ViewPropertyAnimator guarda duration/startDelay/interpolator entre chamadas na mesma View:
// toda função aqui define os três explicitamente para não herdar os de uma animação anterior.

/** Quadros por segundo das animações da Batalha. 12 = "em twos" (bem HQ), 24 = cinema. */
const val COMIC_FPS = 24
private const val MILLIS_PER_SECOND = 1000

private const val FADE_MILLIS = 250L
private const val ENTER_MILLIS = 400L
private const val STAGGER_MILLIS = 80L
private const val SHAKE_MILLIS = 400L
private const val SHAKE = 20f
private const val SHAKE_SMALL = 12f
private const val LIST_STAGGER = 0.15f

private val smooth = DecelerateInterpolator()

/** Congela o tempo em [steps] degraus: a animação "pula" de pose em pose em vez de deslizar. */
class SteppedInterpolator(
    private val steps: Int,
    private val base: TimeInterpolator = DecelerateInterpolator()
) : Interpolator {
    // ceil: o primeiro degrau já mostra movimento (floor seguraria a pose inicial um quadro a mais).
    override fun getInterpolation(input: Float): Float = base.getInterpolation(ceil(input * steps) / steps)
}

/** Interpolador em degraus de [COMIC_FPS] para uma animação de [durationMillis] (só na Batalha). */
fun comicInterpolator(durationMillis: Long): Interpolator =
    SteppedInterpolator(maxOf(1, (durationMillis * COMIC_FPS / MILLIS_PER_SECOND).toInt()))

/** Troca de visibilidade com fade, no lugar de `visibility = if (...) VISIBLE else GONE`. */
fun View.fadeVisible(visible: Boolean) {
    if (visible) {
        if (visibility != View.VISIBLE) {
            alpha = 0f
            visibility = View.VISIBLE
        }
        animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS).setInterpolator(smooth)
    } else if (visibility == View.VISIBLE) {
        // Se um fade-in começar no meio, alpha != 0 no fim e a View continua visível.
        animate().alpha(0f).setStartDelay(0).setDuration(FADE_MILLIS).setInterpolator(smooth)
            .withEndAction { if (alpha == 0f) visibility = View.GONE }
    }
}

/** Views sobem e aparecem uma depois da outra, na ordem da lista. */
fun staggerIn(views: List<View>) {
    views.forEachIndexed { index, view ->
        view.alpha = 0f
        view.translationY = view.resources.getDimension(R.dimen.space_md)
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(index * STAGGER_MILLIS)
            .setDuration(ENTER_MILLIS)
            .setInterpolator(smooth)
    }
}

/** Treme na horizontal: erro (login) ou dano (batalha, que passa o interpolador em degraus). */
fun View.shake(startDelay: Long = 0, interpolator: TimeInterpolator = smooth) {
    ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0f, SHAKE, -SHAKE, SHAKE_SMALL, -SHAKE_SMALL, 0f).apply {
        duration = SHAKE_MILLIS
        this.startDelay = startDelay
        this.interpolator = interpolator
    }.start()
}

/**
 * Cards entram em cascata. O controller é atribuído só aqui (não no XML) porque, no XML, a
 * cascata repetiria toda vez que a View fosse recriada — ex.: ao voltar do Detalhe.
 */
fun RecyclerView.animateItemsIn() {
    val itemAnimation = AnimationUtils.loadAnimation(context, R.anim.item_enter)
    layoutAnimation = LayoutAnimationController(itemAnimation, LIST_STAGGER)
    scheduleLayoutAnimation()
}
