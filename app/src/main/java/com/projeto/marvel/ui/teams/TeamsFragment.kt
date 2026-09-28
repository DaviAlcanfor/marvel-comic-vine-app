package com.projeto.marvel.ui.teams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentTeamsBinding
import com.projeto.marvel.ui.animateItemsIn
import com.projeto.marvel.ui.fadeVisible
import kotlinx.coroutines.launch

class TeamsFragment : Fragment(R.layout.fragment_teams) {

    private val viewModel: TeamsViewModel by viewModels()
    private var binding: FragmentTeamsBinding? = null
    private val adapter = TeamAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTeamsBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        binding.errorText.setOnClickListener { viewModel.load() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: TeamsUiState) {
        val binding = binding ?: return
        val isEmptySuccess = state is TeamsUiState.Success && state.teams.isEmpty()
        binding.progressBar.fadeVisible(state is TeamsUiState.Loading)
        binding.errorText.fadeVisible(state is TeamsUiState.Error || isEmptySuccess)
        binding.recyclerView.fadeVisible(state is TeamsUiState.Success && !isEmptySuccess)

        when (state) {
            is TeamsUiState.Success -> {
                val isNewList = adapter.currentList != state.teams
                adapter.submitList(state.teams) { if (isNewList) binding.recyclerView.animateItemsIn() }
                if (isEmptySuccess) binding.errorText.text = getString(R.string.teams_empty)
            }
            is TeamsUiState.Error -> binding.errorText.text = state.message
            TeamsUiState.Loading -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
