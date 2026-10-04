package com.projeto.marvel.ui.comicmaker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.R
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val COMIC_PANELS = 3

/** Fundos de quadro disponíveis (o desenho de cada um fica no Fragment). */
const val COMIC_BACKGROUNDS = 5

/**
 * Um quadro da página: [hero] desenhado, [background] (índice do fundo), [speech] = fala no balão
 * (vazia = sem balão) e onde o balão foi arrastado ([balloonX]/[balloonY], fração do quadro).
 */
data class ComicPanel(
    val hero: CharacterSummary? = null,
    val background: Int = 0,
    val speech: String = "",
    val balloonX: Float = DEFAULT_BALLOON_X,
    val balloonY: Float = DEFAULT_BALLOON_Y
)

private const val DEFAULT_BALLOON_X = 0.05f
private const val DEFAULT_BALLOON_Y = 0.06f

data class ComicMakerState(
    val panels: List<ComicPanel> = List(COMIC_PANELS) { ComicPanel(background = it) },
    val heroes: List<CharacterSummary> = emptyList()
)

/** "Monte sua HQ": página de 3 quadros; em cada um você escolhe o herói, o fundo e a fala. */
class ComicMakerViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository()
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ComicMakerState())
    val state: StateFlow<ComicMakerState> = _state.asStateFlow()

    init {
        // Falas de exemplo: a página abre como uma tirinha pronta, para editar.
        val lines = listOf(R.string.comic_maker_line1, R.string.comic_maker_line2, R.string.comic_maker_line3)
        _state.update { state ->
            state.copy(
                panels = state.panels.mapIndexed { index, panel ->
                    panel.copy(speech = application.getString(lines[index]))
                }
            )
        }
        viewModelScope.launch {
            val heroes = repository.popularCharacters().getOrNull().orEmpty().filter { it.image?.mediumUrl != null }
            // Começa com uma cena pronta (os 3 mais famosos), para a página nunca abrir vazia.
            _state.update { state ->
                state.copy(
                    heroes = heroes.sortedBy { it.name },
                    panels = state.panels.mapIndexed { index, panel ->
                        panel.copy(hero = panel.hero ?: heroes.getOrNull(index))
                    }
                )
            }
        }
    }

    fun setHero(panel: Int, hero: CharacterSummary) = edit(panel) { it.copy(hero = hero) }

    fun nextBackground(panel: Int) = edit(panel) { it.copy(background = (it.background + 1) % COMIC_BACKGROUNDS) }

    fun setSpeech(panel: Int, text: String) = edit(panel) { it.copy(speech = text.trim()) }

    fun moveBalloon(panel: Int, x: Float, y: Float) =
        edit(panel) { it.copy(balloonX = x.coerceIn(0f, 1f), balloonY = y.coerceIn(0f, 1f)) }

    /** A página foi salva ou compartilhada: conta na missão. */
    fun published() = getApplication<Application>().mission(MissionEvent.COMIC_MADE)

    private fun edit(index: Int, change: (ComicPanel) -> ComicPanel) = _state.update { state ->
        state.copy(panels = state.panels.mapIndexed { i, panel -> if (i == index) change(panel) else panel })
    }
}
