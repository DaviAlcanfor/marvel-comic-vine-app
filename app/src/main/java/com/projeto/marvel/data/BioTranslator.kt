package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

/**
 * As bios da Comic Vine vêm em inglês: traduz para o português do Brasil com o Gemini (Firebase AI
 * Logic, plano grátis) e guarda a tradução no aparelho, por personagem — abrir de novo não gasta cota.
 */
class BioTranslator(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun cached(id: Int): String? = prefs.getString(id.toString(), null)

    suspend fun translate(id: Int, text: String): Result<String> {
        cached(id)?.let { return Result.success(it) }
        return withGemini { model ->
            Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(modelName = model)
                .generateContent(PROMPT + text)
                .text.orEmpty().trim()
        }.onSuccess { translation ->
            if (translation.isNotBlank()) prefs.edit { putString(id.toString(), translation) }
        }
    }

    private companion object {
        const val PREFS_NAME = "bio_translations"
        const val PROMPT = "Traduza para o português do Brasil o texto abaixo, sobre um personagem da Marvel. " +
            "Mantenha nomes próprios e de heróis como estão (Spider-Man, X-Men…). Responda só com a tradução.\n\n"
    }
}
