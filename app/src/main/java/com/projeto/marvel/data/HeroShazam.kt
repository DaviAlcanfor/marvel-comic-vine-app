package com.projeto.marvel.data

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.gson.Gson
import com.projeto.marvel.data.remote.CharacterSummary
import java.time.LocalDate

/**
 * Shazam de herói: foto de um boneco, camiseta, pôster, capa ou cosplay → o Gemini diz o personagem
 * → busca na Comic Vine. Nunca identifica pessoa real (só o personagem que ela veste). A foto só vai
 * na requisição; o app não guarda.
 */
class HeroShazam(context: Context, private val comics: ComicVineRepository = ComicVineRepository()) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun recognize(photo: Bitmap): Result<CharacterSummary> {
        val name = withGemini { model ->
            val text = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(modelName = model, generationConfig = generationConfig {
                    responseMimeType = JSON
                    // Escolha simples: sem raciocínio longo a resposta vem em segundos, não em minuto.
                    thinkingConfig = thinkingConfig { thinkingLevel = ThinkingLevel.LOW }
                })
                .generateContent(
                    content {
                        image(photo)
                        text(PROMPT)
                    }
                )
                .text.orEmpty()
            Gson().fromJson(text, Answer::class.java)?.nome?.takeIf { it.isNotBlank() }
                ?: error("Não reconheci nenhum personagem da Marvel na foto.")
        }.getOrElse { return Result.failure(it) }
        return comics.searchCharacters(name).mapCatching { results ->
            val found = results.filter { it.apiDetailUrl != null }
            found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: found.firstOrNull()
                ?: error("Achei \"$name\", mas não está na Comic Vine.")
        }
    }

    /** Prêmio do dia: true só no primeiro reconhecimento de cada dia (e já marca o dia). */
    fun claimDailyReward(today: LocalDate = LocalDate.now()): Boolean {
        if (prefs.getLong(KEY_DAY, -1) == today.toEpochDay()) return false
        prefs.edit { putLong(KEY_DAY, today.toEpochDay()) }
        return true
    }

    private class Answer(val nome: String? = null)

    private companion object {
        const val PREFS_NAME = "hero_shazam"
        const val KEY_DAY = "reward_day"
        const val JSON = "application/json"
        const val PROMPT = "Que personagem da Marvel aparece nesta foto? Pode ser boneco, camiseta, pôster, capa de " +
            "HQ, desenho ou alguém fantasiado. Nunca identifique uma pessoa real: se for só uma pessoa sem " +
            "fantasia, ou não houver personagem da Marvel, responda nome null. Responda só JSON: " +
            "{\"nome\": \"<nome do personagem em inglês, como na Comic Vine>\"}"
    }
}
