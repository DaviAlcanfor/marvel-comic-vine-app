package com.projeto.marvel.ui.battle

import android.animation.Animator
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
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
import com.projeto.marvel.ui.SpeedLinesDrawable
import com.projeto.marvel.ui.album.badged
import com.projeto.marvel.ui.album.rarityRing
import com.projeto.marvel.ui.album.ringPadding
import com.projeto.marvel.ui.color
import com.projeto.marvel.ui.comicInterpolator
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.icon
import com.projeto.marvel.ui.label
import com.projeto.marvel.ui.shake
import kotlinx.coroutines.launch

class BattleFragment : Fragment(R.layout.fragment_battle) {

    private val viewModel: BattleViewModel by viewModels()
    private var binding: FragmentBattleBinding? = null

    // -1 = primeira renderização desta View: sincroniza sem animar (evita repetir a última
    // animação ao voltar de rotação/background).
    private var lastEventId = -1

    // Última legenda "TURNO n · SUA VEZ!" mostrada (uma por turno).
    private var bannerTurn = 0

    // Animações infinitas (fundo e respiração): canceladas junto com a View.
    private val loops = mutableListOf<Animator>()

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

        viewLifecycleOwner.lifecycle.addObserver(binding.arena3d)
        loops += binding.arenaBackdrop.startSpeedLines(SpeedLinesDrawable(color(R.color.speed_lines)))
        loops += binding.cpuImage.idleSway()
        loops += binding.playerImage.idleSway(startDelay = SWAY_OFFSET_MILLIS)

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
        binding.recordText.text = if (game.pvp) {
            getString(R.string.battle_pvp_label)
        } else {
            getString(R.string.battle_record, game.record.wins, game.record.losses)
        }
        binding.recordText.contentDescription =
            getString(R.string.battle_record_description, game.record.wins, game.record.losses)
        bindStage(binding, game)

        binding.bindMoves(game, viewModel::use)
        binding.bindSquads(game, viewModel::swap)
        binding.dimInactive(game)

        val event = game.event
        val winner = game.winner
        binding.logText.text = logText(game)
        binding.styleLog(game)

        // Começo de um turno do jogador (não na primeira renderização da View): legenda de turno.
        // No PvP a legenda sai também na vez do segundo jogador (chave = turno + quem escolhe).
        val bannerKey = game.turn * Side.entries.size + game.choosing.ordinal
        val playerTurnStarts = !game.busy && winner == null && bannerKey != bannerTurn
        if (playerTurnStarts && lastEventId != -1) {
            binding.turnBanner.popBanner(requireContext().turnBannerText(game))
            bannerTurn = bannerKey
        }

