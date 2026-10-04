package com.projeto.marvel.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.gson.Gson
import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CatalogService
import com.projeto.marvel.data.remote.VolumeCard

/** Uma série recomendada para começar, com o porquê. */
data class ReadingPick(val series: VolumeCard, val why: String)

private const val MAX_CANDIDATES = 25
private const val MAX_DECK = 100
private const val MAX_PICKS = 5
private const val VOLUME_FIELDS = "id,name,start_year,count_of_issues,publisher,deck,image"

/** Séries da Marvel que o modelo pode escolher: as com mais edições primeiro (regra pura, testada). */
fun starterCandidates(volumes: List<VolumeCard>): List<VolumeCard> = volumes
    .filter { it.publisher?.name?.contains("Marvel", ignoreCase = true) == true && (it.issues ?: 0) > 0 }
    .sortedByDescending { it.issues }
    .take(MAX_CANDIDATES)

/**
 * "Por onde começar a ler?": a Comic Vine não tem ordem de leitura nem diz quais HQs um personagem
 * protagoniza (`volume_credits` vem quase vazio). Usa as séries com o nome dele (`volumes/`, dado
 * real) e o Gemini escolhe até 5 portas de entrada SÓ entre elas, em ordem, com o porquê.
 */
class ReadingGuide(private val service: CatalogService = ApiClient.catalog) {

    suspend fun series(hero: String): Result<List<VolumeCard>> = runCatching {
        starterCandidates(service.getVolumes("name:$hero", fieldList = VOLUME_FIELDS).results())
    }

    suspend fun startHere(hero: String): Result<List<ReadingPick>> {
        val candidates = series(hero).getOrElse { return Result.failure(it) }
        return if (candidates.isEmpty()) Result.success(emptyList()) else pick(hero, candidates)
    }

    private suspend fun pick(hero: String, candidates: List<VolumeCard>): Result<List<ReadingPick>> =
        withGemini { model ->
            val text = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(modelName = model, generationConfig = generationConfig {
                    responseMimeType = JSON
                    // Escolha simples: sem raciocínio longo a resposta vem em segundos, não em minuto.
                    thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.LOW }
                })
                .generateContent(prompt(hero, candidates))
                .text.orEmpty()
            Gson().fromJson(text, Array<Answer>::class.java).orEmpty()
                .mapNotNull { answer ->
                    candidates.firstOrNull { it.id == answer.id }?.let { ReadingPick(it, answer.porque.orEmpty()) }
                }
                .distinctBy { it.series.id }
                .take(MAX_PICKS)
        }

    private fun prompt(hero: String, candidates: List<VolumeCard>) = """
        Um leitor novo quer começar a ler HQs de $hero. Escolha até $MAX_PICKS séries da lista abaixo
        (só dela) e ponha na ordem em que ele deve ler: primeiro as mais acessíveis e marcantes.
        Responda só JSON, em português do Brasil: [{"id": <id>, "porque": "<1 frase>"}]
        Séries (id | nome | ano | edições | resumo):
        ${candidates.joinToString("\n", transform = ::line)}
    """.trimIndent()

    private fun line(v: VolumeCard) =
        "${v.id} | ${v.name} | ${v.startYear} | ${v.issues} | ${v.deck.orEmpty().take(MAX_DECK)}"

    private class Answer(val id: Int? = null, val porque: String? = null)

    private companion object {
        const val JSON = "application/json"
    }
}
