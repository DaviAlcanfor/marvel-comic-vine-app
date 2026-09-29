package com.projeto.marvel.ui.guess

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.imageLoader
import coil.request.ImageRequest
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentGuessBinding
import com.projeto.marvel.databinding.ItemQuizAnswerBinding
import kotlinx.coroutines.launch

class GuessFragment : Fragment(R.layout.fragment_guess) {

    private val viewModel: GuessViewModel by viewModels()
    private var binding: FragmentGuessBinding? = null

    // Foto nítida da rodada; cada nível é uma versão reduzida dela, ampliada sem suavizar.
    private var original: Bitmap? = null
    private var originalId = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentGuessBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.nextButton.setOnClickListener { viewModel.next() }
        binding.message.setOnClickListener { viewModel.load() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: GuessUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is GuessUiState.Loading
        binding.message.isVisible = state is GuessUiState.Error
        binding.game.isVisible = state is GuessUiState.Playing
        if (state is GuessUiState.Error) {
            binding.message.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        if (state is GuessUiState.Playing) bindGame(binding, state)
    }

    private fun bindGame(binding: FragmentGuessBinding, game: GuessUiState.Playing) {
        binding.streak.text = getString(R.string.guess_streak, game.streak, game.best, game.score)
        binding.result.isVisible = game.solved
        binding.result.text = getString(R.string.guess_right, game.answer.name, game.gained)
        binding.nextButton.isVisible = game.solved
        binding.options.removeAllViews()
        game.options.forEach { option ->
            ItemQuizAnswerBinding.inflate(layoutInflater, binding.options, true).root.apply {
                text = if (game.solved && option.id == game.answer.id) "✅ ${option.name}" else option.name
                val out = option.id in game.wrong || (game.solved && option.id != game.answer.id)
                isEnabled = !out && !game.solved
                alpha = if (out) DISABLED_ALPHA else 1f
                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.guess(option.id)
                }
            }
        }
        bindImage(binding, game)
    }

    private fun bindImage(binding: FragmentGuessBinding, game: GuessUiState.Playing) {
        if (originalId == game.answer.id) return showLevel(binding, game.level)
        originalId = game.answer.id
        original = null
        binding.image.setImageDrawable(null)
        val id = game.answer.id
        viewLifecycleOwner.lifecycleScope.launch {
            val request = ImageRequest.Builder(requireContext()).data(game.answer.image?.mediumUrl)
                .allowHardware(false).build()
            val bitmap = (requireContext().imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
            if (originalId != id) return@launch
            original = bitmap
            (viewModel.state.value as? GuessUiState.Playing)?.let { showLevel(binding, it.level) }
        }
    }

    private fun showLevel(binding: FragmentGuessBinding, level: Int) {
        val bitmap = original ?: return
        val width = PIXEL_SIZES.getOrNull(level)
        binding.image.setImageDrawable(
            if (width == null) {
                BitmapDrawable(resources, bitmap)
            } else {
                val height = (width * bitmap.height / bitmap.width).coerceAtLeast(1)
                BitmapDrawable(resources, Bitmap.createScaledBitmap(bitmap, width, height, true))
                    .apply { paint.isFilterBitmap = false }
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        originalId = -1
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}
