package com.projeto.marvel.ui.album

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.projeto.marvel.databinding.ViewPackBinding
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val DRAG_DEGREES = 45f
private const val TILT_FACTOR = 1.6f
private const val SWAY_DEGREES = 6f
private const val SWAY_MILLIS = 5_000L
private const val SPRING_MILLIS = 450L
private const val TEAR_ZONE = 0.3f
private const val TEAR_DISTANCE = 0.55f
private const val TEAR_TILT = 12f
private const val TAP_SLOP_DP = 8f

/**
 * Pacote "3D" interativo na abertura. A rotação soma três coisas: um balanço lento, a inclinação
 * do celular ([tilt], giroscópio) e o arraste do dedo (volta com mola ao soltar). Arrastar na
 * faixa de cima para o lado rasga o pacote; um toque simples também abre.
 */
class PackMotion(private val pack: ViewPackBinding, private val onTear: () -> Unit) {

    private var tiltX = 0f
    private var tiltY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var sway = 0f
    private var torn = false
    private val swayAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = SWAY_MILLIS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            sway = sin(it.animatedValue as Float) * SWAY_DEGREES
            apply()
        }
    }

    fun start() {
        torn = false
        swayAnimator.start()
        listen()
    }

    fun stop() {
        swayAnimator.cancel()
        pack.root.setOnTouchListener(null)
    }

    fun tilt(pitch: Float, roll: Float) {
        tiltX = -pitch * TILT_FACTOR
        tiltY = roll * TILT_FACTOR
        apply()
    }

    private fun apply() {
        val root = pack.root
        root.rotationY = sway + tiltY + dragY
        root.rotationX = tiltX + dragX
        // O brilho corre pelo papel conforme o pacote vira para a "luz".
        val turn = ((root.rotationY / DRAG_DEGREES).coerceIn(-1f, 1f) + 1f) / 2f
        pack.packShine.translationX = turn * (root.width - pack.packShine.width)
    }

    @SuppressLint("ClickableViewAccessibility") // o toque simples abre, igual ao clique
    private fun listen() {
        var downX = 0f
        var downY = 0f
        var tearing = false
        var moved = false
        val slop = TAP_SLOP_DP * pack.root.resources.displayMetrics.density
        pack.root.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    tearing = event.y < view.height * TEAR_ZONE
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    moved = moved || abs(dx) > slop || abs(dy) > slop
                    if (tearing) pullStrip(view, dx) else drag(view, dx, dy)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val pulledEnough = tearing && abs(event.x - downX) > view.width * TEAR_DISTANCE
                    if (pulledEnough || (!moved && event.actionMasked == MotionEvent.ACTION_UP)) {
                        open()
                    } else {
                        release()
                    }
                }
            }
            true
        }
    }

    /** Arrastando a faixa de cima: ela entorta e sobe um pouco, como papel começando a rasgar. */
    private fun pullStrip(view: View, dx: Float) {
        val progress = (dx / view.width).coerceIn(-1f, 1f)
        pack.packTop.rotation = progress * TEAR_TILT
        pack.packTop.translationX = dx / 2
        pack.packTop.translationY = -abs(dx) / TEAR_TILT
    }

    private fun drag(view: View, dx: Float, dy: Float) {
        dragY = (dx / view.width).coerceIn(-1f, 1f) * DRAG_DEGREES
        dragX = -(dy / view.height).coerceIn(-1f, 1f) * DRAG_DEGREES
        apply()
    }

    /** Solta: o pacote volta com mola e a faixa, se não rasgou, volta ao lugar. */
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
        pack.packTop.animate().rotation(0f).translationX(0f).translationY(0f).setDuration(SPRING_MILLIS)
            .setInterpolator(OvershootInterpolator())
    }

    private fun open() {
        if (torn) return
        torn = true
        stop()
        onTear()
    }
}
