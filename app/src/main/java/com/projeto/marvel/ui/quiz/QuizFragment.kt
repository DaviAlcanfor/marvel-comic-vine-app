package com.projeto.marvel.ui.quiz

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentQuizBinding
import com.projeto.marvel.databinding.ItemQuizAnswerBinding
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

class QuizFragment : Fragment(R.layout.fragment_quiz) {

    private val viewModel: QuizViewModel by viewModels()
    private var binding: FragmentQuizBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentQuizBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.restartButton.setOnClickListener { viewModel.restart() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: QuizUiState) {
        val binding = binding ?: return
        binding.questionGroup.isVisible = state is QuizUiState.Question
        binding.loading.isVisible = state is QuizUiState.Loading
        binding.message.isVisible = state is QuizUiState.Loading || state is QuizUiState.Error
        binding.resultGroup.isVisible = state is QuizUiState.Result
        when (state) {
            is QuizUiState.Question -> bindQuestion(binding, state.index)
            QuizUiState.Loading -> binding.message.setText(R.string.quiz_loading)
            is QuizUiState.Error -> binding.message.text = state.message
            is QuizUiState.Result -> bindResult(binding, state)
        }
    }

    private fun bindQuestion(binding: FragmentQuizBinding, index: Int) {
        val question = QUESTIONS[index]
        binding.progress.text = getString(R.string.quiz_progress, index + 1, QUESTIONS.size)
        binding.question.setText(question.text)
        binding.answers.removeAllViews()
        question.answers.shuffled().forEach { (text, stat) ->
            ItemQuizAnswerBinding.inflate(layoutInflater, binding.answers, true).root.apply {
                setText(text)
                setOnClickListener { viewModel.answer(stat) }
            }
        }
        staggerIn(listOf(binding.question) + (0 until binding.answers.childCount).map(binding.answers::getChildAt))
    }

    private fun bindResult(binding: FragmentQuizBinding, result: QuizUiState.Result) {
        val character = result.character
        binding.resultImage.load(character.image?.mediumUrl) { crossfade(true) }
        binding.resultTitle.text = getString(R.string.quiz_result, character.name)
        binding.resultHint.text = getString(R.string.quiz_result_hint, character.name)
        binding.openButton.setOnClickListener {
            findNavController().navigate(
                R.id.characterDetailFragment,
                bundleOf("apiDetailUrl" to character.apiDetailUrl, "characterName" to character.name)
            )
        }
        binding.heroButton.setOnClickListener {
            viewModel.setAsHero(character)
            Toast.makeText(requireContext(), R.string.quiz_hero_saved, Toast.LENGTH_SHORT).show()
        }
        staggerIn(listOf(binding.resultImage, binding.resultTitle, binding.resultHint))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
