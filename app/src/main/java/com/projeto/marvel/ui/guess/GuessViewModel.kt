package com.projeto.marvel.ui.guess

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AchievementStore
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed interface GuessUiState {
    data object Loading : GuessUiState

    /**
     * [level] = quão nítida está a foto (ver [PIXEL_SIZES]); [wrong] = opções já erradas nesta
     * rodada. A sequência só cresce com acerto de primeira.
     */
    data class Playing(
        val answer: CharacterSummary,
        val options: List<CharacterSummary>,
        val level: Int = 0,
        val wrong: Set<Int> = emptySet(),
        val solved: Boolean = false,
        val streak: Int = 0,
        val best: Int = 0,
        val score: Int = 0,
        val gained: Int = 0
    ) : GuessUiState

    data class Error(val message: String) : GuessUiState
}

/** "Quem é esse herói?": foto pixelada que clareia com o tempo e a cada erro; 4 opções. */
class GuessViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val achievements: AchievementStore = AchievementStore(application),
    private val random: Random = Random.Default
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<GuessUiState>(GuessUiState.Loading)
    val state: StateFlow<GuessUiState> = _state.asStateFlow()

    private var pool: List<CharacterSummary> = emptyList()
    private var reveal: Job? = null

    init { load() }

    fun load() {
        _state.value = GuessUiState.Loading
        viewModelScope.launch {
            pool = repository.popularCharacters().getOrNull().orEmpty().filter { it.image?.mediumUrl != null }
            next()
        }
    }

    fun next() {
        val last = _state.value as? GuessUiState.Playing
        val round = pickRound(pool, random, avoid = last?.answer)
        if (round == null) {
            _state.value = GuessUiState.Error("Não deu para buscar os personagens agora.")
            return
        }
        _state.value = GuessUiState.Playing(
            answer = round.first,
            options = round.second,
            streak = last?.streak ?: 0,
            best = achievements.bestGuessStreak(),
            score = last?.score ?: 0
        )
        reveal?.cancel()
        reveal = viewModelScope.launch {
            var game = _state.value as? GuessUiState.Playing
            while (game != null && !game.solved && game.level < CLEAR_LEVEL) {
                delay(REVEAL_MILLIS)
                // Um erro durante a espera já pode ter clareado a foto: nunca passa da nítida.
                game = (_state.value as? GuessUiState.Playing)?.takeIf { !it.solved }
                    ?.let { it.copy(level = (it.level + 1).coerceAtMost(CLEAR_LEVEL)) }
                game?.let { _state.value = it }
            }
        }
    }

    fun guess(id: Int) {
        val game = _state.value as? GuessUiState.Playing ?: return
        if (game.solved || id in game.wrong) return
        _state.value = if (id == game.answer.id) {
            reveal?.cancel()
            val streak = if (game.wrong.isEmpty()) game.streak + 1 else 0
            achievements.recordGuessStreak(streak)
            getApplication<Application>().mission(MissionEvent.GUESS_RIGHT)
            val points = guessPoints(game.level)
            game.copy(
                solved = true,
                streak = streak,
                best = maxOf(game.best, streak),
                score = game.score + points,
                gained = points
            )
        } else {
            game.copy(wrong = game.wrong + id, level = (game.level + 1).coerceAtMost(CLEAR_LEVEL), streak = 0)
        }
    }

    private companion object {
        const val REVEAL_MILLIS = 3_000L
    }
}
