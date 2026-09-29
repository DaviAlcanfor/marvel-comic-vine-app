package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat
import kotlin.random.Random

// Cada ponto de atributo vale ~1% de alguma coisa: ATQ multiplica o dano físico, INT o de
// magia/dreno (e dá crítico), DEF é vida, VEL é esquiva e ação extra. Como todo personagem reparte
// o mesmo orçamento de pontos (ver toFighter), os perfis ficam diferentes mas equilibrados — o
// equilíbrio é conferido por simulação em BattleBalanceTest.

private const val BASE_HP = 100
private const val BOSS_HP_BONUS = 60
private const val STRIKE_BASE = 14
private const val BLAST_BASE = 18
// Dano esperado por ação (atributo 50, contando erro, crítico e efeito) fica perto de 20 em todos:
// Gelo compensa o dano baixo com a chance de congelar; Água, com o encharcado (-20% no outro).
private const val FREEZE_BASE = 9
private const val WATER_BASE = 10
private const val MAGIC_BASE = 14
private const val DRAIN_BASE = 10
private const val POISON_HIT = 5
private const val POISON_TURNS = 3
private const val POISON_TICK = 6
private const val SOAKED_TURNS = 2
private const val SOAKED_DAMAGE_PERCENT = 80
private const val FREEZE_IMMUNE_TURNS = 3
private const val FREEZE_ODDS = 3
private const val HEAL_BASE = 10
private const val HEAL_INTELLIGENCE_DIVISOR = 4
private const val HEALS_PER_FIGHT = 2
private const val BLAST_MISS_PERCENT = 15
private const val DODGE_BASE_PERCENT = 30
private const val DODGE_SPEED_DIVISOR = 2
private const val EVASION_SPEED_DIVISOR = 3
private const val CRITICAL_INTELLIGENCE_DIVISOR = 5
private const val CRITICAL_NUMERATOR = 3
private const val CRITICAL_DENOMINATOR = 2
private const val LOW_HP_PERCENT = 35
private const val UTILITY_PERCENT = 15
private const val SPREAD_MIN = 75
private const val SPREAD_MAX = 125
private const val EXTRA_ACTION_SPEED_DIVISOR = 2
private const val EXTRA_ACTION_MAX_PERCENT = 30
private const val PERCENT = 100

/** Golpes que atravessam a arena como projétil (os outros são corpo a corpo). */
val RANGED = setOf(MoveType.BLAST, MoveType.POISON, MoveType.FREEZE, MoveType.WATER, MoveType.MAGIC)

fun Fighter.maxHp() = BASE_HP + stats.getValue(Stat.DEFENSE) + if (boss) BOSS_HP_BONUS else 0

/**
 * [frozen]: perde a próxima ação. Depois de descongelar fica [freezeImmuneTurns] turnos imune,
 * senão um personagem de gelo prenderia o outro para sempre.
 * [soakedTurns]: encharcado, causa só [SOAKED_DAMAGE_PERCENT]% do dano.
 * [healsLeft]: cura é limitada por luta, senão cura + defesa seguravam a luta para sempre.
 */
data class Combatant(
    val fighter: Fighter,
    val hp: Int = fighter.maxHp(),
    val poisonTurns: Int = 0,
    val soakedTurns: Int = 0,
    val frozen: Boolean = false,
    val freezeImmuneTurns: Int = 0,
    val healsLeft: Int = HEALS_PER_FIGHT,
    val energy: Int = 0,
    val guarding: Boolean = false,
    val dodging: Boolean = false
) {
    fun stat(stat: Stat) = fighter.stats.getValue(stat)

    /** Barra cheia: pode usar a ultimate ([Fighter.ultimateMove]). */
    val ultimateReady get() = energy >= ENERGY_MAX

    /** Energia de 0 a 1, para a barra da tela. */
    val energyFraction get() = energy.toFloat() / ENERGY_MAX
}


