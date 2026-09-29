package com.projeto.marvel.data

import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

private const val MAX_TOOL_ROUNDS = 6

/**
 * O Geek: um agente só, que sabe de tudo do universo Marvel porque consulta a Comic Vine pelas
 * [GeekTools] (function calling do Gemini, via Firebase AI Logic — a chave fica no Firebase,
 * protegida pelo App Check). O modelo pede ferramentas, o app executa e devolve, até sair texto.
 */
class GeekAgent(private val tools: GeekTools) {

    private val instructions = """
        Você é o Geek, o maior fã de quadrinhos da Marvel, dentro de um app de HQs. Hoje é ${LocalDate.now()}.
        Responda sempre em português do Brasil, com empolgação de fã, mas curto (até 3 parágrafos).
        Para qualquer fato (datas, edições, poderes, times, filmes, criadores), CONSULTE as
        ferramentas antes de responder; os dados vêm da Comic Vine. Se a ferramenta não trouxer a
        resposta, diga que não achou — não invente. Nomes de busca vão em inglês ("Spider-Man").
        Use "meu_perfil" quando a pergunta for sobre o próprio usuário.
    """.trimIndent()

    // Um Chat por modelo: se o principal lotar e o reserva assumir, ele herda a conversa.
    private var chat: Chat? = null
    private var chatModel: String? = null

    private fun chatFor(model: String): Chat {
        chat?.takeIf { chatModel == model }?.let { return it }
        val next = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = model,
                tools = listOf(Tool.functionDeclarations(tools.declarations)),
                systemInstruction = content { text(instructions) }
            )
            .startChat(chat?.history.orEmpty())
        chat = next
        chatModel = model
        return next
    }

    suspend fun send(message: String): Result<String> = withGemini { model ->
        val chat = chatFor(model)
        var response = chat.sendMessage(message)
        repeat(MAX_TOOL_ROUNDS) {
            if (response.functionCalls.isEmpty()) return@withGemini response.answer()
            response = chat.sendMessage(toolResults(response))
        }
        response.answer()
    }

    private suspend fun toolResults(response: GenerateContentResponse): Content {
        val parts = response.functionCalls.map { call ->
            val result = buildJsonObject { put("resultado", JsonPrimitive(tools.call(call.name, call.args))) }
            FunctionResponsePart(call.name, result, call.id)
        }
        return content("function") { parts.forEach(::part) }
    }

    private fun GenerateContentResponse.answer() = text?.trim().orEmpty().ifEmpty { error("Resposta vazia") }
}
