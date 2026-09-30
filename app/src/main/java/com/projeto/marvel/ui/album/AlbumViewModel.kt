package com.projeto.marvel.ui.album

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.EVOLVE_COST
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.PackType
import com.projeto.marvel.data.Stat
import com.projeto.marvel.data.UpgradeStore
import com.projeto.marvel.data.canEvolve
import com.projeto.marvel.data.goldenChance
import com.projeto.marvel.data.levelFor
import com.projeto.marvel.data.pointsFor
import com.projeto.marvel.data.toFighter
import com.projeto.marvel.data.upgraded
import com.projeto.marvel.data.Rarity
import com.projeto.marvel.data.StickerStore
import com.projeto.marvel.data.openPack
import com.projeto.marvel.data.rarity
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/**
 * [number] = posição no álbum (fixa: lendárias primeiro, depois por nome), como em álbum de banca.
 * [golden] = variante dourada (sorte no pacote ou evoluída).
 */
data class Sticker(
    val character: CharacterSummary,
    val rarity: Rarity,
    val count: Int,
    val number: Int,
    val golden: Boolean = false
)

/**
 * Carta aberta em 3D com o painel de melhorias: [base] = atributos sem nível (chegam da API),
 * [allocation] = pontos que você distribuiu.
 */
data class CardUpgrade(val sticker: Sticker, val base: Fighter? = null, val allocation: Map<Stat, Int> = emptyMap()) {
    val level get() = levelFor(sticker.count)
    val points get() = pointsFor(level)
    val free get() = points - allocation.values.sum()
    val final get() = base?.upgraded(level, allocation, sticker.golden)
}

sealed interface AlbumUiState {
    data object Loading : AlbumUiState

    /**
     * [opened] = figurinhas do pacote que acabou de abrir (a tela faz a abertura); [openId] muda a
     * cada pacote, para a animação não repetir ao voltar para a tela.
     */
    data class Success(
        val stickers: List<Sticker>,
        val packs: Map<PackType, Int>,
        /** Desenho de cada pacote: um herói da raridade garantida (Ouro = lendário…). */
        val packArt: Map<PackType, String?> = emptyMap(),
        val opened: List<Sticker> = emptyList(),
        val openedType: PackType = PackType.BASIC,
        val openId: Int = 0
    ) : AlbumUiState {
        val owned get() = stickers.count { it.count > 0 }
    }

    data class Error(val message: String) : AlbumUiState
}

/** Álbum de figurinhas: o pool é o mesmo dos mais famosos (herói do dia); raridade pela fama. */
class AlbumViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val store: StickerStore = StickerStore(application),
    private val random: Random = Random.Default,
    private val upgrades: UpgradeStore = UpgradeStore(application)
) : AndroidViewModel(application) {

    private val _viewer = MutableStateFlow<CardUpgrade?>(null)
    val viewer: StateFlow<CardUpgrade?> = _viewer.asStateFlow()
    private val bases = mutableMapOf<Int, Fighter>()
    private val _state = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    private var pool: List<CharacterSummary> = emptyList()

    init { load() }

    fun load() {
        _state.value = AlbumUiState.Loading
        viewModelScope.launch {
            pool = repository.popularCharacters().getOrNull().orEmpty()
            _state.value = if (pool.isEmpty()) {
                AlbumUiState.Error("Não deu para buscar os personagens agora.")
            } else {
                success()
            }
        }
    }

    /** Pacotes mudam fora daqui (vitória na Batalha, virada do dia): relê ao voltar para a aba. */
    fun refresh() {
        val current = _state.value as? AlbumUiState.Success ?: return
        _state.value = success().copy(opened = current.opened, openedType = current.openedType, openId = current.openId)
    }

    fun openPack(type: PackType) {
        val current = _state.value as? AlbumUiState.Success ?: return
        if (!store.usePack(type, LocalDate.now())) return
        val picks = openPack(pool, { rarity(it.issueAppearances) }, random, type)
        store.add(picks.map { it.id })
        getApplication<Application>().mission(MissionEvent.PACK_OPENED)
        // Cada figurinha tem uma chancezinha de vir dourada (maior nos pacotes melhores).
        picks.forEach { if (random.nextInt(PERCENT) < goldenChance(type)) upgrades.markGolden(it.id) }
        val album = success()
        val byId = album.stickers.associateBy { it.character.id }
        _state.value = album.copy(
            opened = picks.mapNotNull { byId[it.id] },
            openedType = type,
            openId = current.openId + 1
        )
    }

    /** Abre a carta em 3D e busca os atributos base (os poderes só vêm no detalhe). */
    fun openViewer(sticker: Sticker) {
        val id = sticker.character.id
        _viewer.value = CardUpgrade(sticker, bases[id], upgrades.allocation(id))
        if (bases[id] != null) return
        val url = sticker.character.apiDetailUrl ?: return
        viewModelScope.launch {
            val base = repository.getCharacterDetail(url).getOrNull()?.toFighter() ?: return@launch
            bases[id] = base
            _viewer.value = _viewer.value?.takeIf { it.sticker.character.id == id }?.copy(base = base)
        }
    }

    fun closeViewer() {
        _viewer.value = null
    }

    /** +1/-1 ponto em [stat], sem passar dos pontos do nível nem ficar negativo. */
    fun allocate(stat: Stat, delta: Int) {
        val card = _viewer.value ?: return
        val current = card.allocation[stat] ?: 0
        val next = current + delta
        if (next < 0 || (delta > 0 && card.free < delta)) return
        val allocation = card.allocation + (stat to next)
        upgrades.setAllocation(card.sticker.character.id, allocation)
        if (delta > 0) getApplication<Application>().mission(MissionEvent.UPGRADE_POINT)
        _viewer.value = card.copy(allocation = allocation)
    }

    /** Junta [EVOLVE_COST] figurinhas numa dourada: gasta as repetidas (o nível cai junto). */
    fun evolve() {
        val card = _viewer.value?.takeIf { canEvolve(it.sticker.count, it.sticker.golden) } ?: return
        val id = card.sticker.character.id
        store.remove(id, EVOLVE_COST - 1)
        upgrades.markGolden(id)
        refresh()
        (_state.value as? AlbumUiState.Success)?.stickers?.firstOrNull { it.character.id == id }
            ?.let { _viewer.value = card.copy(sticker = it) }
    }

    private fun success(): AlbumUiState.Success {
        val counts = store.counts()
        val golden = upgrades.goldenIds()
        val stickers = pool.map { it to rarity(it.issueAppearances) }
            .sortedWith(compareByDescending<Pair<CharacterSummary, Rarity>> { it.second }.thenBy { it.first.name })
            .mapIndexed { index, (character, rarity) ->
                val id = character.id
                Sticker(character, rarity, counts[id] ?: 0, number = index + 1, golden = id in golden)
            }
        val art = PackType.entries.associateWith { type ->
            stickers.firstOrNull { it.rarity == type.guaranteed }?.character?.image?.mediumUrl
        }
        return AlbumUiState.Success(stickers, store.packs(LocalDate.now()), art)
    }
}

private const val PERCENT = 100
