package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat

// Barra de energia e ultimate. A barra enche com dano causado (inteiro) e recebido (metade, para
// quem está apanhando também chegar lá), mais rápido para quem é famoso; cheia, libera a ultimate,
// que gasta a barra toda.

internal const val ENERGY_MAX = 100
internal const val ENERGY_TAKEN_DIVISOR = 2

/** Carta Divina entra na luta com meia barra: é o que ela tem de próprio, além do +5 em tudo. */
internal const val GOLDEN_START_ENERGY = ENERGY_MAX / 2
private const val ULTIMATE_MULTIPLIER = 2

/**
 * Ultimate: o golpe de dano de maior prioridade do personagem (temáticos primeiro), turbinado.
 * Sem golpe de dano, um "Golpe final" genérico.
 */
fun Fighter.ultimateMove(): Move {
    val damaging = moves.filter { it.type !in UTILITY_AND_HEAL }
    // Veneno tem dano direto pequeno (o forte é o efeito): só vira ultimate se não houver outro.
    val best = damaging.firstOrNull { it.type != MoveType.POISON } ?: damaging.firstOrNull()
    return (best ?: Move(FINAL_BLOW, MoveType.STRIKE)).copy(ultimate = true)
}

private const val FINAL_BLOW = "Golpe final"
private val UTILITY_AND_HEAL = setOf(MoveType.HEAL, MoveType.GUARD, MoveType.DODGE)

/**
 * Ultimate: não erra e atravessa defesa e esquiva; dano de crítico vezes [ULTIMATE_MULTIPLIER].
 * Gasta a barra inteira.
 */
internal fun ultimate(type: MoveType, actor: Combatant, target: Combatant, spread: Int): Resolution {
    val open = target.copy(guarding = false, dodging = false)
    val damage = damage(type, actor, open, critical = true, spread = spread) * ULTIMATE_MULTIPLIER
    val hit = open.copy(hp = (open.hp - damage).coerceAtLeast(0))
    return Resolution(actor.copy(energy = 0), hit, Outcome.ULTIMATE, damage, critical = true)
}

/** A FAMA acelera a barra: +1% de energia a cada [FAME_CHARGE_DIVISOR] pontos (99 de fama ≈ +50%). */
internal fun Combatant.charged(amount: Int): Combatant {
    val fame = fighter.stats[Stat.FAME] ?: 0
    val boosted = amount * (PERCENT + fame / FAME_CHARGE_DIVISOR) / PERCENT
    return copy(energy = (energy + boosted).coerceAtMost(ENERGY_MAX))
}

private const val FAME_CHARGE_DIVISOR = 2
private const val PERCENT = 100
