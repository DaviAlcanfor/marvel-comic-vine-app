package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleRulesTest {

    private val punch = Move("Punch", MoveType.STRIKE)

    private fun fighter(attack: Int = 50, defense: Int = 10, speed: Int = 10, moves: List<Move> = listOf(punch)) =
        Fighter(
            name = "X",
            imageUrl = null,
            stats = mapOf(
                Stat.ATTACK to attack,
                Stat.DEFENSE to defense,
                Stat.SPEED to speed,
                Stat.INTELLIGENCE to 10,
                Stat.FAME to 10
            ),
            moves = moves
        )

    @Test
    fun `soco tira dano pelo ataque menos a defesa`() {
        // 8 + 50/5 = 18; defesa 30/10 = 3 → 15
        val result = resolve(punch, Combatant(fighter()), Combatant(fighter(defense = 30)), roll = 0)
        assertEquals(Outcome.HIT, result.outcome)
        assertEquals(15, result.amount)
        assertEquals(130 - 15, result.target.hp)
    }

    @Test
    fun `defesa corta o dano pela metade e e consumida`() {
        val guarded = Combatant(fighter(), guarding = true)
        val result = resolve(punch, Combatant(fighter()), guarded, roll = 0)
        assertEquals(18 / 2 - 1, result.amount)
        assertFalse(result.target.guarding)
    }

    @Test
    fun `esquiva faz errar quando o dado cai abaixo da chance`() {
        // chance = 30 + 10/2 = 35
        val dodging = Combatant(fighter(), dodging = true)
        assertEquals(Outcome.MISS, resolve(punch, Combatant(fighter()), dodging, roll = 34).outcome)
        assertEquals(Outcome.HIT, resolve(punch, Combatant(fighter()), dodging, roll = 35).outcome)
    }

    @Test
    fun `cura nao passa da vida maxima`() {
        val heal = Move("Healing", MoveType.HEAL)
        val hurt = Combatant(fighter(), hp = 105)
        val result = resolve(heal, hurt, Combatant(fighter()), roll = 0)
        assertEquals(5, result.amount)
        assertEquals(110, result.actor.hp)
    }

    @Test
    fun `veneno marca turnos e tick tira vida`() {
        val poison = Move("Venom", MoveType.POISON)
        val result = resolve(poison, Combatant(fighter()), Combatant(fighter()), roll = 0)
        assertEquals(Outcome.POISONED, result.outcome)
        assertTrue(result.target.poisonTurns > 0)
        val ticked = poisonTick(result.target)
        assertTrue(ticked.hp < result.target.hp)
        assertEquals(result.target.poisonTurns - 1, ticked.poisonTurns)
    }

    @Test
    fun `cpu cura quando a vida esta baixa`() {
        val heal = Move("Healing", MoveType.HEAL)
        val cpu = Combatant(fighter(moves = listOf(punch, heal)), hp = 20)
        assertEquals(heal, cpuMove(cpu, roll = 0))
        assertEquals(punch, cpuMove(cpu.copy(hp = 110), roll = 0))
    }
}
