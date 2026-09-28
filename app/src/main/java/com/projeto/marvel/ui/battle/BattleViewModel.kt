package com.projeto.marvel.ui.battle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.projeto.marvel.data.BattleRecord
import com.projeto.marvel.data.BattleRecordStore
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Move
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.data.Stat
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
    val critical: Boolean = false
)

sealed interface BattleUiState {
    data object Loading : BattleUiState

    /** [busy] = turno em andamento (golpes bloqueados até as animações terminarem). */
    data class Success(
        val player: Combatant,
        val cpu: Combatant,
        val record: BattleRecord,
        val event: BattleEvent? = null,
        val busy: Boolean = false
    ) : BattleUiState {
        val winner: Side?
            get() = when {
                player.hp == 0 -> Side.CPU
                cpu.hp == 0 -> Side.PLAYER
                else -> null
            }

        fun combatant(side: Side) = if (side == Side.PLAYER) player else cpu

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
    private val random: Random = Random.Default
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<BattleUiState>(BattleUiState.Loading)
    val state: StateFlow<BattleUiState> = _state.asStateFlow()

    private val playerUrl: String = checkNotNull(savedStateHandle["playerUrl"])
    private val opponentUrl: String? = savedStateHandle["opponentUrl"]
    private var fighters: Pair<Fighter, Fighter>? = null
    private var eventId = 0
    private var turn: Job? = null

    init { load() }

    fun load() {
        turn?.cancel()
        _state.value = BattleUiState.Loading
        viewModelScope.launch {
            _state.value = repository.getFighters(playerUrl, opponentUrl).fold(
                onSuccess = {
                    fighters = it
                    BattleUiState.Success(Combatant(it.first), Combatant(it.second), records.get())
                },
                onFailure = { BattleUiState.Error(it.message ?: "Falha ao carregar os lutadores") }
            )
        }
    }

    /** Revanche com os mesmos lutadores (o adversário sorteado não muda). */
    fun rematch() {
        val (player, cpu) = fighters ?: return
        turn?.cancel()
        _state.value = BattleUiState.Success(Combatant(player), Combatant(cpu), records.get())
    }

    /** Um turno: o mais rápido (VEL) age primeiro, depois o outro; no fim o veneno pesa. */
    fun use(move: Move) {
        val start = _state.value as? BattleUiState.Success ?: return
        if (start.busy || start.winner != null) return
        // Marca ocupado antes de lançar: dois toques rápidos não podem iniciar dois turnos.
        _state.value = start.copy(busy = true)
        turn = viewModelScope.launch {
            val playerFirst = start.player.stat(Stat.SPEED) >= start.cpu.stat(Stat.SPEED)
            val order = if (playerFirst) listOf(Side.PLAYER, Side.CPU) else listOf(Side.CPU, Side.PLAYER)
            var game = start.copy(busy = true)
            for (side in order) {
                if (game.winner != null) break
                game = act(game, side, if (side == Side.PLAYER) move else cpuMove(game.cpu, random.nextInt(ROLL_MAX)))
                game = saveIfOver(game)
                emitStep(game)
            }
            for (side in Side.entries) {
                if (game.winner != null || game.combatant(side).poisonTurns == 0) continue
                game = saveIfOver(poison(game, side))
                emitStep(game)
            }
            _state.value = game.copy(busy = false)
        }
    }

    private fun act(game: BattleUiState.Success, side: Side, move: Move): BattleUiState.Success {
        val other = if (side == Side.PLAYER) Side.CPU else Side.PLAYER
        val result = resolve(move, game.combatant(side), game.combatant(other), random.nextInt(ROLL_MAX))
        val event = BattleEvent(
            id = ++eventId,
            side = side,
            moveName = move.name,
            outcome = result.outcome,
            amount = result.amount,
            moveType = move.type,
            critical = result.critical
        )
        return game.with(side, result.actor).with(other, result.target).copy(event = event)
    }

    private fun poison(game: BattleUiState.Success, side: Side): BattleUiState.Success {
        val before = game.combatant(side)
        val after = poisonTick(before)
        return game.with(side, after)
            .copy(event = BattleEvent(++eventId, side, null, Outcome.POISON_TICK, before.hp - after.hp))
    }

    /**
     * Grava o resultado assim que alguém vence — não no fim do turno, que pode ser cancelado
     * por uma revanche durante a última pausa de animação.
     */
    private fun saveIfOver(game: BattleUiState.Success): BattleUiState.Success {
        val winner = game.winner ?: return game
        return game.copy(record = records.add(won = winner == Side.PLAYER))
    }

    private suspend fun emitStep(game: BattleUiState.Success) {
        _state.value = game
        delay(STEP_MILLIS)
    }

    private companion object {
        const val ROLL_MAX = 100
        const val STEP_MILLIS = 1_000L
    }
}
