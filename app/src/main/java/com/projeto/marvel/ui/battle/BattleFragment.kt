package com.projeto.marvel.ui.battle

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.MoveType
import com.projeto.marvel.databinding.FragmentBattleBinding
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.shake
import kotlinx.coroutines.launch

class BattleFragment : Fragment(R.layout.fragment_battle) {

    private val viewModel: BattleViewModel by viewModels()
    private var binding: FragmentBattleBinding? = null

    // -1 = primeira renderização desta View: sincroniza sem animar (evita repetir a última
    // animação ao voltar de rotação/background).
    private var lastEventId = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentBattleBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        lastEventId = -1

        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.errorText.setOnClickListener { viewModel.load() }
        binding.rematchButton.setOnClickListener { viewModel.rematch() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: BattleUiState) {
        val binding = binding ?: return
        val game = state as? BattleUiState.Success
        binding.progressBar.fadeVisible(state is BattleUiState.Loading)
        binding.errorText.fadeVisible(state is BattleUiState.Error)
        binding.arena.visibility = if (game != null) View.VISIBLE else View.GONE
        binding.recordText.fadeVisible(game != null)
        binding.moves.fadeVisible(game != null && game.winner == null)
        binding.rematchButton.fadeVisible(game?.winner != null)

        if (state is BattleUiState.Error) {
            binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        if (game != null) bindGame(binding, game)
    }

    private fun bindGame(binding: FragmentBattleBinding, game: BattleUiState.Success) {
        bindCombatant(game.player, binding.views(Side.PLAYER))
        bindCombatant(game.cpu, binding.views(Side.CPU))
        binding.recordText.text = getString(R.string.battle_record, game.record.wins, game.record.losses)
        binding.recordText.contentDescription =
            getString(R.string.battle_record_description, game.record.wins, game.record.losses)

        val moveButtons = listOf(binding.move1, binding.move2, binding.move3, binding.move4)
        moveButtons.forEachIndexed { index, button ->
            val move = game.player.fighter.moves.getOrNull(index)
            button.visibility = if (move != null) View.VISIBLE else View.INVISIBLE
            button.isEnabled = !game.busy
            if (move != null) {
                button.text = getString(R.string.battle_move, move.name, getString(move.type.label()))
                button.setOnClickListener { viewModel.use(move) }
            }
        }

        val event = game.event
        val winner = game.winner
        binding.logText.text = when {
            winner != null -> getString(R.string.battle_winner, game.combatant(winner).fighter.name)
            event != null -> logText(game, event)
            else -> getString(R.string.battle_your_turn, game.player.fighter.name)
        }

        when {
            // Luta nova (primeira exibição ou revanche): lutadores entram pelos cantos.
            event == null && lastEventId != 0 -> {
                binding.koBurst.visibility = View.INVISIBLE
                binding.cpuImage.enterFromCorner(direction = -1f)
                binding.playerImage.enterFromCorner(direction = 1f)
            }
            event != null && event.id != lastEventId && lastEventId != -1 -> {
                animate(binding, event)
                if (winner != null) {
                    binding.views(if (winner == Side.PLAYER) Side.CPU else Side.PLAYER).image.defeat()
                    binding.koBurst.burst(getString(R.string.battle_ko), color(R.color.primary), KO_DELAY_MILLIS)
                }
            }
        }
        lastEventId = event?.id ?: 0
    }

    private fun bindCombatant(combatant: Combatant, views: FighterViews) {
        val maxHp = combatant.fighter.maxHp()
        // Evita recarregar (e piscar) a imagem a cada passo do turno.
        if (views.image.tag != combatant.fighter.imageUrl) {
            views.image.tag = combatant.fighter.imageUrl
            views.image.load(combatant.fighter.imageUrl) { crossfade(true) }
        }
        views.name.text = combatant.fighter.name
        views.hpBar.max = maxHp
        views.hpBar.setProgressCompat(combatant.hp, true)
        views.hpBar.setIndicatorColor(color(hpColor(combatant.hp, maxHp)))
        views.hpText.text = getString(R.string.battle_hp, combatant.hp, maxHp)
        views.status.text = listOfNotNull(
            getString(R.string.battle_status_poisoned).takeIf { combatant.poisonTurns > 0 },
            getString(R.string.battle_status_guarding).takeIf { combatant.guarding },
            getString(R.string.battle_status_dodging).takeIf { combatant.dodging }
        ).joinToString(" · ")
    }

    private fun logText(game: BattleUiState.Success, event: BattleEvent): String {
        val res = when (event.outcome) {
            Outcome.HIT -> R.string.battle_log_hit
            Outcome.MISS -> R.string.battle_log_miss
            Outcome.POISONED -> R.string.battle_log_poisoned
            Outcome.HEAL -> R.string.battle_log_heal
            Outcome.GUARD -> R.string.battle_log_guard
            Outcome.DODGE -> R.string.battle_log_dodge
            Outcome.POISON_TICK -> R.string.battle_log_poison_tick
        }
        return getString(res, game.combatant(event.side).fighter.name, event.moveName.orEmpty(), event.amount)
    }

    private fun animate(binding: FragmentBattleBinding, event: BattleEvent) {
        val actor = binding.views(event.side)
        val target = binding.views(if (event.side == Side.PLAYER) Side.CPU else Side.PLAYER)
        // Jogador está embaixo à direita: avança para cima/esquerda; a CPU, o contrário.
        val direction = if (event.side == Side.PLAYER) -1f else 1f

        when (event.outcome) {
            Outcome.HIT, Outcome.POISONED -> {
                actor.image.lunge(direction)
                target.image.hurt()
                target.popup.popup("−${event.amount}", color(R.color.primary_text))
                if (event.outcome == Outcome.HIT) {
                    target.burst.burst(resources.getStringArray(R.array.battle_sfx_hit).random(), color(R.color.accent))
                    binding.impactFlash.impactFrame()
                    binding.root.shake(startDelay = IMPACT_SHAKE_DELAY_MILLIS)
                } else {
                    target.burst.burst(getString(R.string.battle_sfx_poison), color(R.color.poison))
                }
            }
            Outcome.MISS -> {
                actor.image.lunge(direction)
                target.burst.burst(getString(R.string.battle_sfx_miss), color(R.color.text_secondary))
            }
            Outcome.HEAL -> {
                actor.image.pulse()
                actor.popup.popup("+${event.amount}", color(R.color.team_green))
                actor.burst.burst(getString(R.string.battle_sfx_heal), color(R.color.team_green), startDelay = 0)
            }
            Outcome.GUARD -> {
                actor.image.pulse()
                actor.burst.burst(getString(R.string.battle_sfx_guard), color(R.color.team_blue), startDelay = 0)
            }
            Outcome.DODGE -> {
                actor.image.pulse()
                actor.burst.burst(getString(R.string.battle_sfx_dodge), color(R.color.accent), startDelay = 0)
            }
            Outcome.POISON_TICK -> {
                actor.image.hurt()
                actor.popup.popup("−${event.amount}", color(R.color.poison))
                actor.burst.burst(getString(R.string.battle_sfx_poison_tick), color(R.color.poison), startDelay = 0)
            }
        }
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}

/** Views de um lado da arena (jogador embaixo à direita, CPU em cima à esquerda). */
private class FighterViews(private val binding: FragmentBattleBinding, side: Side) {
    private val isPlayer = side == Side.PLAYER
    val image get() = if (isPlayer) binding.playerImage else binding.cpuImage
    val popup get() = if (isPlayer) binding.playerPopup else binding.cpuPopup
    val burst get() = if (isPlayer) binding.playerBurst else binding.cpuBurst
    val name get() = if (isPlayer) binding.playerName else binding.cpuName
    val hpBar get() = if (isPlayer) binding.playerHpBar else binding.cpuHpBar
    val hpText get() = if (isPlayer) binding.playerHpText else binding.cpuHpText
    val status get() = if (isPlayer) binding.playerStatus else binding.cpuStatus
}

private fun FragmentBattleBinding.views(side: Side) = FighterViews(this, side)

private const val PERCENT = 100
private const val KO_DELAY_MILLIS = 700L
private const val IMPACT_SHAKE_DELAY_MILLIS = 170L
private const val HP_HIGH_PERCENT = 50
private const val HP_LOW_PERCENT = 20

@ColorRes
private fun hpColor(hp: Int, maxHp: Int) = when {
    hp * PERCENT > maxHp * HP_HIGH_PERCENT -> R.color.team_green
    hp * PERCENT > maxHp * HP_LOW_PERCENT -> R.color.accent
    else -> R.color.primary
}

private fun MoveType.label() = when (this) {
    MoveType.STRIKE -> R.string.move_type_strike
    MoveType.BLAST -> R.string.move_type_blast
    MoveType.POISON -> R.string.move_type_poison
    MoveType.HEAL -> R.string.move_type_heal
    MoveType.GUARD -> R.string.move_type_guard
    MoveType.DODGE -> R.string.move_type_dodge
}
