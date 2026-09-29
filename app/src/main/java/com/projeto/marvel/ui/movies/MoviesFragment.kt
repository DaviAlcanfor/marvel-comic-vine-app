package com.projeto.marvel.ui.movies

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
import com.projeto.marvel.data.remote.Movie
import com.projeto.marvel.databinding.FragmentCatalogBinding
import com.projeto.marvel.ui.bindRecentSearches
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.info.InfoDetailFragment
import kotlinx.coroutines.launch

/** Aba Filmes do Descobrir: filmes da Marvel na Comic Vine. */
class MoviesFragment : Fragment(R.layout.fragment_catalog) {

    private val viewModel: MoviesViewModel by viewModels()
    private var binding: FragmentCatalogBinding? = null
    private val adapter = MovieAdapter { movie -> openMovie(movie) }

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
        binding.intro.setText(R.string.movies_intro)
        binding.searchInput.setHint(R.string.movies_search_hint)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.recyclerView.adapter = adapter
        binding.messageText.setOnClickListener { viewModel.load() }
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

    private fun render(state: MoviesUiState) {
        val binding = binding ?: return
        val movies = (state as? MoviesUiState.Success)?.movies.orEmpty()
        binding.progressBar.fadeVisible(state is MoviesUiState.Loading)
        binding.recyclerView.fadeVisible(movies.isNotEmpty())
        val empty = state is MoviesUiState.Success && movies.isEmpty()
        binding.messageText.fadeVisible(state is MoviesUiState.Error || empty)
        binding.messageText.text = when (state) {
            is MoviesUiState.Error -> getString(R.string.home_error_retry) + "\n" + state.message
            else -> getString(R.string.movies_empty)
        }
        adapter.submitList(movies)
    }

    private fun openMovie(movie: Movie) {
        val url = movie.apiDetailUrl ?: return
        findNavController().navigate(
            R.id.infoDetailFragment,
            InfoDetailFragment.args(InfoDetailFragment.KIND_MOVIE, url, movie.name)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private companion object {
        const val GRID_COLUMNS = 3
    }
}