        when {
            // Luta nova (primeira exibição ou revanche): lutadores entram pelos cantos.
            event == null && lastEventId != 0 -> {
                binding.hideResult()
                bannerTurn = 0
                game.stage?.let(binding::showTrail)
                binding.koBurst.visibility = View.INVISIBLE
                binding.cpuImage.enterFromCorner(direction = -1f)
                binding.playerImage.enterFromCorner(direction = 1f)
                binding.showVersus(game)
            }
            event != null && event.id != lastEventId && lastEventId != -1 -> {
                animate(binding, event)
                if (winner != null) {
                    // Ultimate que derruba: o K.O. espera a cena dela acabar.
                    val lead = if (event.outcome == Outcome.ULTIMATE) ULTIMATE_SCENE_MILLIS else 0L
                    binding.root.postDelayed({ this.binding?.showResult(game) }, lead)
                }
            }
        }
        lastEventId = event?.id ?: 0
    }

    /** Trilha de um time: título mostra a luta atual e o botão final muda conforme o resultado. */
    private fun bindStage(binding: FragmentBattleBinding, game: BattleUiState.Success) {
        val stage = game.stage
        binding.title.text = when {
            stage == null -> getString(R.string.battle_title)
            stage.isLast -> getString(R.string.battle_stage_boss, stage.number, stage.total)
            else -> getString(R.string.battle_stage, stage.number, stage.total)
        }
        binding.rematchButton.setText(
            when {
                stage == null -> R.string.battle_rematch
                game.winner != Side.PLAYER -> R.string.battle_retry
                stage.isLast -> R.string.battle_restart_gauntlet
                else -> R.string.battle_next_fight
            }
        )
    }

    private fun bindCombatant(combatant: Combatant, views: FighterViews) {
        val maxHp = combatant.fighter.maxHp()
        // Evita recarregar (e piscar) a imagem a cada passo do turno.
        if (views.image.tag != combatant.fighter.imageUrl) {
            views.image.tag = combatant.fighter.imageUrl
            views.image.load(combatant.fighter.imageUrl) { crossfade(true) }
            views.image.background = combatant.fighter.rarityRing(requireContext())
            views.image.setPadding(combatant.fighter.ringPadding(requireContext()))
        }
        val name = if (combatant.fighter.boss) {
            getString(R.string.battle_boss_name, combatant.fighter.name)
        } else {
            combatant.fighter.name
        }
        val level = combatant.fighter.level
        val shown = if (level > 1) getString(R.string.battle_level_name, name, level) else name
        views.name.text = combatant.fighter.badged(shown)
        views.name.setTextColor(color(if (combatant.fighter.golden) R.color.accent_text else R.color.text_primary))
        views.hpBar.max = maxHp
        views.hpBar.setProgressCompat(combatant.hp, true)
        views.hpBar.setIndicatorColor(color(hpColor(combatant.hp, maxHp)))
        views.hpText.text = getString(R.string.battle_hp, combatant.hp, maxHp)
        views.energyBar.setProgressCompat((combatant.energyFraction * ENERGY_BAR_MAX).toInt(), true)
        views.image.frost(if (combatant.frozen) color(MoveType.FREEZE.color) else null)
        views.status.text = listOfNotNull(
            getString(R.string.battle_status_poisoned).takeIf { combatant.poisonTurns > 0 },
            getString(R.string.battle_status_guarding).takeIf { combatant.guarding },
            getString(R.string.battle_status_dodging).takeIf { combatant.dodging },
            getString(R.string.battle_status_frozen).takeIf { combatant.frozen },
            getString(R.string.battle_status_soaked).takeIf { combatant.soakedTurns > 0 }
        ).joinToString(" · ")
    }

    private fun logText(game: BattleUiState.Success): String {
        val winner = game.winner
        val event = game.event
        val player = game.player.fighter.name
        return when {
            winner == Side.PLAYER && game.stage?.isLast == true ->
                getString(R.string.battle_team_beaten, player, game.stage.teamName.orEmpty())
            winner != null -> getString(R.string.battle_winner, game.combatant(winner).fighter.name)
            event == null || (game.pvp && !game.busy) -> {
                val yourTurn = getString(R.string.battle_your_turn, game.combatant(game.choosing).fighter.name)
                getString(R.string.battle_turn_log, game.turn, yourTurn)
            }
            else -> {
                val actor = game.combatant(event.side).fighter.name
                val line = getString(event.logRes(), actor, event.moveName.orEmpty(), event.amount)
                if (event.extra) getString(R.string.battle_extra_action, line) else line
            }
        }
    }

    private fun animate(binding: FragmentBattleBinding, event: BattleEvent) {
        val actor = binding.views(event.side)
        val target = binding.views(if (event.side == Side.PLAYER) Side.CPU else Side.PLAYER)
        // Jogador está embaixo à direita: avança para cima/esquerda; a CPU, o contrário.
        val direction = if (event.side == Side.PLAYER) -1f else 1f

        val typeColor = color(event.moveType?.color ?: R.color.accent)

        when (event.outcome) {
            Outcome.HIT, Outcome.POISONED, Outcome.FROZE, Outcome.SOAKED, Outcome.DRAINED ->
                binding.animateHit(event, actor, target, direction)
            Outcome.ULTIMATE -> {
                val fighter = (viewModel.state.value as? BattleUiState.Success)?.combatant(event.side)?.fighter
                binding.playUltimateScene(fighter?.imageUrl, event.moveName.orEmpty(), typeColor) {
                    binding.animateHit(event, actor, target, direction)
                }
            }
            Outcome.MISS -> {
                binding.approach(event.moveType, actor, target, direction)
                target.burst.burst(getString(R.string.battle_sfx_miss), color(R.color.text_secondary))
            }
            Outcome.HEAL -> {
                actor.image.pulse()
                actor.image.glow(typeColor)
                actor.popup.popup("+${event.heal}", typeColor)
                actor.burst.burst(getString(R.string.battle_sfx_heal), typeColor, startDelay = 0)
            }
            Outcome.GUARD -> {
                actor.image.pulse()
                actor.image.glow(typeColor)
                actor.burst.burst(getString(R.string.battle_sfx_guard), typeColor, startDelay = 0)
            }
            Outcome.DODGE -> {
                actor.image.sidestep(direction)
                actor.burst.burst(getString(R.string.battle_sfx_dodge), typeColor, startDelay = 0)
            }
            Outcome.POISON_TICK -> {
                val poison = color(MoveType.POISON.color)
                actor.image.hurt()
                actor.image.glow(poison, startDelay = STEP_MILLIS)
                actor.popup.popup("−${event.amount}", poison)
                actor.burst.burst(getString(R.string.battle_sfx_poison_tick), poison, startDelay = 0)
            }
            Outcome.SWAP_IN -> actor.image.enterFromCorner(-direction)
            Outcome.FROZEN_SKIP -> {
                val ice = color(MoveType.FREEZE.color)
                actor.image.glow(ice)
                actor.image.shake(startDelay = 0, interpolator = comicInterpolator(IMPACT_SHAKE_MILLIS))
                actor.burst.burst(getString(R.string.battle_sfx_frozen), ice, startDelay = 0)
            }
        }
    }

    private fun color(@ColorRes res: Int) = ContextCompat.getColor(requireContext(), res)

    override fun onDestroyView() {
        super.onDestroyView()
        loops.forEach { it.cancel() }
        loops.clear()
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
    val energyBar get() = if (isPlayer) binding.playerEnergyBar else binding.cpuEnergyBar
}

