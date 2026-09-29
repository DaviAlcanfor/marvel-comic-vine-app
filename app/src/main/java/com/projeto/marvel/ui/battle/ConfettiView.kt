package com.projeto.marvel.ui.battle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Chuva de confete da vitória: papéis coloridos estouram de baixo, giram e caem com gravidade.
 * Não recebe toque; some sozinha quando todos saem da tela.
 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Piece {
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var angle = 0f
        var spin = 0f
        var width = 0f
        var height = 0f
        var color = 0
        var round = false
    }

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pieces = mutableListOf<Piece>()
    private val colors = listOf(
        R.color.accent, R.color.primary, R.color.move_water, R.color.move_heal,
        R.color.move_magic, R.color.move_freeze, R.color.move_dodge, R.color.text_primary
    ).map { ContextCompat.getColor(context, it) }
    private var animator: ValueAnimator? = null
    private var lastFrame = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Solta o confete depois de [delayMillis] (a vitória espera o K.O.). */
    fun burst(delayMillis: Long = 0) {
        animator?.cancel()
        pieces.clear()
        repeat(PIECES) { pieces += newPiece() }
        visibility = VISIBLE
        lastFrame = 0L
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MILLIS
            startDelay = delayMillis
            interpolator = LinearInterpolator()
            addUpdateListener { step() }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        pieces.clear()
        visibility = INVISIBLE
    }

    private fun newPiece(): Piece {
        // Dois canhões nos cantos de baixo, atirando para o alto e para o meio.
        val fromLeft = Random.nextBoolean()
        val degrees = Random.nextDouble(CANNON_MIN_DEGREES, CANNON_MAX_DEGREES)
        val angle = Math.toRadians(if (fromLeft) degrees else HALF_TURN - degrees)
        val speed = Random.nextFloat() * SPEED_RANGE + SPEED_MIN
        val canvasHeight = height.toFloat()
        val canvasWidth = width.toFloat()
        return Piece().apply {
            x = if (fromLeft) 0f else canvasWidth
            y = canvasHeight
            vx = (cos(angle) * speed).toFloat() * density
            vy = (-sin(angle) * speed).toFloat() * density
            this.angle = Random.nextFloat() * FULL_TURN
            spin = (Random.nextFloat() - CENTER) * SPIN_RANGE
            width = (Random.nextFloat() * SIZE_RANGE + SIZE_MIN) * density
            height = (Random.nextFloat() * SIZE_RANGE + SIZE_MIN) * density * PAPER_RATIO
            color = colors.random()
            round = Random.nextFloat() < ROUND_CHANCE
        }
    }

    private fun step() {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) FRAME_SECONDS else ((now - lastFrame) / NANOS_PER_SECOND).coerceAtMost(MAX_STEP)
        lastFrame = now
        pieces.forEach { piece ->
            piece.vy += GRAVITY * density * dt
            piece.vx *= DRAG
            piece.x += piece.vx * dt
            piece.y += piece.vy * dt
            piece.angle += piece.spin * dt
        }
        pieces.removeAll { it.y > height + it.height && it.vy > 0 }
        if (pieces.isEmpty()) stop() else invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        pieces.forEach { piece ->
            paint.color = piece.color
            canvas.save()
            canvas.rotate(piece.angle, piece.x, piece.y)
            if (piece.round) {
                canvas.drawCircle(piece.x, piece.y, piece.width / 2, paint)
            } else {
                canvas.drawRect(
                    piece.x - piece.width / 2,
                    piece.y - piece.height / 2,
                    piece.x + piece.width / 2,
                    piece.y + piece.height / 2,
                    paint
                )
            }
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private companion object {
        const val PIECES = 120
        const val DURATION_MILLIS = 4_000L
        const val SPEED_MIN = 700f
        const val SPEED_RANGE = 500f
        const val GRAVITY = 900f
        const val DRAG = 0.995f
        const val SPIN_RANGE = 720f
        const val SIZE_MIN = 6f
        const val SIZE_RANGE = 6f
        const val PAPER_RATIO = 1.6f
        const val ROUND_CHANCE = 0.25f
        const val FULL_TURN = 360f
        const val HALF_TURN = 180.0
        const val CANNON_MIN_DEGREES = 55.0
        const val CANNON_MAX_DEGREES = 80.0
        const val CENTER = 0.5f
        const val FRAME_SECONDS = 1f / 60f
        const val MAX_STEP = 0.05f
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}
