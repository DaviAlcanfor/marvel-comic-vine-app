package com.projeto.marvel.ui.creators

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentCatalogBinding
import com.projeto.marvel.ui.bindRecentSearches
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.info.InfoDetailFragment
import com.projeto.marvel.ui.submitAnimated
import kotlinx.coroutines.launch

/** Aba Criadores do Descobrir: quem escreveu e desenhou os personagens. */
class CreatorsFragment : Fragment(R.layout.fragment_catalog) {

    private val viewModel: CreatorsViewModel by viewModels()
    private var binding: FragmentCatalogBinding? = null
    private val adapter = CreatorAdapter(onClick = { person ->
        val url = person.apiDetailUrl ?: return@CreatorAdapter
        findNavController().navigate(
            R.id.infoDetailFragment,
            InfoDetailFragment.args(InfoDetailFragment.KIND_CREATOR, url, person.name)
        )
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentCatalogBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.intro.setText(R.string.creators_intro)
        binding.searchInput.setHint(R.string.creators_search_hint)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        binding.messageText.setOnClickListener { viewModel.retry() }
        binding.searchInput.doAfterTextChanged {
            viewModel.search(it?.toString())
            bindRecentSearches(
                binding.recentScroll,
                binding.recentGroup,
                binding.searchInput,
                viewModel.recentSearches.value,
                viewModel::forgetSearch
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentSearches.collect { queries ->
                        val b = binding ?: return@collect
                        bindRecentSearches(
                            b.recentScroll,
                            b.recentGroup,
                            b.searchInput,
                            queries,
                            viewModel::forgetSearch
                        )
                    }
                }
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: CreatorsUiState) {
        val binding = binding ?: return
        val creators = (state as? CreatorsUiState.Success)?.creators.orEmpty()
        binding.progressBar.fadeVisible(state is CreatorsUiState.Loading)
        binding.recyclerView.fadeVisible(creators.isNotEmpty())
        val empty = state is CreatorsUiState.Success && creators.isEmpty()
        binding.messageText.fadeVisible(state is CreatorsUiState.Error || empty)
        binding.messageText.text = when (state) {
            is CreatorsUiState.Error -> getString(R.string.home_error_retry) + "\n" + state.message
            else -> getString(R.string.creators_empty)
        }
        adapter.submitAnimated(creators, binding.recyclerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