private fun FragmentBattleBinding.views(side: Side) = FighterViews(this, side)

private const val PERCENT = 100
private const val ENERGY_BAR_MAX = 100
private const val ULTIMATE_ZOOM = 1.12f
private const val IMPACT_SHAKE_MILLIS = 400L
private const val SWAY_OFFSET_MILLIS = 700L
private const val HP_HIGH_PERCENT = 50
private const val HP_LOW_PERCENT = 20

@ColorRes
private fun hpColor(hp: Int, maxHp: Int) = when {
    hp * PERCENT > maxHp * HP_HIGH_PERCENT -> R.color.team_green
    hp * PERCENT > maxHp * HP_LOW_PERCENT -> R.color.accent
    else -> R.color.primary
}

/** Golpes à distância ([RANGED]) voam como projétil na cor do golpe; o resto é corpo a corpo. */
private fun FragmentBattleBinding.approach(
    type: MoveType?,
    actor: FighterViews,
    target: FighterViews,
    direction: Float
) {
    if (type != null && type in RANGED) {
        projectile.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(root.context, type.color))
        projectile.fireProjectile(from = actor.image, to = target.image, spin = type == MoveType.MAGIC)
    } else {
        actor.image.lunge(direction)
    }
}

@StringRes
private fun BattleEvent.logRes() = when (outcome) {
    Outcome.HIT -> hitLogRes()
    Outcome.ULTIMATE -> R.string.battle_log_ultimate
    Outcome.FROZE -> R.string.battle_log_freeze
    Outcome.FROZEN_SKIP -> R.string.battle_log_frozen
    Outcome.SOAKED -> R.string.battle_log_soak
    Outcome.DRAINED -> R.string.battle_log_drain
    Outcome.MISS -> R.string.battle_log_miss
    Outcome.POISONED -> R.string.battle_log_poisoned
    Outcome.HEAL -> R.string.battle_log_heal
    Outcome.GUARD -> R.string.battle_log_guard
    Outcome.DODGE -> R.string.battle_log_dodge
    Outcome.POISON_TICK -> R.string.battle_log_poison_tick
    Outcome.SWAP_IN -> R.string.battle_log_swap
}

