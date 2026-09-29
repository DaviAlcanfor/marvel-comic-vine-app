package com.projeto.marvel.ui.compare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.projeto.marvel.databinding.FragmentCompareBinding
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch

class CompareFragment : Fragment(R.layout.fragment_compare) {

    private val viewModel: CompareViewModel by viewModels()
    private var binding: FragmentCompareBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentCompareBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.message.setOnClickListener { viewModel.load() }
        binding.fightButton.setOnClickListener {
            val (left, right) = viewModel.urls
            findNavController().navigate(R.id.battleFragment, bundleOf("playerUrl" to left, "opponentUrl" to right))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: CompareUiState) {
        val binding = binding ?: return
        binding.loading.isVisible = state is CompareUiState.Loading
        binding.message.isVisible = state is CompareUiState.Error
        binding.content.isVisible = state is CompareUiState.Success
        if (state is CompareUiState.Error) {
            binding.message.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        if (state is CompareUiState.Success) bind(binding, state)
    }

    private fun bind(binding: FragmentCompareBinding, state: CompareUiState.Success) {
        val (left, right) = state.left to state.right
        binding.leftImage.load(left.character.image?.mediumUrl) { crossfade(true) }
        binding.rightImage.load(right.character.image?.mediumUrl) { crossfade(true) }
        binding.leftName.text = left.character.name
        binding.rightName.text = right.character.name
        val rows = fighterRows(left.fighter, right.fighter) +
            CompareRow(
                R.string.compare_appearances,
                left.character.issueAppearances ?: 0,
                right.character.issueAppearances ?: 0,
                max = null
            )
        bindComparison(binding.rows, rows)
        binding.debut.text = getString(
            R.string.compare_debut,
            left.character.birth ?: getString(R.string.compare_unknown),
            right.character.birth ?: getString(R.string.compare_unknown)
        )
        binding.commonPowers.text = state.commonPowers.joinToString(" · ").ifEmpty { getString(R.string.compare_none) }
        binding.commonTeams.text = state.commonTeams.joinToString(" · ").ifEmpty { getString(R.string.compare_none) }
        staggerIn((0 until binding.rows.childCount).map(binding.rows::getChildAt))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
