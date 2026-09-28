package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat

private const val BASE_HP = 100
private const val STRIKE_BASE = 8
private const val BLAST_BASE = 12
private const val POISON_HIT = 4
private const val POISON_TURNS = 3
private const val POISON_TICK = 6
private const val HEAL_BASE = 15
private const val BLAST_MISS_PERCENT = 25
private const val DODGE_BASE_PERCENT = 30
private const val LOW_HP_PERCENT = 35
private const val ATTACK_DIVISOR_STRIKE = 5
private const val ATTACK_DIVISOR_BLAST = 4
private const val DEFENSE_DIVISOR = 10
private const val INTELLIGENCE_DIVISOR = 5
private const val SPEED_DIVISOR = 2
private const val PERCENT = 100
private const val CRITICAL_PERCENT = 15
private const val CRITICAL_NUMERATOR = 3
private const val CRITICAL_DENOMINATOR = 2

fun Fighter.maxHp() = BASE_HP + stats.getValue(Stat.DEFENSE)

data class Combatant(
    val fighter: Fighter,
    val hp: Int = fighter.maxHp(),
    val poisonTurns: Int = 0,
    val guarding: Boolean = false,
    val dodging: Boolean = false
) {
    fun stat(stat: Stat) = fighter.stats.getValue(stat)
}

enum class Outcome { HIT, MISS, POISONED, HEAL, GUARD, DODGE, POISON_TICK }

data class Resolution(
    val actor: Combatant,
    val target: Combatant,
    val outcome: Outcome,
    val amount: Int = 0,
    val critical: Boolean = false
)

/**
 * Aplica [move] de [actor] em [target]. [roll] (0..99) decide erro da Rajada, sucesso da
 * esquiva e golpe crítico — recebido de fora para o teste ser determinístico.
 *
 * Defesa e esquiva duram até o próximo golpe recebido ou até a próxima ação de quem usou.
 */
fun resolve(move: Move, actor: Combatant, target: Combatant, roll: Int): Resolution {
    val self = actor.copy(guarding = false, dodging = false)
    return when (move.type) {
        MoveType.HEAL -> {
            val amount = (HEAL_BASE + actor.stat(Stat.INTELLIGENCE) / INTELLIGENCE_DIVISOR)
                .coerceAtMost(actor.fighter.maxHp() - actor.hp)
            Resolution(self.copy(hp = actor.hp + amount), target, Outcome.HEAL, amount)
        }
        MoveType.GUARD -> Resolution(self.copy(guarding = true), target, Outcome.GUARD)
        MoveType.DODGE -> Resolution(self.copy(dodging = true), target, Outcome.DODGE)
        MoveType.STRIKE, MoveType.BLAST, MoveType.POISON -> attack(move.type, self, target, roll)
    }
}

private fun attack(type: MoveType, actor: Combatant, target: Combatant, roll: Int): Resolution {
    val missChance = (if (type == MoveType.BLAST) BLAST_MISS_PERCENT else 0) +
        (if (target.dodging) DODGE_BASE_PERCENT + target.stat(Stat.SPEED) / SPEED_DIVISOR else 0)
    if (roll < missChance) return Resolution(actor, target.copy(dodging = false), Outcome.MISS)

    val attack = actor.stat(Stat.ATTACK)
    val base = when (type) {
        MoveType.BLAST -> BLAST_BASE + attack / ATTACK_DIVISOR_BLAST
        MoveType.POISON -> POISON_HIT
        else -> STRIKE_BASE + attack / ATTACK_DIVISOR_STRIKE
    }
    // Dado alto = crítico (o baixo já é o erro). Veneno não critica: o dano dele vem nos turnos.
    val critical = type != MoveType.POISON && roll >= PERCENT - CRITICAL_PERCENT
    val raw = if (critical) base * CRITICAL_NUMERATOR / CRITICAL_DENOMINATOR else base
    val damage = ((if (target.guarding) raw / 2 else raw) - target.stat(Stat.DEFENSE) / DEFENSE_DIVISOR)
        .coerceAtLeast(1)
    val hit = target.copy(
        hp = (target.hp - damage).coerceAtLeast(0),
        guarding = false,
        dodging = false,
        poisonTurns = if (type == MoveType.POISON) POISON_TURNS else target.poisonTurns
    )
    return Resolution(actor, hit, if (type == MoveType.POISON) Outcome.POISONED else Outcome.HIT, damage, critical)
}

fun poisonTick(combatant: Combatant) = combatant.copy(
    hp = (combatant.hp - POISON_TICK).coerceAtLeast(0),
    poisonTurns = combatant.poisonTurns - 1
)

/** CPU simples: cura quando a vida está baixa; senão escolhe ao acaso entre os outros golpes. */
fun cpuMove(cpu: Combatant, roll: Int): Move {
    val moves = cpu.fighter.moves
    val heal = moves.firstOrNull { it.type == MoveType.HEAL }
    if (heal != null && cpu.hp * PERCENT < cpu.fighter.maxHp() * LOW_HP_PERCENT) return heal
    val options = moves.filter { it.type != MoveType.HEAL }.ifEmpty { moves }
    return options[roll % options.size]
}
