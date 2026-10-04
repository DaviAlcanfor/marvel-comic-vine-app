package com.projeto.marvel.ui.battle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.Achievement
import com.projeto.marvel.data.AchievementStore
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.BattleRecord
import com.projeto.marvel.data.BattleRecordStore
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.MissionEvent
import com.projeto.marvel.data.mission
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.PackType
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.UpgradeStore
import com.projeto.marvel.data.leveled
import com.projeto.marvel.data.upgraded
import com.projeto.marvel.data.levelFor
import com.projeto.marvel.data.rivalLevel
import com.projeto.marvel.data.StickerStore
import com.projeto.marvel.data.newlyUnlocked
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class Side { PLAYER, CPU }

/** [id] cresce a cada evento: a View anima só eventos que ainda não viu. */
data class BattleEvent(
    val id: Int,
    val side: Side,
    val moveName: String?,
    val outcome: Outcome,
    val amount: Int,
    val moveType: MoveType? = null,
    val critical: Boolean = false,
    val heal: Int = 0,
    /** Ação a mais de quem é bem mais rápido (ver [playTurn]). */
    val extra: Boolean = false
)

/**
 * Posição na trilha de um time: luta [number] de [total] (a última é contra o chefe). [rivals]:
 * todos os adversários, em ordem, para desenhar a trilha.
 */
data class Stage(val number: Int, val total: Int, val teamName: String?, val rivals: List<Fighter> = emptyList()) {
    val isLast get() = number == total
}

/** Resumo da luta do ponto de vista do jogador (painel de vitória/derrota). */
data class FightStats(
    val dealt: Int = 0,
    val taken: Int = 0,
    val biggestHit: Int = 0,
    val criticals: Int = 0,
    val ultimates: Int = 0
) {
    fun plus(step: TurnStep): FightStats {
        // No veneno o passo é de quem sofre; nos golpes, de quem ataca.
        val playerHit = (step.side == Side.PLAYER) != (step.outcome == Outcome.POISON_TICK)
        val ultimate = step.outcome == Outcome.ULTIMATE
        return when {
            step.amount == 0 || step.outcome == Outcome.HEAL -> this
            !playerHit -> copy(taken = taken + step.amount)
            else -> copy(
                dealt = dealt + step.amount,
                biggestHit = maxOf(biggestHit, step.amount),
                criticals = criticals + if (step.critical && !ultimate) 1 else 0,
                ultimates = ultimates + if (ultimate) 1 else 0
            )
        }
    }
}

sealed interface BattleUiState {
    data object Loading : BattleUiState

    /**
     * [busy] = turno em andamento (golpes bloqueados até as animações terminarem).
     * [stage] = só na trilha de um time; null na luta avulsa.
     */
    data class Success(
        val player: Combatant,
        val cpu: Combatant,
        val record: BattleRecord,
        val event: BattleEvent? = null,
        val busy: Boolean = false,
        val stage: Stage? = null,
        /** Turno atual (1 = o primeiro, ainda não jogado). */
        val turn: Int = 1,
        val stats: FightStats = FightStats(),
        /** Conquistas desbloqueadas nesta luta (painel de resultado). */
        val newAchievements: List<Achievement> = emptyList(),
        /** 2 jogadores no mesmo aparelho: [Side.CPU] é o segundo jogador. */
        val pvp: Boolean = false,
        /** Quem escolhe o golpe agora (no PvP os dois escolhem antes do turno rodar). */
        val choosing: Side = Side.PLAYER,
        /** 3×3: o resto do trio de cada lado (os caídos ficam, com 0 de vida). Vazio na luta 1×1. */
        val playerBench: List<Combatant> = emptyList(),
        val cpuBench: List<Combatant> = emptyList()
    ) : BattleUiState {
        /** Só acaba quando o ativo caiu e não sobrou ninguém de pé no banco. */
        val winner: Side?
            get() = when {
                player.hp == 0 && playerBench.none { it.hp > 0 } -> Side.CPU
                cpu.hp == 0 && cpuBench.none { it.hp > 0 } -> Side.PLAYER
                else -> null
            }

        fun combatant(side: Side) = if (side == Side.PLAYER) player else cpu

        fun bench(side: Side) = if (side == Side.PLAYER) playerBench else cpuBench

        /** [incoming] (do banco) vira o ativo e o ativo vai para o lugar dele no banco. */
        fun swap(side: Side, incoming: Combatant): Success {
            val bench = bench(side).map { if (it === incoming) combatant(side) else it }
            val swapped = with(side, incoming)
            return if (side == Side.PLAYER) swapped.copy(playerBench = bench) else swapped.copy(cpuBench = bench)
        }

        fun with(side: Side, combatant: Combatant) =
            if (side == Side.PLAYER) copy(player = combatant) else copy(cpu = combatant)
    }

