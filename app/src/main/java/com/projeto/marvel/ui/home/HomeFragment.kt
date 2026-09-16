package com.projeto.marvel.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentHomeBinding
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

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter

        binding.teamsButton.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_teams)
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString())
            }
        })

        // TODO: a Comic Vine não expõe um campo de classificação herói/vilão, então estes
        // chips são apenas visuais por enquanto — só "Todos" reflete a lista real carregada.

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
        binding.progressBar.visibility = if (state is HomeUiState.Loading) View.VISIBLE else View.GONE
        binding.errorText.visibility = if (state is HomeUiState.Error || isEmptySuccess) View.VISIBLE else View.GONE
        val showList = state is HomeUiState.Success && !isEmptySuccess
        binding.recyclerView.visibility = if (showList) View.VISIBLE else View.GONE

        when (state) {
            is HomeUiState.Success -> {
                adapter.submitList(state.characters)
                if (isEmptySuccess) binding.errorText.text = getString(R.string.home_empty)
            }
            is HomeUiState.Error -> binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            HomeUiState.Loading -> Unit
        }
    }

    private fun openDetail(character: CharacterSummary) {
        val apiDetailUrl = character.apiDetailUrl ?: return
        val args = Bundle().apply {
            putString("apiDetailUrl", apiDetailUrl)
            putString("characterName", character.name)
        }
        findNavController().navigate(R.id.action_home_to_detail, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