enum class Outcome {
    HIT, MISS, POISONED, FROZE, SOAKED, DRAINED, HEAL, GUARD, DODGE, POISON_TICK, FROZEN_SKIP, ULTIMATE,

    /** 3×3: outro lutador do trio entra (sem dano; não sai de [playTurn], só do ViewModel). */
    SWAP_IN
}

/** [heal]: vida que quem agiu recuperou (cura e dreno). */
data class Resolution(
    val actor: Combatant,
    val target: Combatant,
    val outcome: Outcome,
    val amount: Int = 0,
    val critical: Boolean = false,
    val heal: Int = 0
)

/**
 * Aplica [move] de [actor] em [target]. [roll] (0..99) decide erro (baixo) e crítico (alto);
 * [spread] (%) varia o dano, para uma vantagem pequena não decidir toda luta. Os dois vêm de fora
 * para o teste ser determinístico.
 *
 * Defesa e esquiva duram até o próximo golpe recebido ou até a próxima ação de quem usou.
 */
fun resolve(move: Move, actor: Combatant, target: Combatant, roll: Int, spread: Int = PERCENT): Resolution {
    val self = actor.copy(guarding = false, dodging = false)
    if (move.ultimate) return ultimate(move.type, self, target, spread)
    return when (move.type) {
        MoveType.HEAL -> {
            val amount = if (actor.healsLeft == 0) {
                0
            } else {
                (HEAL_BASE + actor.stat(Stat.INTELLIGENCE) / HEAL_INTELLIGENCE_DIVISOR)
                    .coerceAtMost(actor.fighter.maxHp() - actor.hp)
            }
            val healed = self.copy(hp = actor.hp + amount, healsLeft = (actor.healsLeft - 1).coerceAtLeast(0))
            Resolution(healed, target, Outcome.HEAL, heal = amount)
        }
        MoveType.GUARD -> Resolution(self.copy(guarding = true), target, Outcome.GUARD)
        MoveType.DODGE -> Resolution(self.copy(dodging = true), target, Outcome.DODGE)
        else -> attack(move.type, self, target, roll, spread)
    }
}


private fun attack(type: MoveType, actor: Combatant, target: Combatant, roll: Int, spread: Int): Resolution {
    if (roll < missChance(type, target)) return Resolution(actor, target.copy(dodging = false), Outcome.MISS)

    // Dado alto = crítico (o baixo já é o erro). Efeitos de status não criticam.
    val critical = type in CAN_CRIT && roll >= PERCENT - criticalChance(actor)
    val damage = damage(type, actor, target, critical, spread)
    val healed = if (type == MoveType.DRAIN) (damage / 2).coerceAtMost(actor.fighter.maxHp() - actor.hp) else 0
    // Congela em ~1/3 dos acertos (dado múltiplo de [FREEZE_ODDS]): sempre prendia o alvo demais.
    val froze = type == MoveType.FREEZE && target.freezeImmuneTurns == 0 && roll % FREEZE_ODDS == 0
    val hit = target.copy(
        hp = (target.hp - damage).coerceAtLeast(0),
        guarding = false,
        dodging = false,
        poisonTurns = if (type == MoveType.POISON) POISON_TURNS else target.poisonTurns,
        soakedTurns = if (type == MoveType.WATER) SOAKED_TURNS else target.soakedTurns,
        frozen = target.frozen || froze
    )
    val outcome = if (froze) Outcome.FROZE else HIT_OUTCOMES[type] ?: Outcome.HIT
    return Resolution(actor.copy(hp = actor.hp + healed), hit, outcome, damage, critical, healed)
}

private val HIT_OUTCOMES = mapOf(
    MoveType.POISON to Outcome.POISONED,
    MoveType.WATER to Outcome.SOAKED,
    MoveType.DRAIN to Outcome.DRAINED
)

