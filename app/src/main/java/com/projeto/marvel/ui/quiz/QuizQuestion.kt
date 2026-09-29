package com.projeto.marvel.ui.quiz

import androidx.annotation.StringRes
import com.projeto.marvel.R
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Stat

/** Uma pergunta do quiz: cada resposta aponta para um atributo. */
data class QuizQuestion(@StringRes val text: Int, val answers: List<Pair<Int, Stat>>)

/** Uma resposta por atributo em cada pergunta: o perfil é quantas vezes cada um foi escolhido. */
val QUESTIONS = listOf(
    QuizQuestion(
        R.string.quiz_q1,
        listOf(
            R.string.quiz_q1_attack to Stat.ATTACK,
            R.string.quiz_q1_defense to Stat.DEFENSE,
            R.string.quiz_q1_speed to Stat.SPEED,
            R.string.quiz_q1_intelligence to Stat.INTELLIGENCE
        )
    ),
    QuizQuestion(
        R.string.quiz_q2,
        listOf(
            R.string.quiz_q2_attack to Stat.ATTACK,
            R.string.quiz_q2_defense to Stat.DEFENSE,
            R.string.quiz_q2_speed to Stat.SPEED,
            R.string.quiz_q2_intelligence to Stat.INTELLIGENCE
        )
    ),
    QuizQuestion(
        R.string.quiz_q3,
        listOf(
            R.string.quiz_q3_attack to Stat.ATTACK,
            R.string.quiz_q3_defense to Stat.DEFENSE,
            R.string.quiz_q3_speed to Stat.SPEED,
            R.string.quiz_q3_intelligence to Stat.INTELLIGENCE
        )
    ),
    QuizQuestion(
        R.string.quiz_q4,
        listOf(
            R.string.quiz_q4_attack to Stat.ATTACK,
            R.string.quiz_q4_defense to Stat.DEFENSE,
            R.string.quiz_q4_speed to Stat.SPEED,
            R.string.quiz_q4_intelligence to Stat.INTELLIGENCE
        )
    ),
    QuizQuestion(
        R.string.quiz_q5,
        listOf(
            R.string.quiz_q5_attack to Stat.ATTACK,
            R.string.quiz_q5_defense to Stat.DEFENSE,
            R.string.quiz_q5_speed to Stat.SPEED,
            R.string.quiz_q5_intelligence to Stat.INTELLIGENCE
        )
    )
)

private val COMBAT_STATS = listOf(Stat.ATTACK, Stat.DEFENSE, Stat.SPEED, Stat.INTELLIGENCE)

/**
 * O personagem mais parecido com as respostas: compara as proporções (quanto de cada atributo)
 * do perfil do usuário com as dos atributos derivados dos poderes reais de cada candidato.
 */
fun closestFighter(answers: List<Stat>, candidates: List<Fighter>): Fighter? {
    val profile = COMBAT_STATS.map { stat -> answers.count { it == stat }.toDouble() }.normalized()
    return candidates.minByOrNull { fighter ->
        val stats = COMBAT_STATS.map { fighter.stats.getValue(it).toDouble() }.normalized()
        profile.zip(stats).sumOf { (a, b) -> (a - b) * (a - b) }
    }
}

private fun List<Double>.normalized(): List<Double> {
    val total = sum().takeIf { it > 0 } ?: return map { 0.0 }
    return map { it / total }
}