/**
 * Golpe que acertou: avanço ou projétil, dano no alvo e a marca do tipo — onomatopeia e aura
 * na cor do golpe (veneno, gelo, água), vida drenada voltando para quem atacou.
 */
private fun FragmentBattleBinding.animateHit(
    event: BattleEvent,
    actor: FighterViews,
    target: FighterViews,
    direction: Float
) {
    val context = root.context
    fun color(@ColorRes res: Int) = ContextCompat.getColor(context, res)
    val typeColor = color(event.moveType?.color ?: R.color.accent)

    approach(event.moveType, actor, target, direction)
    target.image.hurt()
    target.popup.popup("−${event.amount}", color(R.color.primary_text))
    val effect = EFFECT_SFX[event.outcome]
    when {
        effect != null -> {
            target.burst.burst(context.getString(effect), typeColor)
            target.image.glow(typeColor, startDelay = STEP_MILLIS)
        }
        event.outcome == Outcome.ULTIMATE -> {
            target.burst.burst(context.getString(R.string.battle_sfx_ultimate), typeColor, big = true)
            target.image.knockback(-direction)
            root.punchZoom(scale = ULTIMATE_ZOOM)
        }
        event.critical -> {
            target.burst.burst(context.getString(R.string.battle_sfx_critical), color(R.color.primary))
            root.punchZoom()
        }
        event.moveType == MoveType.MAGIC -> target.burst.burst(context.getString(R.string.battle_sfx_magic), typeColor)
        else -> target.burst.burst(context.resources.getStringArray(R.array.battle_sfx_hit).random(), typeColor)
    }
    if (event.heal > 0) drainBack(event.heal, actor, target, typeColor)
    if (event.outcome == Outcome.HIT || event.outcome == Outcome.ULTIMATE) {
        impactFlash.impactFrame()
        root.shake(STEP_MILLIS, comicInterpolator(IMPACT_SHAKE_MILLIS))
        arena3d.impact()
        // Vibração do sistema (segue a configuração do usuário; sem permissão): forte no crítico.
        val strong = event.critical || event.outcome == Outcome.ULTIMATE
        val haptic = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
        root.performHapticFeedback(haptic)
    }
}

@StringRes
private fun BattleEvent.hitLogRes() = when {
    critical -> R.string.battle_log_critical
    moveType == MoveType.MAGIC -> R.string.battle_log_magic
    else -> R.string.battle_log_hit
}

/** Dreno: a vida "volta" voando do alvo para quem atacou. */
private fun FragmentBattleBinding.drainBack(heal: Int, actor: FighterViews, target: FighterViews, color: Int) {
    projectile.fireProjectile(from = target.image, to = actor.image, startDelay = STEP_MILLIS)
    actor.image.glow(color, startDelay = STEP_MILLIS * 2)
    actor.popup.popup("+$heal", color)
}

/** Onomatopeia dos golpes com efeito (os outros usam as de impacto). */
private val EFFECT_SFX = mapOf(
    Outcome.POISONED to R.string.battle_sfx_poison,
    Outcome.FROZE to R.string.battle_sfx_freeze,
    Outcome.SOAKED to R.string.battle_sfx_water,
    Outcome.DRAINED to R.string.battle_sfx_drain
)

private fun Context.turnBannerText(game: BattleUiState.Success) = when {
    !game.pvp -> getString(R.string.battle_turn_yours, game.turn)
    game.choosing == Side.PLAYER -> getString(R.string.battle_turn_p1, game.turn)
    else -> getString(R.string.battle_turn_p2, game.turn)
}
