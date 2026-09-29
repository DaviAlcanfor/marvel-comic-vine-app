package com.projeto.marvel.ui.comics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentComicSearchBinding
import com.projeto.marvel.ui.bindRecentSearches
import com.projeto.marvel.ui.fadeVisible
import kotlinx.coroutines.launch

class ComicSearchFragment : Fragment(R.layout.fragment_comic_search) {

    private val viewModel: ComicSearchViewModel by viewModels()
    private var binding: FragmentComicSearchBinding? = null
    private val adapter = ComicAdapter(onClick = ::onComicClick)
    private val pickingSeries get() = arguments?.getBoolean(ARG_PICK_SERIES) == true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentComicSearchBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.recyclerView.adapter = adapter
        viewModel.series = pickingSeries
        if (pickingSeries) {
            binding.title.setText(R.string.comics_pick_series)
            binding.searchInput.setHint(R.string.comics_series_hint)
        }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
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

    private fun render(state: ComicSearchUiState) {
        val binding = binding ?: return
        val comics = (state as? ComicSearchUiState.Success)?.comics.orEmpty()
        binding.progressBar.fadeVisible(state is ComicSearchUiState.Loading)
        binding.recyclerView.fadeVisible(comics.isNotEmpty())
        adapter.submitList(comics)
        binding.messageText.text = when (state) {
            ComicSearchUiState.Idle -> getString(R.string.comics_search_prompt)
            is ComicSearchUiState.Error -> getString(R.string.home_error_retry) + "\n" + state.message
            is ComicSearchUiState.Success -> if (comics.isEmpty()) getString(R.string.comics_empty) else ""
            ComicSearchUiState.Loading -> ""
        }
    }

    private fun onComicClick(item: ComicItem) {
        if (!pickingSeries) return showComicDialog(item, viewModel::save, viewModel::remove)
        val series = item.comic
        setFragmentResult(
            PICK_SERIES,
            bundleOf(KEY_ID to series.id, KEY_NAME to series.title, KEY_IMAGE to series.coverUrl)
        )
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val ARG_PICK_SERIES = "pickSeries"
        const val PICK_SERIES = "pick_series"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_IMAGE = "image"
    }
}
