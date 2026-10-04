package com.projeto.marvel.ui.detail

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Física da Teia de conexões (pura, testada em ForceLayoutTest): o nó 0 (o personagem) fica fixo no
 * centro; cada outro nó é puxado até ele por uma mola de tamanho [rest] e empurra todos os outros
 * (repulsão ~ 1/d²), então os nós se espalham sem se sobrepor. Um nó arrastado ([pinned]) não se mexe.
 */
class ForceLayout(val count: Int, private val rest: Float) {

    val x = FloatArray(count)
    val y = FloatArray(count)
    private val vx = FloatArray(count)
    private val vy = FloatArray(count)
    var pinned = -1

    init {
        for (i in 1 until count) {
            val angle = 2 * PI * (i - 1) / (count - 1)
            x[i] = (rest * cos(angle)).toFloat()
            y[i] = (rest * sin(angle)).toFloat()
        }
    }

    /** Um passo da simulação; devolve a energia (soma das velocidades²) para saber quando parar. */
    fun step(): Float {
        var energy = 0f
        for (i in 1 until count) {
            if (i == pinned) continue
            var fx = -SPRING * x[i] * (1 - rest / distance(x[i], y[i]))
            var fy = -SPRING * y[i] * (1 - rest / distance(x[i], y[i]))
            for (j in 0 until count) {
                if (j == i) continue
                val dx = x[i] - x[j]
                val dy = y[i] - y[j]
                val d = distance(dx, dy)
                val push = REPULSION * rest * rest / (d * d * d)
                fx += dx * push
                fy += dy * push
            }
            vx[i] = (vx[i] + fx) * DAMPING
            vy[i] = (vy[i] + fy) * DAMPING
            x[i] += vx[i]
            y[i] += vy[i]
            energy += vx[i] * vx[i] + vy[i] * vy[i]
        }
        return energy
    }

    /** Nó mais perto de (px, py) dentro de [radius], ou -1. */
    fun nodeAt(px: Float, py: Float, radius: Float): Int =
        (0 until count).filter { distance(x[it] - px, y[it] - py) <= radius }
            .minByOrNull { distance(x[it] - px, y[it] - py) } ?: -1

    private fun distance(dx: Float, dy: Float) = sqrt(dx * dx + dy * dy).coerceAtLeast(MIN_DISTANCE)

    private companion object {
        const val SPRING = 0.05f
        const val REPULSION = 0.6f
        const val DAMPING = 0.82f
        const val MIN_DISTANCE = 0.01f
    }
}
