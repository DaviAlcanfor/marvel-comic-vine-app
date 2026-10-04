package com.projeto.marvel.ui.quote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.GameRecord
import com.projeto.marvel.data.GameRecordStore
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.PackType
import com.projeto.marvel.data.StickerStore
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.ui.guess.pickRound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface QuoteUiState {
    data object Loading : QuoteUiState

    /** [quote] = o resumo com o nome escondido; [pack] = acabou de ganhar um pacote (a cada 5 seguidos). */
    data class Playing(
        val answer: CharacterSummary,
        val options: List<CharacterSummary>,
        val quote: String,
        val picked: Int? = null,
        val streak: Int = 0,
        val best: Int = 0,
        val pack: Boolean = false
    ) : QuoteUiState {
        val solved get() = picked != null
        val right get() = picked == answer.id
    }

    data class Error(val message: String) : QuoteUiState
}

/** "Quem disse?": o resumo do personagem num balão, com o nome dele escondido; 4 opções, uma chance. */
class QuoteViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val records: GameRecordStore = GameRecordStore(application),
    private val stickers: StickerStore = StickerStore(application),
    private val random: Random = Random.Default
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<QuoteUiState>(QuoteUiState.Loading)
    val state: StateFlow<QuoteUiState> = _state.asStateFlow()

    private var pool: List<CharacterSummary> = emptyList()

    init { load() }

    fun load() {
        _state.value = QuoteUiState.Loading
        viewModelScope.launch {
            pool = repository.popularCharacters().getOrNull().orEmpty().filter { !it.deck.isNullOrBlank() }
            next()
        }
    }

    fun next() {
        val last = _state.value as? QuoteUiState.Playing
        val round = pickRound(pool, random, avoid = last?.answer)
        if (round == null) {
            _state.value = QuoteUiState.Error("Não deu para buscar os personagens agora.")
            return
        }
        val (answer, options) = round
        _state.value = QuoteUiState.Playing(
            answer = answer,
            options = options,
            quote = maskNames(answer.deck.orEmpty(), answer.name, answer.realName),
            streak = if (last?.right == true) last.streak else 0,
            best = records.best(GameRecord.QUOTE_STREAK) ?: 0
        )
    }

    fun pick(id: Int) {
        val game = _state.value as? QuoteUiState.Playing ?: return
        if (game.solved) return
        if (id != game.answer.id) {
            _state.value = game.copy(picked = id, streak = 0)
        } else {
            right(game, id)
        }
    }

    private fun right(game: QuoteUiState.Playing, id: Int) {
        val streak = game.streak + 1
        getApplication<Application>().mission(MissionEvent.QUOTE_RIGHT)
        records.record(GameRecord.QUOTE_STREAK, streak)
        val pack = earnsPack(streak)
        if (pack) stickers.addPack(PackType.BASIC)
        _state.value = game.copy(picked = id, streak = streak, best = maxOf(game.best, streak), pack = pack)
    }
}
