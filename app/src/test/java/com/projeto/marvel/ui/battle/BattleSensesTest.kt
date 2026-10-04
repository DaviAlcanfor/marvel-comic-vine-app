package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleSensesTest {

    @Test
    fun `todo evento tem fala do locutor com os nomes`() {
        Outcome.entries.forEach { outcome ->
            val line = commentary(outcome, "Thor", "Loki")
            assertTrue("$outcome sem nome: $line", "Thor" in line || "Loki" in line)
        }
        assertTrue(commentary(Outcome.HIT, "Thor", "Loki", critical = true).startsWith("Crítico"))
        assertTrue((0..3).all { "Thor" in knockoutLine("Thor", it) })
    }

    @Test
    fun `sacudir de leve nao vale e forte tem teto`() {
        assertEquals(0, shakeBoost(1.2f))
        assertTrue(shakeBoost(3f) in 1 until SHAKE_MAX_BOOST)
        assertEquals(SHAKE_MAX_BOOST, shakeBoost(9f))
    }

    private val hammer = Move("Martelada", MoveType.STRIKE)
    private val storm = Move("Chamar a Tempestade", MoveType.STRIKE)
    private val ultimate = Move("Fúria de Asgard", MoveType.STRIKE, ultimate = true)

    @Test
    fun `voz acha o golpe pelo nome, sem acento`() {
        assertEquals(hammer, moveForSpeech("martelada nele", listOf(hammer, storm), null))
        assertEquals(storm, moveForSpeech("tempestade", listOf(hammer, storm), null))
        assertNull(moveForSpeech("bom dia", listOf(hammer, storm), null))
    }

    @Test
    fun `grito de ultimate so com a barra cheia`() {
        assertEquals(ultimate, moveForSpeech("EXCELSIOR!", listOf(hammer), ultimate))
        assertNull(moveForSpeech("excelsior", listOf(hammer), null))
    }

    @Test
    fun `sacudir soma dano so no acerto do jogador`() {
        val punch = Move("Punch", MoveType.STRIKE)
        val guard = Move("Guard", MoveType.GUARD)
        fun fighter(moves: List<Move>, speed: Int) = Fighter(
            name = "X",
            imageUrl = null,
            stats = Stat.entries.associateWith { if (it == Stat.SPEED) speed else 30 },
            moves = moves
        )
        val cpu = Combatant(fighter(listOf(guard), speed = 10))
        fun hit(boost: Int, seed: Int) =
            playTurn(Combatant(fighter(listOf(punch), speed = 60), charge = boost), cpu, punch, Random(seed))
                .first { it.side == Side.PLAYER }
        val seed = (0 until 100).first { hit(0, it).outcome == Outcome.HIT }
        assertEquals(hit(0, seed).amount + 5, hit(5, seed).amount)
        assertEquals(hit(0, seed).cpu.hp - 5, hit(5, seed).cpu.hp)
        val missSeed = (0 until 500).firstOrNull { hit(0, it).outcome == Outcome.MISS }
        missSeed?.let { assertEquals(hit(0, it).amount, hit(5, it).amount) }
        assertEquals(0, hit(5, seed).player.charge)
    }
}