/** Esquiva passiva pela VEL (ou a do golpe Esquiva, bem maior) + o erro próprio da Rajada. */
private fun missChance(type: MoveType, target: Combatant): Int {
    val evasion = if (target.dodging) {
        DODGE_BASE_PERCENT + target.stat(Stat.SPEED) / DODGE_SPEED_DIVISOR
    } else {
        target.stat(Stat.SPEED) / EVASION_SPEED_DIVISOR
    }
    return (if (type == MoveType.BLAST) BLAST_MISS_PERCENT else 0) + evasion
}

private val CAN_CRIT = setOf(MoveType.STRIKE, MoveType.BLAST, MoveType.MAGIC)

private fun criticalChance(actor: Combatant) = actor.stat(Stat.INTELLIGENCE) / CRITICAL_INTELLIGENCE_DIVISOR

internal fun damage(type: MoveType, actor: Combatant, target: Combatant, critical: Boolean, spread: Int): Int {
    val (base, stat) = when (type) {
        MoveType.BLAST -> BLAST_BASE to Stat.ATTACK
        MoveType.POISON -> POISON_HIT to Stat.ATTACK
        MoveType.FREEZE -> FREEZE_BASE to Stat.ATTACK
        MoveType.WATER -> WATER_BASE to Stat.ATTACK
        MoveType.MAGIC -> MAGIC_BASE to Stat.INTELLIGENCE
        MoveType.DRAIN -> DRAIN_BASE to Stat.INTELLIGENCE
        else -> STRIKE_BASE to Stat.ATTACK
    }
    // +1% de dano por ponto do atributo do golpe, depois a variação do dado.
    val rolled = base * (PERCENT + actor.stat(stat)) / PERCENT * spread / PERCENT
    val crit = if (critical) rolled * CRITICAL_NUMERATOR / CRITICAL_DENOMINATOR else rolled
    val weakened = if (actor.soakedTurns > 0) crit * SOAKED_DAMAGE_PERCENT / PERCENT else crit
    // Magia atravessa a defesa erguida.
    val guarded = if (target.guarding && type != MoveType.MAGIC) weakened / 2 else weakened
    return guarded.coerceAtLeast(1)
}

fun poisonTick(combatant: Combatant) = combatant.copy(
    hp = (combatant.hp - POISON_TICK).coerceAtLeast(0),
    poisonTurns = combatant.poisonTurns - 1
)

/**
 * CPU simples: usa a ultimate assim que a barra enche; cura quando a vida está baixa (se ainda
 * puder); senão ataca, e em
 * [UTILITY_PERCENT]% das vezes defende/esquiva (se tiver).
 */
fun cpuMove(cpu: Combatant, roll: Int): Move {
    val moves = cpu.fighter.moves
    val heal = moves.firstOrNull { it.type == MoveType.HEAL }
    val lowHp = cpu.hp * PERCENT < cpu.fighter.maxHp() * LOW_HP_PERCENT
    val (utility, attacks) = moves.filter { it.type != MoveType.HEAL }.partition { it.type in UTILITY }
    val options = when {
        attacks.isEmpty() -> utility.ifEmpty { moves }
        utility.isEmpty() || roll >= UTILITY_PERCENT -> attacks
        else -> utility
    }
    return when {
        cpu.ultimateReady -> cpu.fighter.ultimateMove()
        heal != null && cpu.healsLeft > 0 && lowHp -> heal
        else -> options[roll % options.size]
    }
}

private val UTILITY = setOf(MoveType.GUARD, MoveType.DODGE)

/** Um passo do turno já resolvido: estado dos dois lados depois da ação de [side]. */
data class TurnStep(
    val player: Combatant,
    val cpu: Combatant,
    val side: Side,
    val outcome: Outcome,
    val move: Move? = null,
    val amount: Int = 0,
    val critical: Boolean = false,
    val heal: Int = 0,
    val extra: Boolean = false
)

/**
 * Um turno inteiro: o mais rápido (VEL) age primeiro e, quanto mais rápido que o outro, mais
 * chance de uma ação extra; congelado perde a vez; no fim o veneno pesa e os efeitos com duração
 * contam um turno. Função pura: a ViewModel só anima os passos.
 */
