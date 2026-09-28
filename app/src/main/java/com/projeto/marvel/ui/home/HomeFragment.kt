package com.projeto.marvel.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.transition.Hold
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentHomeBinding
import com.projeto.marvel.ui.animateItemsIn
import com.projeto.marvel.ui.fadeVisible
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private var binding: FragmentHomeBinding? = null
    private val adapter = CharacterAdapter(onClick = ::openDetail)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentHomeBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)

        // Na volta do Detalhe, o card de destino da transição só existe depois do layout da lista.
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter

        binding.logoutButton.setOnClickListener {
            viewModel.signOut()
            exitTransition = null
            findNavController().navigate(R.id.action_home_to_login)
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString())
            }
        })

        binding.errorText.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: HomeUiState) {
        val binding = binding ?: return
        val isEmptySuccess = state is HomeUiState.Success && state.characters.isEmpty()
        binding.progressBar.fadeVisible(state is HomeUiState.Loading)
        binding.errorText.fadeVisible(state is HomeUiState.Error || isEmptySuccess)
        binding.recyclerView.fadeVisible(state is HomeUiState.Success && !isEmptySuccess)

        when (state) {
            is HomeUiState.Success -> {
                val isNewList = adapter.currentList != state.characters
                adapter.submitList(state.characters) { if (isNewList) binding.recyclerView.animateItemsIn() }
                if (isEmptySuccess) binding.errorText.text = getString(R.string.home_empty)
            }
            is HomeUiState.Error -> binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            HomeUiState.Loading -> Unit
        }
    }

    private fun openDetail(character: CharacterSummary, card: View) {
        val apiDetailUrl = character.apiDetailUrl ?: return
        val args = Bundle().apply {
            putString("apiDetailUrl", apiDetailUrl)
            putString("characterName", character.name)
            putString("transitionName", card.transitionName)
        }
        // Hold: a Home fica parada por baixo enquanto o card se expande no Detalhe.
        exitTransition = Hold().apply { duration = resources.getInteger(R.integer.motion_duration).toLong() }
        findNavController().navigate(
            R.id.action_home_to_detail,
            args,
            null,
            FragmentNavigatorExtras(card to card.transitionName)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
