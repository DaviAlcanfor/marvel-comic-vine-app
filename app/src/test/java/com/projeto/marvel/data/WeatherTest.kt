package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherTest {

    @Test
    fun `tempo vira heroi`() {
        assertEquals("Thor", weatherHero(95, 25.0).name)
        assertEquals("Storm", weatherHero(61, 22.0).name)
        assertEquals("Iceman", weatherHero(73, -2.0).name)
        assertEquals("Invisible Woman", weatherHero(45, 15.0).name)
        assertEquals("Human Torch", weatherHero(0, 33.0).name)
        assertEquals("Iceman", weatherHero(0, 8.0).name)
        assertEquals("Doctor Strange", weatherHero(2, 20.0).name)
        assertEquals("Spider-Man", weatherHero(0, 22.0).name)
    }

    @Test
    fun `chuva quente ainda e da Storm`() {
        assertEquals("Storm", weatherHero(80, 31.0).name)
    }
}
