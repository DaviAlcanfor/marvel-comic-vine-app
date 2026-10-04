package com.projeto.marvel.ui.trunfo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.GameRecord
import com.projeto.marvel.data.GameRecordStore
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.PackType
import com.projeto.marvel.data.Stat
import com.projeto.marvel.data.StickerStore
import com.projeto.marvel.data.UpgradeStore
import com.projeto.marvel.data.levelFor
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.toFighter
import com.projeto.marvel.data.upgraded
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Mínimo de figurinhas para montar um baralho. */
const val TRUNFO_MIN_CARDS = 3

sealed interface TrunfoUiState {
    data object Loading : TrunfoUiState

    /**
     * [round] = a rodada que acabou de ser jogada (as duas cartas à mostra) até o "Próxima";
     * null = esperando a escolha (sua, ou a CPU pensando). [reward] = pacote da vitória.
     */
    data class Playing(
        val game: TrunfoGame,
        val round: TrunfoRound? = null,
        val reward: PackType? = null
    ) : TrunfoUiState

    data class Error(val message: String) : TrunfoUiState
}

/** Super Trunfo com as suas figurinhas (nível e pontos contam) contra cartas sorteadas da CPU. */
class TrunfoViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val stickers: StickerStore = StickerStore(application),
    private val upgrades: UpgradeStore = UpgradeStore(application),
    private val records: GameRecordStore = GameRecordStore(application),
    private val random: Random = Random.Default
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<TrunfoUiState>(TrunfoUiState.Loading)
    val state: StateFlow<TrunfoUiState> = _state.asStateFlow()

    private var cpuTurn: Job? = null

    init { load() }

    fun load() {
        _state.value = TrunfoUiState.Loading
        viewModelScope.launch {
            val pool = repository.popularCharacters().getOrNull().orEmpty().filter { it.apiDetailUrl != null }
            val counts = stickers.counts()
            val owned = pool.filter { (counts[it.id] ?: 0) > 0 }
            if (owned.size < TRUNFO_MIN_CARDS) {
                _state.value = TrunfoUiState.Error(
                    "Você precisa de pelo menos $TRUNFO_MIN_CARDS figurinhas no Álbum para montar o baralho."
                )
                return@launch
            }
            val hand = owned.shuffled(random).take(TRUNFO_HAND)
            val rivals = pool.filter { it !in hand }.shuffled(random).take(hand.size)
            val mine = cards(hand) { card -> card.mine(counts[card.id] ?: 0) }
            val theirs = cards(rivals) { it }
            _state.value = if (mine.size < TRUNFO_MIN_CARDS || theirs.size < TRUNFO_MIN_CARDS) {
                TrunfoUiState.Error("Não deu para buscar os atributos agora.")
            } else {
                val size = minOf(mine.size, theirs.size)
                TrunfoUiState.Playing(TrunfoGame(mine.take(size), theirs.take(size)))
            }
        }
    }

    /** Atributos vêm dos poderes (só no detalhe): busca as cartas em paralelo. */
    private suspend fun cards(characters: List<CharacterSummary>, adjust: (TrunfoCard) -> TrunfoCard) =
        coroutineScope {
            characters.map { character ->
                async {
                    val url = character.apiDetailUrl ?: return@async null
                    val fighter = repository.getCharacterDetail(url).getOrNull()?.toFighter() ?: return@async null
                    adjust(TrunfoCard(character.id, character.name, character.image?.mediumUrl, fighter.stats))
                }
            }.awaitAll().filterNotNull()
        }

    /** A sua carta: nível pelas repetidas, pontos distribuídos e bônus da Divina. */
    private fun TrunfoCard.mine(count: Int): TrunfoCard {
        val fighter = Fighter(id = id, name = name, imageUrl = imageUrl, stats = stats, moves = emptyList())
        val boosted = fighter.upgraded(levelFor(count), upgrades.allocation(id), upgrades.isGolden(id))
        return copy(stats = boosted.stats)
    }

    /** Você escolhe o atributo (na sua vez). */
    fun choose(stat: Stat) {
        val playing = _state.value as? TrunfoUiState.Playing ?: return
        if (playing.round != null || playing.game.over || playing.game.chooser != TrunfoSide.PLAYER) return
        resolve(playing, playing.game.play(stat))
    }

    /** Próxima rodada; na vez da CPU, ela "pensa" um instante e escolhe o atributo mais forte. */
    fun next() {
        val playing = _state.value as? TrunfoUiState.Playing ?: return
        val game = playing.round?.next ?: return
        _state.value = playing.copy(game = game, round = null)
        if (!game.over && game.chooser == TrunfoSide.CPU) cpuPlays()
    }

    private fun cpuPlays() {
        cpuTurn?.cancel()
        cpuTurn = viewModelScope.launch {
            delay(CPU_THINK_MILLIS)
            val now = _state.value as? TrunfoUiState.Playing ?: return@launch
            resolve(now, now.game.play(now.game.cpu.first().bestStat()))
        }
    }

    private fun resolve(playing: TrunfoUiState.Playing, round: TrunfoRound) {
        val won = round.next.over && round.next.winner == TrunfoSide.PLAYER
        if (won) {
            stickers.addPack(PackType.SILVER)
            records.increment(GameRecord.TRUNFO_WINS)
            getApplication<Application>().mission(MissionEvent.TRUNFO_WIN)
        }
        _state.value = playing.copy(round = round, reward = if (won) PackType.SILVER else null)
    }

    private companion object {
        const val CPU_THINK_MILLIS = 1_100L
    }
}
