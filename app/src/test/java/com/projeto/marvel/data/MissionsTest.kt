package com.projeto.marvel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MissionsTest {

    private val today = LocalDate.of(2026, 9, 29)

    @Test
    fun `tres diarias e duas semanais sem repetir`() {
        val missions = activeMissions(today)
        assertEquals(3, missions.count { !it.weekly })
        assertEquals(2, missions.count { it.weekly })
        assertEquals(missions.size, missions.map { it.id }.toSet().size)
    }

    @Test
    fun `o sorteio e o mesmo o dia inteiro e as semanais a semana inteira`() {
        assertEquals(activeMissions(today), activeMissions(LocalDate.of(2026, 9, 29)))
        val weekly = { day: LocalDate -> activeMissions(day).filter { it.weekly } }
        val sameWeek = today.minusDays(today.toEpochDay() % 7)
        assertEquals(weekly(sameWeek), weekly(sameWeek.plusDays(6)))
    }

    @Test
    fun `periodo muda de um dia para o outro nas diarias`() {
        val daily = DAILY_MISSIONS.first()
        assertNotEquals(periodOf(daily, today), periodOf(daily, today.plusDays(1)))
        assertTrue(periodOf(WEEKLY_MISSIONS.first(), today).startsWith("w"))
    }
}
