package com.projeto.marvel.data

import kotlinx.coroutines.delay

// Chamadas ao Gemini (Firebase AI Logic) com tolerância a sobrecarga: o Google responde "high
// demand" às vezes, e aí vale tentar de novo esperando mais a cada vez. Cota esgotada (plano
// gratuito) não melhora em segundos: passa para o modelo seguinte, que tem cota própria, e só no
// último vira mensagem clara. Usado pelo Geek e pelo "Com qual herói você parece?".

/** ponytail: lista fixa no código; Remote Config se precisar trocar modelo sem publicar versão. */
internal val GEMINI_MODELS = listOf("gemini-3.8-flash", "gemini-3.1-flash-lite")

private const val MAX_TRIES = 3
private const val BACKOFF_MILLIS = 2_000L
private val BUSY_HINTS = listOf("high demand", "overloaded", "unavailable", "503")
private val QUOTA_HINTS = listOf("quota", "429", "exhausted")
private const val BUSY_MESSAGE =
    "A IA está sobrecarregada agora (do lado do Google). Tente de novo em alguns instantes."
private const val QUOTA_MESSAGE =
    "Acabou a cota gratuita da IA por enquanto. Tente de novo mais tarde."

private fun Throwable.matches(hints: List<String>) = hints.any { message.orEmpty().contains(it, ignoreCase = true) }

/** Roda [call]; sobrecarga vira nova tentativa, cota passa ao próximo modelo, e o que sobrar vira mensagem clara. */
internal suspend fun <T> withGemini(call: suspend (model: String) -> T): Result<T> {
    var result = tryModel(GEMINI_MODELS.first(), call)
    for (model in GEMINI_MODELS.drop(1)) {
        if (result.exceptionOrNull()?.matches(QUOTA_HINTS) != true) break
        result = tryModel(model, call)
    }
    return result.recoverCatching {
        when {
            it.matches(QUOTA_HINTS) -> error(QUOTA_MESSAGE)
            it.matches(BUSY_HINTS) -> error(BUSY_MESSAGE)
            else -> throw it
        }
    }
}

private suspend fun <T> tryModel(model: String, call: suspend (model: String) -> T): Result<T> {
    var result = runCatching { call(model) }
    var tries = 1
    while (result.exceptionOrNull()?.matches(BUSY_HINTS) == true && tries < MAX_TRIES) {
        delay(BACKOFF_MILLIS * tries)
        result = runCatching { call(model) }
        tries++
    }
    return result
}