/**
 * [playerMove] nulo = o jogador gastou a vez (troca no 3×3) e só a CPU age.
 * [cpuChoice]: golpe do segundo jogador no modo 2 jogadores; nulo = a CPU escolhe ([cpuMove]).
 */
fun playTurn(
    player: Combatant,
    cpu: Combatant,
    playerMove: Move?,
    random: Random,
    cpuChoice: Move? = null
): List<TurnStep> =
    Turn(player, cpu, playerMove, random, cpuChoice).play()

/** Estado mutável de um turno em andamento; só existe dentro de [playTurn]. */
private class Turn(
    private var player: Combatant,
    private var cpu: Combatant,
    private val playerMove: Move?,
    private val random: Random,
    private val cpuChoice: Move?
) {
    private val steps = mutableListOf<TurnStep>()

    private fun get(side: Side) = if (side == Side.PLAYER) player else cpu

    private fun set(side: Side, value: Combatant) {
        if (side == Side.PLAYER) player = value else cpu = value
    }

    private fun over() = player.hp == 0 || cpu.hp == 0

    fun play(): List<TurnStep> {
        val fast = if (player.stat(Stat.SPEED) >= cpu.stat(Stat.SPEED)) Side.PLAYER else Side.CPU
        for (side in listOf(fast, fast.opponent())) {
            if (!over()) act(side)
        }
        val speedGap = get(fast).stat(Stat.SPEED) - get(fast.opponent()).stat(Stat.SPEED)
        val extraChance = (speedGap / EXTRA_ACTION_SPEED_DIVISOR).coerceAtMost(EXTRA_ACTION_MAX_PERCENT)
        if (!over() && !get(fast).frozen && random.nextInt(PERCENT) < extraChance) act(fast, extra = true)
        endOfTurn()
        return steps
    }

    private fun act(side: Side, extra: Boolean = false) {
        val actor = get(side)
        if (actor.frozen) {
            set(side, actor.copy(frozen = false, freezeImmuneTurns = FREEZE_IMMUNE_TURNS))
            steps += TurnStep(player, cpu, side, Outcome.FROZEN_SKIP)
            return
        }
        val other = side.opponent()
        val move = (if (side == Side.PLAYER) playerMove else cpuChoice ?: cpuMove(actor, random.nextInt(PERCENT)))
            ?: return
        val spread = random.nextInt(SPREAD_MIN, SPREAD_MAX + 1)
        val result = resolve(move, actor, get(other), random.nextInt(PERCENT), spread)
        // Dano carrega as duas barras: quem bate (inteiro) e quem apanha (metade). A ultimate não recarrega.
        val dealt = if (move.ultimate) 0 else result.amount
        set(side, result.actor.charged(dealt))
        set(other, result.target.charged(result.amount / ENERGY_TAKEN_DIVISOR))
        steps += TurnStep(player, cpu, side, result.outcome, move, result.amount, result.critical, result.heal, extra)
    }

    /** Veneno pesa (um passo animado cada) e os efeitos com duração contam um turno (sem passo). */
    private fun endOfTurn() {
        for (side in Side.entries) {
            val before = get(side)
            if (over() || before.poisonTurns == 0) continue
            set(side, poisonTick(before))
            steps += TurnStep(player, cpu, side, Outcome.POISON_TICK, amount = before.hp - get(side).hp)
        }
        for (side in Side.entries) {
            val combatant = get(side)
            set(
                side,
                combatant.copy(
                    soakedTurns = (combatant.soakedTurns - 1).coerceAtLeast(0),
                    freezeImmuneTurns = (combatant.freezeImmuneTurns - 1).coerceAtLeast(0)
                )
            )
        }
        if (steps.isNotEmpty()) steps[steps.lastIndex] = steps.last().copy(player = player, cpu = cpu)
    }
}

fun Side.opponent() = if (this == Side.PLAYER) Side.CPU else Side.PLAYER
