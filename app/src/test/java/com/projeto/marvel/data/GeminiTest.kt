package com.projeto.marvel.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTest {
    @Test
    fun quotaOnFirstModelFallsBackToNext() = runTest {
        val used = mutableListOf<String>()
        val result = withGemini { model ->
            used += model
            if (model == GEMINI_MODELS.first()) error("429 quota exhausted") else "ok"
        }
        assertEquals("ok", result.getOrNull())
        assertEquals(GEMINI_MODELS.take(2), used)
    }

    @Test
    fun quotaOnEveryModelBecomesClearMessage() = runTest {
        val result = withGemini<String> { error("429 quota exhausted") }
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("cota"))
    }

    @Test
    fun otherErrorsDoNotSwitchModel() = runTest {
        val used = mutableListOf<String>()
        val result = withGemini<String> { model ->
            used += model
            error("invalid token")
        }
        assertEquals(listOf(GEMINI_MODELS.first()), used)
        assertEquals("invalid token", result.exceptionOrNull()?.message)
    }
}
