package com.projeto.marvel.ui

import android.animation.TimeInterpolator
import org.junit.Assert.assertEquals
import org.junit.Test

class SteppedInterpolatorTest {

    @Test
    fun `segura cada pose ate o proximo degrau`() {
        val stepped = SteppedInterpolator(steps = 4, base = TimeInterpolator { it })
        assertEquals(0f, stepped.getInterpolation(0f))
        assertEquals(0.25f, stepped.getInterpolation(0.1f))
        assertEquals(0.25f, stepped.getInterpolation(0.25f))
        assertEquals(0.5f, stepped.getInterpolation(0.26f))
        assertEquals(1f, stepped.getInterpolation(1f))
    }
}