    data class Error(val message: String) : BattleUiState
}

class BattleViewModel @JvmOverloads constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val repository: ComicVineRepository = ComicVineRepository(),
    private val records: BattleRecordStore = BattleRecordStore(application),
    private val random: Random = Random.Default,
    private val achievements: AchievementStore = AchievementStore(application)
) : AndroidViewModel(application) {

    private val reading = ReadingStore(application, AuthRepository().currentUser?.uid)
    private val stickers = StickerStore(application)
    private val app = application
    private val upgrades = UpgradeStore(application)

    private val _state = MutableStateFlow<BattleUiState>(BattleUiState.Loading)
    val state: StateFlow<BattleUiState> = _state.asStateFlow()

    private val playerUrl: String = checkNotNull(savedStateHandle["playerUrl"])

    // 3×3: urls do trio do jogador, separadas por vírgula (sem Safe Args, só String no Bundle).
    private val playerUrls: List<String>? = savedStateHandle.get<String>("playerUrls")?.split(",")
    private val opponentUrl: String? = savedStateHandle["opponentUrl"]
    private val teamUrl: String? = savedStateHandle["teamUrl"]
    private val teamName: String? = savedStateHandle["teamName"]
    private val pvp: Boolean = savedStateHandle["pvp"] ?: false
    private var pendingMove: Move? = null

    // Luta avulsa = trilha de um adversário só. No 3×3, [opponents] é o trio rival inteiro.
    private var players: List<Fighter> = emptyList()
    private var opponents: List<Fighter> = emptyList()
    private var stageIndex = 0
    private var eventId = 0
    private var turn: Job? = null

    init { load() }

    fun load() {
        turn?.cancel()
        _state.value = BattleUiState.Loading
        viewModelScope.launch {
            repository.getFighters(playerUrls ?: listOf(playerUrl), opponentUrl, teamUrl).fold(
                onSuccess = { (players, opponents) ->
                    val counts = stickers.counts()
                    val team = players.map {
                        it.upgraded(levelFor(counts[it.id] ?: 0), upgrades.allocation(it.id), upgrades.isGolden(it.id))
                    }
                    this@BattleViewModel.players = team
                    this@BattleViewModel.opponents = opponents.map { it.leveled(rivalLevel(team, it.boss)) }
                    startStage(0)
                },
                onFailure = { _state.value = BattleUiState.Error(it.message ?: "Falha ao carregar os lutadores") }
            )
        }
    }

    /**
     * Botão do fim da luta. Avulsa: revanche (o adversário sorteado não muda). Trilha: vitória
     * avança para o próximo membro (depois do chefe, recomeça); derrota repete a mesma luta.
     */
    fun rematch() {
        val game = _state.value as? BattleUiState.Success ?: return
        val won = game.winner == Side.PLAYER
        startStage(if (won && game.stage != null) (stageIndex + 1) % opponents.size else stageIndex)
    }

    private fun startStage(index: Int) {
        if (players.isEmpty()) return
        turn?.cancel()
        stageIndex = index
        _state.value = BattleUiState.Success(
            Combatant(players.first()),
            Combatant(opponents[index]),
            records.get(),
            stage = teamUrl?.let { Stage(index + 1, opponents.size, teamName, opponents) },
            pvp = pvp,
            playerBench = players.drop(1).map(::Combatant),
            cpuBench = if (playerUrls != null) opponents.drop(1).map(::Combatant) else emptyList()
        )
    }

    /** 3×3: troca pelo próximo de pé do banco. Gasta a vez: a CPU ataca quem entrou. */
    fun swap() {
        val start = (_state.value as? BattleUiState.Success)
            ?.takeIf { !it.busy && it.winner == null && !it.pvp }
            ?: return
        start.playerBench.firstOrNull { it.hp > 0 }
            ?.let { runTurn(start, playerMove = null, cpuChoice = null, swapIn = it) }
    }

    /**
     * Golpe escolhido; [boost] = carga do sacudir ([shakeBoost], só contra a CPU). No PvP o do P1
     * fica guardado até o P2 escolher o dele.
     */
    fun use(move: Move, boost: Int = 0) {
        val start = _state.value as? BattleUiState.Success ?: return
        if (start.busy || start.winner != null) return
        when {
            !start.pvp -> runTurn(start.copy(player = start.player.copy(charge = boost)), move, cpuChoice = null)
            start.choosing == Side.PLAYER -> {
                pendingMove = move
                _state.value = start.copy(choosing = Side.CPU)
            }
            else -> pendingMove?.let { runTurn(start, it, cpuChoice = move) }
        }
    }

    /** Um turno (regras em [playTurn]): cada passo vira um evento animado, com pausa entre eles. */
    private fun runTurn(start: BattleUiState.Success, playerMove: Move?, cpuChoice: Move?, swapIn: Combatant? = null) {
        // Marca ocupado antes de lançar: dois toques rápidos não podem iniciar dois turnos.
        _state.value = start.copy(busy = true)
        turn = viewModelScope.launch {
            val before = achievements.progress(records.get(), reading)
            var game = start.copy(busy = true)
            if (swapIn != null) game = swapStep(game, Side.PLAYER, swapIn)
            for (step in playTurn(game.player, game.cpu, playerMove, random, cpuChoice)) {
                game = game.copy(stats = game.stats.plus(step))
                if (step.outcome == Outcome.ULTIMATE && step.side == Side.PLAYER && !start.pvp) {
                    achievements.addUltimate()
                    app.mission(MissionEvent.ULTIMATE)
                }
                val event = BattleEvent(
                    id = ++eventId,
                    side = step.side,
                    moveName = step.move?.name,
                    outcome = step.outcome,
                    amount = step.amount,
                    moveType = step.move?.type,
                    critical = step.critical,
                    heal = step.heal,
                    extra = step.extra
                )
                game = saveIfOver(game.copy(player = step.player, cpu = step.cpu, event = event))
                emitStep(game, if (step.outcome == Outcome.ULTIMATE) ULTIMATE_STEP_MILLIS else STEP_MILLIS)
            }
            game = replaceFallen(game)
            if (game.winner == Side.PLAYER && game.stage?.isLast == true) {
                game.stage?.teamName?.let(achievements::addGauntlet)
            }
            val unlocked = newlyUnlocked(before, achievements.progress(records.get(), reading))
            _state.value = game.copy(
                busy = false,
                choosing = Side.PLAYER,
                turn = if (game.winner == null) game.turn + 1 else game.turn,
                newAchievements = unlocked
            )
        }
    }

    /**
     * Grava o resultado assim que alguém vence — não no fim do turno, que pode ser cancelado
     * por uma revanche durante a última pausa de animação. PvP não conta no placar.
     */
    private fun saveIfOver(game: BattleUiState.Success): BattleUiState.Success {
        if (game.pvp && game.winner != null) app.mission(MissionEvent.PVP_PLAYED)
        val winner = game.winner?.takeIf { !game.pvp } ?: return game
        // Vitória vale pacote: Ouro fechando a trilha ou no 3×3, Prata no resto.
        if (winner == Side.PLAYER) {
            val gauntlet = game.stage?.isLast == true
            val squad = game.playerBench.isNotEmpty()
            stickers.addPack(if (gauntlet || squad) PackType.GOLD else PackType.SILVER)
            app.mission(MissionEvent.BATTLE_WIN)
            if (squad) app.mission(MissionEvent.SQUAD_WIN)
            if (gauntlet) app.mission(MissionEvent.GAUNTLET_DONE)
        }
        return game.copy(record = records.add(won = winner == Side.PLAYER))
    }

    /** 3×3: quem caiu dá lugar ao do banco com mais vida (dos dois lados). */
    private suspend fun replaceFallen(start: BattleUiState.Success) = Side.entries.fold(start) { game, side ->
        val next = game.bench(side).filter { it.hp > 0 }.maxByOrNull { it.hp }
            .takeIf { game.combatant(side).hp == 0 }
        if (next == null) game else swapStep(game, side, next)
    }

    private suspend fun swapStep(game: BattleUiState.Success, side: Side, incoming: Combatant): BattleUiState.Success {
        val event = BattleEvent(++eventId, side, incoming.fighter.name, Outcome.SWAP_IN, amount = 0)
        val swapped = game.swap(side, incoming).copy(event = event)
        emitStep(swapped, STEP_MILLIS)
        return swapped
    }

    private suspend fun emitStep(game: BattleUiState.Success, pause: Long) {
        _state.value = game
        delay(pause)
    }

    private companion object {
        // Pausa entre as ações do turno: dá tempo de ver cada golpe e o efeito dele antes do próximo.
        const val STEP_MILLIS = 1_700L

        // A ultimate tem cena própria (tela escurece, quadro do lutador) antes do impacto.
        const val ULTIMATE_STEP_MILLIS = ULTIMATE_SCENE_MILLIS + STEP_MILLIS
    }
}
