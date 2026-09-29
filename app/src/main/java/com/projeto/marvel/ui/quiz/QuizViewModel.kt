package com.projeto.marvel.ui.quiz

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AchievementStore
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.Favorite
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.Stat
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.toFighter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface QuizUiState {
    data class Question(val index: Int) : QuizUiState

    /** Respondeu tudo, mas os candidatos ainda estão chegando da API. */
    data object Loading : QuizUiState

    data class Result(val character: CharacterSummary, val fighter: Fighter) : QuizUiState

    data class Error(val message: String) : QuizUiState
}

/**
 * Quiz "Que herói é você?". Os candidatos são carregados já na abertura (os atributos saem dos
 * poderes, que só vêm no detalhe), enquanto o usuário responde.
 */
class QuizViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val store: ReadingStore = ReadingStore(application, AuthRepository().currentUser?.uid),
    private val achievements: AchievementStore = AchievementStore(application)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Question(0))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    private val answers = mutableListOf<Stat>()
    private val candidates = viewModelScope.async {
        CANDIDATES.map { url ->
            async { repository.getCharacterDetail(url).getOrNull()?.let { it to it.toFighter() } }
        }.awaitAll().filterNotNull()
    }

    fun answer(stat: Stat) {
        answers += stat
        if (answers.size < QUESTIONS.size) {
            _state.value = QuizUiState.Question(answers.size)
            return
        }
        _state.value = QuizUiState.Loading
        viewModelScope.launch {
            val pool = candidates.await()
            val best = closestFighter(answers, pool.map { it.second })
            val character = pool.firstOrNull { it.second == best }?.first
            _state.value = if (character != null && best != null) {
                achievements.markQuiz()
                getApplication<Application>().mission(MissionEvent.QUIZ_DONE)
                QuizUiState.Result(character, best)
            } else {
                QuizUiState.Error("Não deu para consultar os personagens agora.")
            }
        }
    }

    fun restart() {
        answers.clear()
        _state.value = QuizUiState.Question(0)
    }

    /** Resultado vira o herói preferido do Perfil. */
    fun setAsHero(character: CharacterSummary) {
        val hero = Favorite(character.id, character.name, character.image?.mediumUrl, character.apiDetailUrl)
        store.savePreferences(store.preferences().copy(hero = hero))
    }

    private companion object {
        /** Homem-Aranha, Hulk, Thor, Doutor Estranho, Wolverine, Homem de Ferro, Capitão América,
         * Viúva Negra, Tempestade, Pantera Negra, Feiticeira Escarlate e Deadpool. */
        val CANDIDATES = listOf(1443, 2267, 2268, 1456, 1440, 1455, 1442, 3200, 1444, 1477, 1466, 7606)
            .map { "https://comicvine.gamespot.com/api/character/4005-$it/" }
    }
}
