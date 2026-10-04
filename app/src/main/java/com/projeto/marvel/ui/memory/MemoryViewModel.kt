package com.projeto.marvel.ui.memory

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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface MemoryUiState {
    data object Loading : MemoryUiState

    /** [images] = foto de cada personagem da mesa; [reward] = pacote ganho ao terminar. */
    data class Playing(
        val game: MemoryGame,
        val images: Map<Int, String?>,
        val best: Int?,
        val reward: PackType? = null,
        val newRecord: Boolean = false
    ) : MemoryUiState

    data class Error(val message: String) : MemoryUiState
}

/** Jogo da Memória com as figurinhas (as suas, se tiver 8; senão as dos mais famosos). */
class MemoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val stickers: StickerStore = StickerStore(application),
    private val records: GameRecordStore = GameRecordStore(application),
    private val random: Random = Random.Default
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<MemoryUiState>(MemoryUiState.Loading)
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    private var images: Map<Int, String?> = emptyMap()
    private var ids: List<Int> = emptyList()
    private var unflip: Job? = null

    init { load() }

    fun load() {
        _state.value = MemoryUiState.Loading
        viewModelScope.launch {
            val pool = repository.popularCharacters().getOrNull().orEmpty().filter { it.image?.mediumUrl != null }
            images = pool.associate { it.id to it.image?.mediumUrl }
            val owned = stickers.counts().filterValues { it > 0 }.keys.filter { it in images }
            ids = if (owned.size >= MEMORY_PAIRS) owned else images.keys.toList()
            if (ids.size < MEMORY_PAIRS) {
                _state.value = MemoryUiState.Error("Não deu para buscar os personagens agora.")
            } else {
                restart()
            }
        }
    }

    fun restart() {
        if (ids.size < MEMORY_PAIRS) return load()
        unflip?.cancel()
        val faces = memoryDeck(ids, random)
        _state.value = MemoryUiState.Playing(
            MemoryGame(faces),
            faces.toSet().associateWith { images[it] },
            records.best(GameRecord.MEMORY_MOVES)
        )
    }

    fun flip(index: Int) {
        val playing = _state.value as? MemoryUiState.Playing ?: return
        if (playing.game.done) return
        unflip?.cancel()
        val game = playing.game.flip(index)
        _state.value = if (game.done) finish(playing, game) else playing.copy(game = game)
        if (game.mismatch) {
            unflip = viewModelScope.launch {
                delay(UNFLIP_MILLIS)
                val now = _state.value as? MemoryUiState.Playing ?: return@launch
                _state.value = now.copy(game = now.game.hideMismatch())
            }
        }
    }

    /** Fim: pacote Prata com poucas jogadas (senão Básico), missão e recorde. */
    private fun finish(playing: MemoryUiState.Playing, game: MemoryGame): MemoryUiState.Playing {
        val reward = if (game.moves <= MEMORY_SILVER_MOVES) PackType.SILVER else PackType.BASIC
        stickers.addPack(reward)
        getApplication<Application>().mission(MissionEvent.MEMORY_DONE)
        val newRecord = records.record(GameRecord.MEMORY_MOVES, game.moves)
        val best = records.best(GameRecord.MEMORY_MOVES)
        return playing.copy(game = game, reward = reward, newRecord = newRecord, best = best)
    }

    private companion object {
        const val UNFLIP_MILLIS = 900L
    }
}
