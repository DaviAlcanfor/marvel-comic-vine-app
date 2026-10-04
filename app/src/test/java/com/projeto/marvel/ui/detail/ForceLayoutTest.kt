package com.projeto.marvel.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ForceLayoutTest {

    private fun settled(count: Int, rest: Float = 100f) = ForceLayout(count, rest).apply { repeat(600) { step() } }

    @Test
    fun `assenta e nenhum no fica em cima do outro`() {
        val layout = settled(20)
        assertTrue(layout.step() < 0.01f)
        for (i in 0 until layout.count) {
            for (j in i + 1 until layout.count) {
                assertTrue("$i e $j colados", hypot(layout.x[i] - layout.x[j], layout.y[i] - layout.y[j]) > 30f)
            }
        }
    }

    @Test
    fun `personagem fica no centro e os outros perto dele`() {
        val layout = settled(8)
        assertEquals(0f, layout.x[0])
        assertEquals(0f, layout.y[0])
        for (i in 1 until layout.count) assertTrue(hypot(layout.x[i], layout.y[i]) in 60f..260f)
    }

    @Test
    fun `no arrastado nao se mexe`() {
        val layout = ForceLayout(6, 100f)
        layout.pinned = 3
        layout.x[3] = 400f
        layout.y[3] = -50f
        repeat(100) { layout.step() }
        assertEquals(400f, layout.x[3])
        assertEquals(-50f, layout.y[3])
    }

    @Test
    fun `toque acha o no mais perto`() {
        val layout = settled(5)
        assertEquals(0, layout.nodeAt(3f, -2f, 40f))
        assertEquals(-1, layout.nodeAt(5000f, 5000f, 40f))
    }
}
