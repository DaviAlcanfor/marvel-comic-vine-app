package com.projeto.marvel.ui.album

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import com.projeto.marvel.databinding.ViewPackBinding
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin

private const val DRAG_DEGREES = 45f
private const val TILT_FACTOR = 1.6f
private const val SWAY_DEGREES = 6f
private const val SWAY_MILLIS = 5_000L
private const val SPRING_MILLIS = 450L
private const val TEAR_ZONE = 0.35f
private const val TEAR_LENGTH = 0.85f
private const val TEAR_COMMIT = 0.55f
private const val FLING_DP_PER_SECOND = 900f
private const val FLING_MIN_PROGRESS = 0.15f
private const val FINISH_MILLIS = 260L
private const val AUTO_TEAR_MILLIS = 520L
private const val TICKS = 8
private const val TAP_SLOP_DP = 8f
private const val ONE_SECOND = 1_000

/**
 * Pacote "3D" interativo na abertura. A rotação soma três coisas: um balanço lento, a inclinação
 * do celular ([tilt], giroscópio) e o arraste do dedo (volta com mola ao soltar). Deslizar na
 * faixa de cima rasga o pacote junto com o dedo ([peel]): soltando antes da metade, a faixa volta;
 * depois (ou num deslize rápido), termina de rasgar. Um toque simples rasga sozinho.
 * [onTear] recebe o lado para onde o dedo puxou (+1 direita, -1 esquerda).
 */
class PackMotion(private val pack: ViewPackBinding, private val onTear: (Int) -> Unit) {

    private var tiltX = 0f
    private var tiltY = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var sway = 0f
    private var torn = false
    private var progress = 0f
    private var direction = 1
    private var tearAnimator: ValueAnimator? = null
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
        progress = 0f
        swayAnimator.start()
        listen()
    }

    fun stop() {
        swayAnimator.cancel()
        tearAnimator?.cancel()
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

    // Gesto em andamento: onde o dedo desceu, se começou na faixa (rasgo) e se já passou do toque.
    private var downX = 0f
    private var downY = 0f
    private var tearing = false
    private var moved = false
    private var velocity: VelocityTracker? = null
    private val density = pack.root.resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility") // o toque simples abre, igual ao clique
    private fun listen() {
        pack.root.setOnTouchListener { view, event ->
            if (!torn) {
                velocity?.addMovement(event)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        tearAnimator?.cancel()
                        downX = event.x
                        downY = event.y
                        tearing = event.y < view.height * TEAR_ZONE
                        moved = false
                        velocity?.recycle()
                        velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                    }
                    MotionEvent.ACTION_MOVE -> move(view, event)
                    MotionEvent.ACTION_UP -> up(tap = true)
                    MotionEvent.ACTION_CANCEL -> up(tap = false)
                }
            }
            true
        }
    }

    private fun move(view: View, event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        val slop = TAP_SLOP_DP * density
        if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
            moved = true
            if (tearing) direction = if (dx >= 0) 1 else -1
        }
        when {
            !tearing -> {
                dragY = (dx / view.width).coerceIn(-1f, 1f) * DRAG_DEGREES
                dragX = -(dy / view.height).coerceIn(-1f, 1f) * DRAG_DEGREES
                apply()
            }
            moved -> setProgress((dx * direction / (view.width * TEAR_LENGTH)).coerceIn(0f, 1f))
        }
    }

    /** Soltou: termina o rasgo (passou da metade ou foi rápido), rasga sozinho (toque) ou volta. */
    private fun up(tap: Boolean) {
        velocity?.computeCurrentVelocity(ONE_SECOND)
        val speed = (velocity?.xVelocity ?: 0f) / density
        velocity?.recycle()
        velocity = null
        val flung = sign(speed).toInt() == direction &&
            abs(speed) > FLING_DP_PER_SECOND && progress > FLING_MIN_PROGRESS
        when {
            tearing && (progress >= TEAR_COMMIT || flung) -> animateTear(1f, FINISH_MILLIS)
            !moved && tap -> animateTear(1f, AUTO_TEAR_MILLIS)
            tearing -> animateTear(0f, SPRING_MILLIS)
            else -> release()
        }
    }

    /** Rasgo até [progress], com um "tec" de vibração a cada pedaço rasgado. */
    private fun setProgress(value: Float) {
        val before = (progress * TICKS).toInt()
        progress = value
        if ((progress * TICKS).toInt() > before) pack.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        pack.peel(progress, direction)
        if (progress >= 1f && !torn) {
            torn = true
            swayAnimator.cancel()
            pack.root.setOnTouchListener(null)
            onTear(direction)
        }
    }

    private fun animateTear(target: Float, duration: Long) {
        tearAnimator?.cancel()
        tearAnimator = ValueAnimator.ofFloat(progress, target).apply {
            this.duration = duration
            interpolator = if (target > 0f) DecelerateInterpolator() else OvershootInterpolator()
            addUpdateListener { setProgress((it.animatedValue as Float).coerceIn(0f, 1f)) }
            start()
        }
    }

    /** Solta: o pacote volta com mola. */
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
}
