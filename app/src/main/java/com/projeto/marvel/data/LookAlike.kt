package com.projeto.marvel.data

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.gson.Gson
import com.projeto.marvel.data.remote.CharacterSummary

/**
 * [match] = semelhança de 0 a 100; [traits] = o que combinou; [quote] = o herói falando com você;
 * [runnerUp] = quem quase ganhou.
 */
data class LookAlikeResult(
    val character: CharacterSummary,
    val reason: String,
    val match: Int,
    val traits: List<String>,
    val quote: String,
    val runnerUp: CharacterSummary?
)

private data class Answer(
    val id: Int = 0,
    val motivo: String? = null,
    val semelhanca: Int? = null,
    val detalhes: List<String>? = null,
    val frase: String? = null,
    val segundo: Int? = null
)

private const val MAX_MATCH = 100
private const val MAX_TRAITS = 3

/**
 * "Com qual herói você parece?": manda a foto ao Gemini com a lista dos personagens famosos e
 * pede o mais parecido — por estilo, expressão, pose e roupa, nunca por traços sensíveis. A foto
 * só vai na requisição; o app não guarda.
 */
class LookAlike(private val comics: ComicVineRepository = ComicVineRepository()) {

    suspend fun match(photo: Bitmap): Result<LookAlikeResult> {
        val pool = comics.popularCharacters().getOrElse { return Result.failure(it) }
        return withGemini { model ->
            val response = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(modelName = model, generationConfig = generationConfig { responseMimeType = JSON })
                .generateContent(
                    content {
                        image(photo)
                        text(prompt(pool))
                    }
                )
            val answer = Gson().fromJson(response.text.orEmpty(), Answer::class.java)
            val character = pool.firstOrNull { it.id == answer.id } ?: error("Não reconheci ninguém na foto")
            LookAlikeResult(
                character = character,
                reason = answer.motivo.orEmpty(),
                match = (answer.semelhanca ?: 0).coerceIn(0, MAX_MATCH),
                traits = answer.detalhes.orEmpty().filter { it.isNotBlank() }.take(MAX_TRAITS),
                quote = answer.frase.orEmpty(),
                runnerUp = pool.firstOrNull { it.id == answer.segundo && it.id != character.id }
            )
        }
    }

    private fun prompt(pool: List<CharacterSummary>) = """
        Brincadeira de app de HQ: diga com qual personagem abaixo a pessoa da foto mais parece.
        Compare SÓ estilo e atitude: expressão, pose, penteado, roupa, cores, acessórios, clima da
        foto. Nunca comente etnia, cor de pele, corpo, idade ou aparência física de forma negativa.
        Se não houver pessoa na foto, escolha pelo clima/cores da imagem mesmo.
        Responda só JSON, em português do Brasil, neste formato:
        {
          "id": <id do personagem mais parecido>,
          "semelhanca": <0 a 100>,
          "motivo": "<1 ou 2 frases divertidas explicando a escolha>",
          "detalhes": ["<o que combinou 1>", "<o que combinou 2>", "<o que combinou 3>"],
          "frase": "<uma fala curta do personagem para a pessoa, no jeito dele>",
          "segundo": <id do segundo mais parecido>
        }

        Personagens (id: nome):
        ${pool.joinToString("\n") { "${it.id}: ${it.name}" }}
    """.trimIndent()

    private companion object {
        const val JSON = "application/json"
    }
}
