package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BattleRulesTest {

    private val punch = Move("Punch", MoveType.STRIKE)

    // Com VEL 10 a esquiva passiva é 3%: dado 50 sempre acerta sem crítico (INT 10 = crítico só em 98+).
    private val hitRoll = 50

    private fun fighter(
        attack: Int = 50,
        defense: Int = 10,
        speed: Int = 10,
        intelligence: Int = 10,
        moves: List<Move> = listOf(punch)
    ) = Fighter(
        name = "X",
        imageUrl = null,
        stats = mapOf(
            Stat.ATTACK to attack,
            Stat.DEFENSE to defense,
            Stat.SPEED to speed,
            Stat.INTELLIGENCE to intelligence,
            Stat.FAME to 10
        ),
        moves = moves
    )

    @Test
    fun `carta dourada entra na luta com meia ultimate`() {
        assertEquals(ENERGY_MAX / 2, Combatant(fighter().copy(golden = true)).energy)
        assertEquals(0, Combatant(fighter()).energy)
    }

    @Test
    fun `soco tira mais dano com mais ataque e defesa vira vida`() {
        // 14 * (100 + 50)% = 21; vida = 100 + DEF
        val result = resolve(punch, Combatant(fighter()), Combatant(fighter(defense = 30)), roll = hitRoll)
        assertEquals(Outcome.HIT, result.outcome)
        assertEquals(21, result.amount)
        assertEquals(130 - 21, result.target.hp)
    }

    @Test
    fun `dado alto e critico e tira metade a mais`() {
        val result = resolve(punch, Combatant(fighter()), Combatant(fighter()), roll = 99)
        assertTrue(result.critical)
        assertEquals(21 * 3 / 2, result.amount)
        assertFalse(resolve(punch, Combatant(fighter()), Combatant(fighter()), roll = 97).critical)
    }

    @Test
    fun `variacao do dado muda o dano`() {
        val low = resolve(punch, Combatant(fighter()), Combatant(fighter()), roll = hitRoll, spread = 75)
        val high = resolve(punch, Combatant(fighter()), Combatant(fighter()), roll = hitRoll, spread = 125)
        assertTrue(low.amount < high.amount)
    }

    @Test
    fun `defesa corta o dano pela metade e e consumida`() {
        val guarded = Combatant(fighter(), guarding = true)
        val result = resolve(punch, Combatant(fighter()), guarded, roll = hitRoll)
        assertEquals(21 / 2, result.amount)
        assertFalse(result.target.guarding)
    }

    @Test
    fun `magia atravessa a defesa erguida`() {
        val magic = Move("Magic", MoveType.MAGIC)
        val guarded = Combatant(fighter(), guarding = true)
        // 14 * (100 + INT 10)% = 15, sem cortar pela metade
        assertEquals(15, resolve(magic, Combatant(fighter()), guarded, roll = hitRoll).amount)
    }

    @Test
    fun `esquiva faz errar quando o dado cai abaixo da chance`() {
        // chance = 30 + 10/2 = 35
        val dodging = Combatant(fighter(), dodging = true)
        assertEquals(Outcome.MISS, resolve(punch, Combatant(fighter()), dodging, roll = 34).outcome)
        assertEquals(Outcome.HIT, resolve(punch, Combatant(fighter()), dodging, roll = 35).outcome)
    }

    @Test
    fun `cura nao passa da vida maxima e acaba`() {
        val heal = Move("Healing", MoveType.HEAL)
        val hurt = Combatant(fighter(), hp = 105)
        val result = resolve(heal, hurt, Combatant(fighter()), roll = 0)
        assertEquals(5, result.heal)
        assertEquals(110, result.actor.hp)
        val exhausted = Combatant(fighter(), hp = 50, healsLeft = 0)
        assertEquals(50, resolve(heal, exhausted, Combatant(fighter()), roll = 0).actor.hp)
    }

    @Test
    fun `dreno devolve metade do dano para quem atacou`() {
        val drain = Move("Siphon Lifeforce", MoveType.DRAIN)
        val result = resolve(drain, Combatant(fighter(), hp = 50), Combatant(fighter()), roll = hitRoll)
        assertEquals(Outcome.DRAINED, result.outcome)
        assertEquals(result.amount / 2, result.heal)
        assertEquals(50 + result.heal, result.actor.hp)
    }

    @Test
    fun `agua encharca e encharcado bate mais fraco`() {
        val water = Move("Water Control", MoveType.WATER)
        val soaked = resolve(water, Combatant(fighter()), Combatant(fighter()), roll = hitRoll).target
        assertTrue(soaked.soakedTurns > 0)
        val normal = resolve(punch, Combatant(fighter()), Combatant(fighter()), roll = hitRoll).amount
        assertTrue(resolve(punch, soaked, Combatant(fighter()), roll = hitRoll).amount < normal)
    }

    @Test
    fun `gelo congela em parte dos acertos e congelado perde a vez`() {
        val ice = Move("Ice Breath", MoveType.FREEZE)
        assertEquals(Outcome.HIT, resolve(ice, Combatant(fighter()), Combatant(fighter()), roll = 50).outcome)
        val frozen = resolve(ice, Combatant(fighter()), Combatant(fighter()), roll = 51)
        assertEquals(Outcome.FROZE, frozen.outcome)
        assertTrue(frozen.target.frozen)

        // CPU congelada: perde a vez e fica imune por alguns turnos.
        val steps = playTurn(Combatant(fighter(speed = 50)), frozen.target, punch, Random(0))
        val skip = steps.single { it.side == Side.CPU }
        assertEquals(Outcome.FROZEN_SKIP, skip.outcome)
        assertFalse(steps.last().cpu.frozen)
        assertTrue(steps.last().cpu.freezeImmuneTurns > 0)
    }

    @Test
    fun `veneno marca turnos e tick tira vida`() {
        val poison = Move("Venom", MoveType.POISON)
        val result = resolve(poison, Combatant(fighter()), Combatant(fighter()), roll = hitRoll)
        assertEquals(Outcome.POISONED, result.outcome)
        assertTrue(result.target.poisonTurns > 0)
        val ticked = poisonTick(result.target)
        assertTrue(ticked.hp < result.target.hp)
        assertEquals(result.target.poisonTurns - 1, ticked.poisonTurns)
    }

    @Test
    fun `ultimate nao erra, atravessa defesa e esquiva e gasta a barra`() {
        val ultimate = fighter().ultimateMove()
        val full = Combatant(fighter(), energy = 100)
        val target = Combatant(fighter(speed = 99), guarding = true, dodging = true)
        val result = resolve(ultimate, full, target, roll = 0)
        assertEquals(Outcome.ULTIMATE, result.outcome)
        // crítico (21 * 3/2 = 31) vezes 2
        assertEquals(62, result.amount)
        assertEquals(0, result.actor.energy)
    }

    @Test
    fun `sem golpe do jogador so a CPU age`() {
        val steps = playTurn(Combatant(fighter(speed = 90)), Combatant(fighter()), null, Random(SEED))
        assertTrue(steps.isNotEmpty())
        assertTrue(steps.all { it.side == Side.CPU })
    }

    @Test
    fun `no modo 2 jogadores o golpe do adversario e o escolhido, nao o da CPU`() {
        val guard = Move("Guard", MoveType.GUARD)
        val cpu = fighter(moves = listOf(punch, guard))
        val step = playTurn(Combatant(fighter()), Combatant(cpu), punch, Random(SEED), cpuChoice = guard)
            .first { it.side == Side.CPU }
        assertEquals(guard, step.move)
    }

    @Test
    fun `dano carrega a barra de quem bate e metade na de quem apanha`() {
        val guardOnly = fighter(moves = listOf(Move("Guard", MoveType.GUARD)))
        val step = playTurn(Combatant(fighter(speed = 50)), Combatant(guardOnly), punch, Random(SEED))
            .first { it.side == Side.PLAYER }
        assertEquals(Outcome.HIT, step.outcome)
        // FAMA 10 dos dois: +5% em cada carga.
        assertEquals(step.amount * 105 / 100, step.player.energy)
        assertEquals(step.amount / 2 * 105 / 100, step.cpu.energy)
    }

    @Test
    fun `quem e famoso carrega a barra mais rapido`() {
        val famous = Combatant(fighter().copy(stats = fighter().stats + (Stat.FAME to 90)))
        val unknown = Combatant(fighter())
        assertTrue(famous.charged(40).energy > unknown.charged(40).energy)
    }

    @Test
    fun `cpu usa a ultimate assim que a barra enche`() {
        val cpu = Combatant(fighter(), energy = 100)
        assertTrue(cpuMove(cpu, roll = 0).ultimate)
    }

    @Test
    fun `cpu cura quando a vida esta baixa`() {
        val heal = Move("Healing", MoveType.HEAL)
        val cpu = Combatant(fighter(moves = listOf(punch, heal)), hp = 20)
        assertEquals(heal, cpuMove(cpu, roll = 0))
        assertEquals(punch, cpuMove(cpu.copy(hp = 110), roll = 0))
        assertEquals(punch, cpuMove(cpu.copy(healsLeft = 0), roll = 0))
    }

    private companion object {
        // Semente em que o soco acerta (a esquiva passiva é só 3% com VEL 10).
        const val SEED = 1
    }
}
