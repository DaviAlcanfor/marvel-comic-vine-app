package com.projeto.marvel.ui.album

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import com.projeto.marvel.databinding.ItemTradingCardBinding
import kotlin.math.abs

private const val DRAG_DEGREES = 50f
private const val TILT_FACTOR = 1.8f
private const val SPRING_MILLIS = 450L
private const val FLIP_HALF_MILLIS = 170L
private const val QUARTER_TURN = 90f
private const val CAMERA_DISTANCE = 12_000f
private const val TAP_SLOP_DP = 8f

/**
 * Carta grande em 3D. [card] (o conjunto) gira com o dedo e com a inclinação do celular e volta
 * com mola ao soltar; um toque vira a carta: frente ([front]) e verso ([back]) giram cada um no
 * próprio eixo, sem brigar com a rotação do conjunto.
 */
class CardViewer(
    private val card: View,
    private val front: ItemTradingCardBinding,
    private val back: View
) {
    private var tiltX = 0f
    private var tiltY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var showingBack = false

    init {
        val distance = CAMERA_DISTANCE * card.resources.displayMetrics.density
        listOf(card, front.root, back).forEach { it.cameraDistance = distance }
        listen()
    }

    /** Volta para a frente e reta (ao abrir outra carta). */
    fun reset() {
        showingBack = false
        front.root.visibility = View.VISIBLE
        back.visibility = View.GONE
        front.root.rotationY = 0f
        back.rotationY = 0f
        dragX = 0f
        dragY = 0f
        apply()
    }

    fun tilt(pitch: Float, roll: Float) {
        tiltX = -pitch * TILT_FACTOR
        tiltY = roll * TILT_FACTOR
        front.tilt(pitch, roll)
        apply()
    }

    private fun apply() {
        card.rotationX = tiltX + dragX
        card.rotationY = tiltY + dragY
    }

    @SuppressLint("ClickableViewAccessibility") // o toque simples vira a carta, como um clique
    private fun listen() {
        var downX = 0f
        var downY = 0f
        var moved = false
        val slop = TAP_SLOP_DP * card.resources.displayMetrics.density
        card.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    moved = moved || abs(dx) > slop || abs(dy) > slop
                    dragY = (dx / view.width).coerceIn(-1f, 1f) * DRAG_DEGREES
                    dragX = -(dy / view.height).coerceIn(-1f, 1f) * DRAG_DEGREES
                    apply()
                }
                MotionEvent.ACTION_UP -> if (moved) release() else flip()
                MotionEvent.ACTION_CANCEL -> release()
            }
            true
        }
        // O botão do verso precisa receber o toque dele (o resto do verso vira a carta).
        back.setOnClickListener { flip() }
    }

    private fun release() {
        val (fromX, fromY) = dragX to dragY
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SPRING_MILLIS
            interpolator = OvershootInterpolator()
            addUpdateListener {
                val left = it.animatedValue as Float
                dragX = fromX * left
                dragY = fromY * left
                apply()
            }
        }.start()
    }

    /** Meia volta até ficar de lado, troca a face e completa a volta. */
    private fun flip() {
        val (out, into) = if (showingBack) back to front.root else front.root to back
        showingBack = !showingBack
        out.animate().rotationY(QUARTER_TURN).setDuration(FLIP_HALF_MILLIS).withEndAction {
            // INVISIBLE (não GONE): a frente continua dando o tamanho da carta e o verso a preenche.
            out.visibility = View.INVISIBLE
            out.rotationY = 0f
            into.rotationY = -QUARTER_TURN
            into.visibility = View.VISIBLE
            into.animate().rotationY(0f).setDuration(FLIP_HALF_MILLIS)
        }
    }
}
