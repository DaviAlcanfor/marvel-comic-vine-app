package com.projeto.marvel.ui.battle

import com.projeto.marvel.data.Move
import java.text.Normalizer

/**
 * Frases do locutor por evento (tabela fixa: não gasta cota de IA). %1$s = quem agiu, %2$s = o
 * outro. [pick] escolhe a variação (o ViewModel/Fragment passa um número aleatório).
 */
private val LINES: Map<Outcome, List<String>> = mapOf(
    Outcome.HIT to listOf("%1\$s acerta em cheio!", "Que golpe de %1\$s!", "%2\$s sentiu essa!"),
    Outcome.MISS to listOf("%1\$s errou feio!", "Passou raspando em %2\$s!"),
    Outcome.POISONED to listOf("%2\$s foi envenenado!", "Veneno de %1\$s no ar!"),
    Outcome.FROZE to listOf("%2\$s virou picolé!", "%1\$s congelou o adversário!"),
    Outcome.SOAKED to listOf("%2\$s está encharcado!", "Banho de água de %1\$s!"),
    Outcome.DRAINED to listOf("%1\$s sugou a energia de %2\$s!"),
    Outcome.HEAL to listOf("%1\$s se recupera!", "%1\$s respira e volta pra luta!"),
    Outcome.GUARD to listOf("%1\$s levanta a guarda!", "Defesa firme de %1\$s!"),
    Outcome.DODGE to listOf("%1\$s se prepara pra esquivar!", "%1\$s está ligeiro!"),
    Outcome.POISON_TICK to listOf("O veneno pesa em %1\$s!"),
    Outcome.FROZEN_SKIP to listOf("%1\$s está congelado e perde a vez!"),
    Outcome.ULTIMATE to listOf("ULTIMATE de %1\$s! Inacreditável!", "%1\$s solta tudo que tem!"),
    Outcome.SWAP_IN to listOf("%1\$s entra na luta!")
)

private val KNOCKOUT = listOf("NOCAUTE! %1\$s vence!", "Acabou! %1\$s é o campeão!")
private const val CRITICAL = "Crítico! "

fun commentary(outcome: Outcome, actor: String, other: String, critical: Boolean = false, pick: Int = 0): String {
    val lines = LINES.getValue(outcome)
    val line = lines[Math.floorMod(pick, lines.size)].format(actor, other)
    return if (critical) CRITICAL + line else line
}

fun knockoutLine(winner: String, pick: Int = 0): String = KNOCKOUT[Math.floorMod(pick, KNOCKOUT.size)].format(winner)

/**
 * Sacudir carrega o golpe: abaixo de [SHAKE_MIN_G] (andar, mexer na mão) não vale nada; daí até
 * [SHAKE_MAX_G] o bônus cresce até [SHAKE_MAX_BOOST] — pequeno, para não desequilibrar a luta.
 */
fun shakeBoost(peakG: Float): Int = when {
    peakG < SHAKE_MIN_G -> 0
    peakG >= SHAKE_MAX_G -> SHAKE_MAX_BOOST
    else -> 1 + ((peakG - SHAKE_MIN_G) / (SHAKE_MAX_G - SHAKE_MIN_G) * (SHAKE_MAX_BOOST - 1)).toInt()
}

private const val SHAKE_MIN_G = 2.2f
private const val SHAKE_MAX_G = 4f
const val SHAKE_MAX_BOOST = 8

/**
 * Golpe pelo que o jogador falou: o nome do golpe (sem acento/maiúscula) ou, com a barra cheia,
 * um grito de ultimate ("ultimate", "especial", "Excelsior!", "Hulk esmaga!"). Null = não entendeu.
 */
fun moveForSpeech(heard: String, moves: List<Move>, ultimate: Move?): Move? {
    val text = heard.plain()
    if (ultimate != null && ULTIMATE_WORDS.any { it in text }) return ultimate
    return moves.firstOrNull { it.name.plain() in text }
        ?: moves.firstOrNull { move -> move.name.plain().split(" ").any { it.length > MIN_WORD && it in text } }
}

private val ULTIMATE_WORDS = listOf("ultimate", "especial", "excelsior", "esmaga", "vingadores avante", "avengers")
private const val MIN_WORD = 3

private fun String.plain() =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
