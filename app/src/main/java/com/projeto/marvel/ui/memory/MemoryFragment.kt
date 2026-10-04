package com.projeto.marvel.ui.memory

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentMemoryBinding
import com.projeto.marvel.databinding.ItemMemoryCardBinding
import com.projeto.marvel.ui.album.label
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

class MemoryFragment : Fragment(R.layout.fragment_memory) {

    private val viewModel: MemoryViewModel by viewModels()
    private var binding: FragmentMemoryBinding? = null
    private val cards = mutableListOf<ItemMemoryCardBinding>()

    // Mesa montada (faces) e o que cada carta mostra agora: só anima o que mudou.
    private var dealt: List<Int> = emptyList()
    private val shown = mutableSetOf<Int>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentMemoryBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.againButton.setOnClickListener { viewModel.restart() }
        binding.message.setOnClickListener { viewModel.load() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: MemoryUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is MemoryUiState.Loading
        binding.message.isVisible = state is MemoryUiState.Error
        binding.board.isVisible = state is MemoryUiState.Playing
        binding.moves.isVisible = state is MemoryUiState.Playing
        if (state is MemoryUiState.Error) {
            binding.message.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        if (state !is MemoryUiState.Playing) return
        if (state.game.faces != dealt) deal(binding, state)
        val game = state.game
        binding.moves.text = state.best?.let { getString(R.string.memory_moves_best, game.moves, it) }
            ?: getString(R.string.memory_moves, game.moves)
        cards.forEachIndexed { index, card ->
            val up = index in game.open || index in game.matched
            if (up != index in shown) card.flip(up)
            if (up) shown += index else shown -= index
        }
        binding.result.isVisible = game.done
        binding.againButton.isVisible = game.done
        state.reward?.let { reward ->
            val text = getString(R.string.memory_done, game.moves, getString(reward.label()))
            binding.result.text = if (state.newRecord) getString(R.string.memory_record, text) else text
        }
    }

    /** Monta as 16 cartas viradas para baixo (quadradas) e faz elas entrarem em sequência. */
    private fun deal(binding: FragmentMemoryBinding, state: MemoryUiState.Playing) {
        dealt = state.game.faces
        shown.clear()
        cards.clear()
        binding.board.removeAllViews()
        state.game.faces.forEachIndexed { index, face ->
            val card = ItemMemoryCardBinding.inflate(layoutInflater, binding.board, false)
            binding.board.addView(card.root)
            card.face.load(state.images[face])
            card.root.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                viewModel.flip(index)
            }
            card.root.post { card.root.updateLayoutParams { height = card.root.width } }
            cards += card
        }
        staggerIn(cards.map { it.root })
    }

    /** Meia volta até de lado, troca a face e completa (com um pulinho ao abrir). */
    private fun ItemMemoryCardBinding.flip(up: Boolean) {
        val card = root
        card.cameraDistance = CAMERA_DISTANCE * card.resources.displayMetrics.density
        card.animate().cancel()
        card.animate().rotationY(QUARTER).setStartDelay(0).setDuration(HALF_MILLIS).withEndAction {
            face.visibility = if (up) View.VISIBLE else View.INVISIBLE
            back.visibility = if (up) View.INVISIBLE else View.VISIBLE
            card.rotationY = -QUARTER
            card.animate().rotationY(0f).setDuration(HALF_MILLIS).setInterpolator(OvershootInterpolator())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        cards.clear()
        dealt = emptyList()
        shown.clear()
    }

    private companion object {
        const val QUARTER = 90f
        const val HALF_MILLIS = 140L
        const val CAMERA_DISTANCE = 8_000f
    }
}
