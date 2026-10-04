package com.projeto.marvel.ui.quote

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.projeto.marvel.databinding.FragmentQuoteBinding
import com.projeto.marvel.databinding.ItemQuizAnswerBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.shake
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

class QuoteFragment : Fragment(R.layout.fragment_quote) {

    private val viewModel: QuoteViewModel by viewModels()
    private var binding: FragmentQuoteBinding? = null
    private var shownAnswer = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentQuoteBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.nextButton.setOnClickListener { viewModel.next() }
        binding.message.setOnClickListener { viewModel.load() }
        binding.quote.comicBox(BoxStyle.SPEECH, ContextCompat.getColor(requireContext(), R.color.white))
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: QuoteUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is QuoteUiState.Loading
        binding.message.isVisible = state is QuoteUiState.Error
        binding.game.isVisible = state is QuoteUiState.Playing
        if (state is QuoteUiState.Error) {
            binding.message.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        if (state is QuoteUiState.Playing) bindGame(binding, state)
    }

    private fun bindGame(binding: FragmentQuoteBinding, game: QuoteUiState.Playing) {
        binding.streak.text = getString(R.string.quote_streak, game.streak, game.best)
        binding.quote.text = getString(R.string.quote_text, game.quote)
        binding.result.isVisible = game.solved
        binding.nextButton.isVisible = game.solved
        binding.speaker.visibility = if (game.solved) View.VISIBLE else View.INVISIBLE
        if (game.solved) binding.speaker.load(game.answer.image?.mediumUrl)
        binding.result.text = when {
            !game.right -> getString(R.string.quote_wrong, game.answer.name)
            game.pack -> getString(R.string.quote_right_pack, game.answer.name)
            else -> getString(R.string.quote_right, game.answer.name)
        }
        val fresh = shownAnswer != game.answer.id
        shownAnswer = game.answer.id
        binding.options.removeAllViews()
        val buttons = game.options.map { option ->
            ItemQuizAnswerBinding.inflate(layoutInflater, binding.options, true).root.apply {
                text = if (game.solved && option.id == game.answer.id) "✓ ${option.name}" else option.name
                isEnabled = !game.solved
                alpha = if (game.solved && option.id != game.answer.id) DISABLED_ALPHA else 1f
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.pick(option.id)
                }
            }
        }
        if (fresh) staggerIn(listOf(binding.quote) + buttons)
        if (game.solved && !game.right) binding.quote.shake()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        shownAnswer = -1
    }

    private companion object {
        const val DISABLED_ALPHA = 0.4f
    }
}
