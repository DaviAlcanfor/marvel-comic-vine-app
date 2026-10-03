package com.projeto.marvel.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.CatalogRepository
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.Favorite
import com.projeto.marvel.data.Mission
import com.projeto.marvel.data.MissionProgress
import com.projeto.marvel.data.MissionStore
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.Movie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Página inicial. Saudação, estante e favorito vêm do aparelho na hora; o herói do dia vem da
 * API e chega depois (null enquanto carrega ou se falhar — o card só some, a Home segue).
 */
data class HomeUiState(
    val userName: String?,
    val userPhoto: String? = null,
    val reading: List<ReadComic>,
    val favoriteHero: Favorite?,
    /** Últimas resenhas do usuário: a da HQ e a do filme mais recentes (com capa/pôster). */
    val heroOfTheDay: CharacterSummary? = null,
    val heroMovies: List<Movie> = emptyList(),
    /** Populares que estrearam nas HQs neste dia do ano. */
    val debutedToday: List<CharacterSummary> = emptyList(),
    val dailyTrail: DailyTrail? = null,
    val missions: List<MissionProgress> = emptyList()
)

/** Resenha na Início; [movie] muda o ícone (🎬 × 📚). */

/** Trilha do dia: um time por dia e os adversários que a trilha vai ter (do menos famoso ao chefe). */
data class DailyTrail(val teamName: String, val teamUrl: String, val rivals: List<CharacterSummary>)

class HomeViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val auth: AuthRepository = AuthRepository(),
    private val store: ReadingStore = ReadingStore(application, auth.currentUser?.uid),
    private val catalog: CatalogRepository = CatalogRepository(),
    private val missions: MissionStore = MissionStore(application)
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(local())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val all = repository.popularCharacters().getOrNull().orEmpty()
            val today = LocalDate.now()
            val hero = heroOfTheDay(all, today) ?: return@launch
            val debuted = all.filter { debutedOn(it.birth, today) }
            _state.update { it.copy(heroOfTheDay = hero, debutedToday = debuted) }
            loadHeroMovies(hero)
        }
        viewModelScope.launch { loadDailyTrail() }
    }

    /** Pega o pacote de uma missão cumprida (vai para o Álbum). */
    fun claim(mission: Mission) {
        if (missions.claim(mission)) refresh()
    }

    /** Estante, favorito e missões mudam em outras telas: relê ao voltar para a Home. */
    fun refresh() {
        _state.update {
            local().copy(
                heroOfTheDay = it.heroOfTheDay,
                heroMovies = it.heroMovies,
                debutedToday = it.debutedToday,
                dailyTrail = it.dailyTrail
            )
        }
    }

    /** Os filmes só vêm no detalhe do personagem; os pôsteres, numa busca por id. */
    private suspend fun loadHeroMovies(hero: CharacterSummary) {
        val url = hero.apiDetailUrl ?: return
        val ids = repository.getCharacterDetail(url).getOrNull()?.movies.orEmpty().map { it.id }
        if (ids.isEmpty()) return
        val movies = catalog.moviesByIds(ids).getOrNull().orEmpty()
        _state.update { it.copy(heroMovies = movies) }
    }

    /** Mesmos adversários que a trilha vai ter (ver `getFighters` com teamUrl): os mais famosos. */
    private suspend fun loadDailyTrail() {
        val url = DAILY_TEAMS[LocalDate.now().toEpochDay().mod(DAILY_TEAMS.size)]
        val team = repository.getTeamDetail(url).getOrNull() ?: return
        val rivals = repository.charactersByIds(team.members.orEmpty().map { it.id }).getOrNull().orEmpty()
            .take(ComicVineRepository.GAUNTLET_SIZE)
            .reversed()
        if (rivals.isEmpty()) return
        _state.update { it.copy(dailyTrail = DailyTrail(team.name, url, rivals)) }
    }

    private fun local(): HomeUiState {
        val shelf = store.get()
        return HomeUiState(
            userName = auth.currentUser?.name?.takeIf { it.isNotBlank() }?.substringBefore(' '),
            userPhoto = auth.currentUser?.photoUrl,
            reading = shelf.filter { it.status == ReadingStatus.READING },
            favoriteHero = store.preferences().hero,
            missions = missions.missions()
        )
    }

    private companion object {
        /** Times e grupos de vilões da Marvel que se revezam na trilha do dia. */
        private val DAILY_TEAMS = listOf(
            "4060-3806", "4060-3173", "4060-3804", "4060-25956", "4060-40429", "4060-7582",
            "4060-26333", "4060-23977", "4060-40421", "4060-11427", "4060-13357", "4060-42520"
        ).map { "https://comicvine.gamespot.com/api/team/$it/" }
    }
}
