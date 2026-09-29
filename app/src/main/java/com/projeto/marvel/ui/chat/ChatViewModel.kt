package com.projeto.marvel.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.BattleRecordStore
import com.projeto.marvel.data.GeekAgent
import com.projeto.marvel.data.GeekTools
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.data.ReadingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [error]: falha da IA mostrada como balão do sistema (não é fala do Geek). */
data class ChatMessage(val text: String, val fromUser: Boolean, val error: Boolean = false)

/** [typing]: esperando a resposta do Geek (bloqueia o envio). */
data class ChatUiState(val messages: List<ChatMessage>, val typing: Boolean)

/**
 * Conversa com o Geek. Aberto a partir de um personagem ([ARG_TOPIC]), já começa perguntando dele.
 */
class ChatViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val agent: GeekAgent = GeekAgent(GeekTools(profile = profileOf(application)))
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ChatUiState(listOf(ChatMessage(WELCOME, fromUser = false)), typing = false))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        savedStateHandle.get<String>(ARG_TOPIC)?.let { send("Me conta tudo sobre $it!") }
    }

    fun send(text: String) {
        val current = _state.value
        val message = text.trim()
        if (message.isEmpty() || current.typing) return
        _state.value = current.copy(messages = current.messages + ChatMessage(message, fromUser = true), typing = true)
        viewModelScope.launch {
            val reply = agent.send(message).fold(
                onSuccess = {
                    getApplication<Application>().mission(MissionEvent.GEEK_QUESTION)
                    ChatMessage(it, fromUser = false)
                },
                onFailure = { ChatMessage(it.message ?: "Falha ao falar com a IA", fromUser = false, error = true) }
            )
            _state.update { it.copy(messages = it.messages + reply, typing = false) }
        }
    }

    companion object {
        const val ARG_TOPIC = "topic"
        private const val WELCOME =
            "E aí! Sou o Geek 🤓 Pergunte qualquer coisa do universo Marvel: personagens, HQs, " +
                "times, filmes, criadores… Eu confiro tudo na Comic Vine antes de responder."
    }
}

/** O que a ferramenta "meu_perfil" do Geek enxerga: só o que já está no aparelho. */
private fun profileOf(application: Application): () -> Map<String, Any?> = {
    val store = ReadingStore(application, AuthRepository().currentUser?.uid)
    val record = BattleRecordStore(application).get()
    val comics = store.get()
    mapOf(
        "heroi_favorito" to store.preferences().hero?.name,
        "serie_favorita" to store.preferences().series?.name,
        "generos" to store.preferences().genres,
        "hqs_lidas" to comics.filter { it.status == ReadingStatus.READ }.map { it.title to it.rating },
        "lendo" to comics.filter { it.status == ReadingStatus.READING }.map { it.title },
        "filmes" to store.movies().map { it.title to it.rating },
        "batalhas" to mapOf("vitorias" to record.wins, "derrotas" to record.losses)
    )
}
