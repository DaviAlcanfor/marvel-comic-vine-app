package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Stat
import com.projeto.marvel.data.leveled
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.Power
import com.projeto.marvel.data.toFighter
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Equilíbrio conferido por simulação, com os poderes reais da Comic Vine (copiados da API em
 * 2026-09): perfis diferentes (Hulk bate e aguenta mais, Homem-Aranha é mais rápido), mas nenhum
 * confronto é decidido de antemão.
 */
class BattleBalanceTest {

    private fun fighter(name: String, powers: String) = CharacterSummary(
        id = 0, name = name, realName = null, deck = null, description = null, image = null,
        publisher = null, apiDetailUrl = null, powers = powers.split(", ").filter { it.isNotBlank() }.map(::Power)
    ).toFighter()

    private val hulk = fighter(
        "Hulk",
        "Super Strength, Super Speed, Agility, Stamina, Invulnerability, Intellect, Blast Power, Healing, " +
            "Weapon Master, Super Sight, Shape Shifter, Radiation, Unarmed Combat, Gadgets, Immortal, Tracking, " +
            "Adaptive, Energy Absorption, Berserker Strength, Longevity"
    )
    private val spiderMan = fighter(
        "Spider-Man",
        "Super Strength, Super Speed, Agility, Stamina, Intellect, Healing, Feral, Gadgets, Siphon Abilities, " +
            "Wall Clinger, Danger Sense, Berserker Strength, Webslinger"
    )
    private val strange = fighter(
        "Doctor Strange",
        "Flight, Agility, Stamina, Telepathy, Telekinesis, Intellect, Teleport, Psychic, Force Field, Blast Power, " +
            "Healing, Magic, Invisibility, Phasing / Ghost, Fire Control, Psionic, Insanely Rich, Unarmed Combat, " +
            "Divine Powers, Necromancy, Mesmerize, Astral Projection, Possession, Animation, Reality Manpulation, " +
            "Swordsmanship, Dimensional Manipulation, Time Travel, Illusion Casting, Time Manipulation, Hypnosis, " +
            "Energy Manipulation, Cosmic Awareness, Leadership, Longevity"
    )
    private val wolverine = fighter(
        "Wolverine",
        "Super Strength, Agility, Stamina, Invulnerability, Healing, Weapon Master, Super Sight, Super Smell, " +
            "Super Hearing, Implants, Feral, Unarmed Combat, Immortal, Escape Artist, Tracking, Swordsmanship, " +
            "Animal Control, Enhance Mutation, Omni-lingual, Claws, Stealth, Berserker Strength, Leadership, Longevity"
    )
    private val magneto = fighter(
        "Magneto",
        "Flight, Stamina, Invulnerability, Intellect, Force Field, Blast Power, Magnetism, Unarmed Combat, " +
            "Electricity Control, Electronic Disruption, Levitation, Energy Shield, Gravity control, " +
            "Energy Manipulation, Leadership, Longevity"
    )
    private val iceman = fighter(
        "Iceman",
        "Super Strength, Super Speed, Agility, Invulnerability, Teleport, Force Field, Blast Power, Healing, " +
            "Shape Shifter, Psionic, Unarmed Combat, Size Manipulation, Ice Control, Adaptive, " +
            "Energy-Enhanced Strike, " +
            "Swordsmanship, Levitation, Duplication, Energy Shield, Earth Manipulation, Water Control, " +
            "Energy Manipulation, Chemical Absorbtion, Ice Breath, Blood Control"
    )
    private val nobody = fighter("Sem poderes", "")

    private val roster = listOf(hulk, spiderMan, strange, wolverine, magneto, iceman, nobody)

    /** Taxa de vitória de [a] contra [b]; cada um começa metade das lutas (desempate de VEL). */
    private fun winRate(a: Fighter, b: Fighter, fights: Int = 2000): Double {
        val random = Random(42)
        var wins = 0
        repeat(fights) {
            var p = Combatant(a)
            var c = Combatant(b)
            var turns = 0
            while (p.hp > 0 && c.hp > 0 && turns++ < MAX_TURNS) {
                val last = playTurn(p, c, cpuMove(p, random.nextInt(100)), random).last()
                p = last.player
                c = last.cpu
            }
            if (c.hp == 0 && p.hp > 0) wins++
        }
        return wins.toDouble() / fights
    }

    @Test
    fun `perfis sao diferentes`() {
        println(roster.joinToString("\n") { f -> "${f.name}: ${f.stats} hp=${f.maxHp()} ${f.moves.map { it.type }}" })
        assertTrue(hulk.maxHp() > spiderMan.maxHp())
        assertTrue(hulk.stats.getValue(Stat.ATTACK) > spiderMan.stats.getValue(Stat.ATTACK))
        assertTrue(spiderMan.stats.getValue(Stat.SPEED) > hulk.stats.getValue(Stat.SPEED))
    }

    @Test
    fun `nenhum confronto e decidido de antemao`() {
        val report = StringBuilder()
        var worst = 0.5
        for (a in roster) for (b in roster) {
            if (a === b) continue
            val rate = winRate(a, b)
            report.append("%-15s x %-15s %.0f%%\n".format(a.name, b.name, rate * 100))
            if (kotlin.math.abs(rate - 0.5) > kotlin.math.abs(worst - 0.5)) worst = rate
        }
        println(report)
        assertTrue("confronto mais desequilibrado: ${worst * 100}%\n$report", worst in MIN_RATE..MAX_RATE)
    }

    @Test
    fun `nivel alto ajuda mas nao garante a vitoria`() {
        val rate = winRate(spiderMan.leveled(5), spiderMan)
        println("Spider-Man Nv5 x Nv1: ${rate * 100}%")
        assertTrue("Nv5 x Nv1: ${rate * 100}%", rate in LEVEL_MIN_RATE..LEVEL_MAX_RATE)
    }

    private companion object {
        const val LEVEL_MIN_RATE = 0.6
        const val LEVEL_MAX_RATE = 0.9
        const val MAX_TURNS = 200
        const val MIN_RATE = 0.2
        const val MAX_RATE = 0.8
    }
}
