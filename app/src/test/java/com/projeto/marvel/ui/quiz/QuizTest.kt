package com.projeto.marvel.ui.quiz

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Stat
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizTest {

    private fun fighter(name: String, attack: Int, defense: Int, speed: Int, intelligence: Int) = Fighter(
        name = name,
        imageUrl = null,
        stats = mapOf(
            Stat.ATTACK to attack,
            Stat.DEFENSE to defense,
            Stat.SPEED to speed,
            Stat.INTELLIGENCE to intelligence,
            Stat.FAME to 10
        ),
        moves = emptyList()
    )

    private val hulk = fighter("Hulk", 44, 57, 18, 18)
    private val spiderMan = fighter("Spider-Man", 24, 24, 67, 24)
    private val strange = fighter("Doctor Strange", 18, 18, 24, 77)

    @Test
    fun `quem responde velocidade vira o mais rapido`() {
        val answers = listOf(Stat.SPEED, Stat.SPEED, Stat.SPEED, Stat.ATTACK, Stat.INTELLIGENCE)
        assertEquals(spiderMan, closestFighter(answers, listOf(hulk, spiderMan, strange)))
    }

    @Test
    fun `quem responde forca e resistencia vira o tanque`() {
        val answers = listOf(Stat.DEFENSE, Stat.ATTACK, Stat.DEFENSE, Stat.ATTACK, Stat.DEFENSE)
        assertEquals(hulk, closestFighter(answers, listOf(hulk, spiderMan, strange)))
    }

    @Test
    fun `sem candidatos nao ha resultado`() {
        assertEquals(null, closestFighter(listOf(Stat.SPEED), emptyList()))
    }
}
