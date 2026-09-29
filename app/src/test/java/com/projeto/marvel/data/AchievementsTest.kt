package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    @Test
    fun `primeira vitoria desbloqueia com uma vitoria`() {
        assertFalse(Achievement.FIRST_WIN.unlocked(Progress()))
        assertTrue(Achievement.FIRST_WIN.unlocked(Progress(wins = 1)))
    }

    @Test
    fun `vingador so com a trilha dos vingadores`() {
        assertFalse(Achievement.AVENGER.unlocked(Progress(gauntletTeams = setOf("X-Men"))))
        assertTrue(Achievement.AVENGER.unlocked(Progress(gauntletTeams = setOf("Avengers"))))
        assertTrue(Achievement.PATHFINDER.unlocked(Progress(gauntletTeams = setOf("X-Men"))))
    }

    @Test
    fun `progresso nao passa da meta`() {
        assertEquals(10, Achievement.VETERAN.current(Progress(wins = 25)))
    }

    @Test
    fun `novas conquistas sao so as que viraram agora`() {
        val before = Progress(wins = 1)
        val after = Progress(wins = 10, ultimates = 5)
        assertEquals(listOf(Achievement.VETERAN, Achievement.MAX_POWER), newlyUnlocked(before, after))
    }
}
