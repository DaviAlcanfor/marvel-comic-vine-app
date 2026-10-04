package com.projeto.marvel.ui.trunfo

import com.projeto.marvel.data.Stat

// Regras puras do Super Trunfo (testadas em TrunfoRulesTest).

const val TRUNFO_HAND = 5
const val TRUNFO_MAX_ROUNDS = 20

/** Carta do Trunfo: atributos já com nível/pontos/Divina (as suas) ou base (as da CPU). */
data class TrunfoCard(val id: Int, val name: String, val imageUrl: String?, val stats: Map<Stat, Int>)

enum class TrunfoSide { PLAYER, CPU }

/**
 * Partida: cada um joga a carta do topo do seu monte. [pot] = cartas de empates, que vão para
 * quem vencer a próxima; [chooser] = quem escolhe o atributo (o último vencedor).
 */
data class TrunfoGame(
    val player: List<TrunfoCard>,
    val cpu: List<TrunfoCard>,
    val pot: List<TrunfoCard> = emptyList(),
    val chooser: TrunfoSide = TrunfoSide.PLAYER,
    val round: Int = 1
) {
    val over get() = player.isEmpty() || cpu.isEmpty() || round > TRUNFO_MAX_ROUNDS

    /** Quem ganhou (com tudo, ou com mais cartas no limite de rodadas); null = empate ou em jogo. */
    val winner: TrunfoSide?
        get() = when {
            !over -> null
            player.size > cpu.size -> TrunfoSide.PLAYER
            cpu.size > player.size -> TrunfoSide.CPU
            else -> null
        }
}

data class TrunfoRound(
    val stat: Stat,
    val playerCard: TrunfoCard,
    val cpuCard: TrunfoCard,
    val winner: TrunfoSide?,
    val next: TrunfoGame
)

/** Compara [stat] das duas cartas do topo: o maior leva as duas (e o monte); empate vai para o monte. */
fun TrunfoGame.play(stat: Stat): TrunfoRound {
    val mine = player.first()
    val theirs = cpu.first()
    val table = listOf(mine, theirs) + pot
    val a = mine.stats[stat] ?: 0
    val b = theirs.stats[stat] ?: 0
    val winner = when {
        a > b -> TrunfoSide.PLAYER
        b > a -> TrunfoSide.CPU
        else -> null
    }
    val next = when (winner) {
        TrunfoSide.PLAYER ->
            copy(player = player.drop(1) + table, cpu = cpu.drop(1), pot = emptyList(), chooser = winner)
        TrunfoSide.CPU ->
            copy(player = player.drop(1), cpu = cpu.drop(1) + table, pot = emptyList(), chooser = winner)
        null -> copy(player = player.drop(1), cpu = cpu.drop(1), pot = table)
    }.copy(round = round + 1)
    return TrunfoRound(stat, mine, theirs, winner, next)
}

/** A CPU escolhe o atributo mais forte da carta dela. */
fun TrunfoCard.bestStat(): Stat = stats.maxBy { it.value }.key
