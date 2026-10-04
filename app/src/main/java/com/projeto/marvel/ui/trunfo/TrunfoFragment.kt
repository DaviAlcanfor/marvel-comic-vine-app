package com.projeto.marvel.ui.trunfo

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.Stat
import com.projeto.marvel.databinding.FragmentTrunfoBinding
import com.projeto.marvel.databinding.ViewTrunfoCardBinding
import com.projeto.marvel.ui.EraPanelDrawable
import com.projeto.marvel.ui.album.label
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

private val STAT_LABELS = listOf(
    Stat.ATTACK to R.string.stat_attack,
    Stat.DEFENSE to R.string.stat_defense,
    Stat.SPEED to R.string.stat_speed,
    Stat.INTELLIGENCE to R.string.stat_intelligence,
    Stat.FAME to R.string.stat_fame
)

class TrunfoFragment : Fragment(R.layout.fragment_trunfo) {

    private val viewModel: TrunfoViewModel by viewModels()
    private var binding: FragmentTrunfoBinding? = null

    // Última rodada mostrada: a virada da carta da CPU só anima quando ela muda.
    private var shownRound: TrunfoRound? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentTrunfoBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.message.setOnClickListener { viewModel.load() }
        binding.nextButton.setOnClickListener {
            val playing = viewModel.state.value as? TrunfoUiState.Playing
            if (playing?.round?.next?.over == true) viewModel.load() else viewModel.next()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: TrunfoUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is TrunfoUiState.Loading
        binding.message.isVisible = state is TrunfoUiState.Error
        binding.game.isVisible = state is TrunfoUiState.Playing
        if (state is TrunfoUiState.Error) binding.message.text = state.message
        if (state is TrunfoUiState.Playing) bindGame(binding, state)
    }

    private fun bindGame(binding: FragmentTrunfoBinding, state: TrunfoUiState.Playing) {
        val game = state.game
        val round = state.round
        val shown = round?.next ?: game
        binding.score.text = getString(
            R.string.trunfo_score,
            shown.player.size,
            shown.cpu.size,
            minOf(game.round, TRUNFO_MAX_ROUNDS),
            TRUNFO_MAX_ROUNDS
        )
        val choosing = round == null && game.chooser == TrunfoSide.PLAYER && !game.over
        binding.playerCard.show(round?.playerCard ?: game.player.firstOrNull(), round, TrunfoSide.PLAYER, choosing)
        binding.cpuCard.show(round?.cpuCard ?: game.cpu.firstOrNull(), round, TrunfoSide.CPU, false)
        binding.cpuCard.back.isVisible = round == null
        if (round != null && round != shownRound) {
            binding.cpuCard.root.flipIn()
            staggerIn(listOf(binding.status))
        }
        if (round == null && shownRound != null) staggerIn(listOf(binding.playerCard.root, binding.cpuCard.root))
        shownRound = round
        binding.status.text = status(state)
        binding.nextButton.isVisible = round != null
        binding.nextButton.setText(if (round?.next?.over == true) R.string.trunfo_again else R.string.trunfo_next)
    }

    private fun status(state: TrunfoUiState.Playing): String {
        val round = state.round ?: return getString(
            if (state.game.chooser == TrunfoSide.PLAYER) R.string.trunfo_your_turn else R.string.trunfo_cpu_turn
        )
        val stat = getString(STAT_LABELS.first { it.first == round.stat }.second)
        val mine = round.playerCard.stats[round.stat] ?: 0
        val theirs = round.cpuCard.stats[round.stat] ?: 0
        val line = when (round.winner) {
            TrunfoSide.PLAYER -> getString(R.string.trunfo_round_win, stat, mine, theirs)
            TrunfoSide.CPU -> getString(R.string.trunfo_round_lose, stat, mine, theirs)
            null -> getString(R.string.trunfo_round_tie, stat, mine)
        }
        val end = when {
            !round.next.over -> null
            round.next.winner == TrunfoSide.PLAYER ->
                getString(R.string.trunfo_won, getString(requireNotNull(state.reward).label()))
            round.next.winner == TrunfoSide.CPU -> getString(R.string.trunfo_lost)
            else -> getString(R.string.trunfo_draw)
        }
        return listOfNotNull(line, end).joinToString("\n")
    }

    /** Carta com as 5 linhas de atributo; a escolhida fica destacada (dourada se venceu). */
    private fun ViewTrunfoCardBinding.show(
        card: TrunfoCard?,
        round: TrunfoRound?,
        side: TrunfoSide,
        choosing: Boolean
    ) {
        card ?: return
        val stat = round?.stat
        val winner = round?.winner
        val context = root.context
        if (image.tag != card.id) {
            image.tag = card.id
            image.load(card.imageUrl) { crossfade(true) }
        }
        name.text = card.name
        stats.removeAllViews()
        STAT_LABELS.forEach { (key, label) ->
            val row = LayoutInflater.from(context).inflate(R.layout.item_trunfo_stat, stats, false) as TextView
            row.text = context.getString(R.string.trunfo_stat_row, context.getString(label), card.stats[key] ?: 0)
            val picked = key == stat
            if (picked) {
                val fill = if (winner == side) R.color.caption_yellow else R.color.surface_variant
                val color = ContextCompat.getColor(context, fill)
                row.background = EraPanelDrawable(context, EraPanelDrawable.Kind.CAPTION, color)
                row.setTextColor(ContextCompat.getColor(context, R.color.ink))
            }
            if (choosing) {
                row.background = EraPanelDrawable(context, EraPanelDrawable.Kind.SECONDARY)
                row.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.choose(key)
                }
            }
            // Fundo da época posto em código troca o padding da View: devolve a folga do texto.
            val gap = resources.getDimensionPixelSize(R.dimen.space_sm)
            row.setPadding(gap, 0, gap, 0)
            stats.addView(row)
        }
    }

    /** A carta da CPU vira de frente (meia volta com mola). */
    private fun View.flipIn() {
        cameraDistance = CAMERA_DISTANCE * resources.displayMetrics.density
        rotationY = -QUARTER
        animate().rotationY(0f).setStartDelay(0).setDuration(FLIP_MILLIS).setInterpolator(OvershootInterpolator())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        shownRound = null
    }

    private companion object {
        const val QUARTER = 90f
        const val FLIP_MILLIS = 380L
        const val CAMERA_DISTANCE = 8_000f
    }
}
