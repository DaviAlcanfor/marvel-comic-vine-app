package com.projeto.marvel.ui.info

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentInfoDetailBinding
import com.projeto.marvel.databinding.ItemStatBinding
import com.projeto.marvel.ui.characters.CharacterAdapter
import com.projeto.marvel.data.RatedMovie
import com.projeto.marvel.ui.comics.stars
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.movies.showMovieDialog
import com.projeto.marvel.ui.submitCarousel
import kotlinx.coroutines.launch

/** Detalhe de criador ou de filme (Descobrir). O tipo vem em [ARG_KIND]. */
class InfoDetailFragment : Fragment(R.layout.fragment_info_detail) {

    private val viewModel: InfoDetailViewModel by viewModels()
    private var binding: FragmentInfoDetailBinding? = null
    private var shown: InfoDetail? = null
    private val charactersAdapter by lazy {
        CharacterAdapter(resources.getDimensionPixelSize(R.dimen.member_card_width)) { character, _ ->
            val url = character.apiDetailUrl ?: return@CharacterAdapter
            findNavController().navigate(
                R.id.characterDetailFragment,
                bundleOf("apiDetailUrl" to url, "characterName" to character.name)
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentInfoDetailBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        shown = null
        binding.title.text = arguments?.getString(ARG_TITLE)
        binding.characters.adapter = charactersAdapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.errorText.setOnClickListener { viewModel.load() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.rated.collect(::renderRating) }
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: InfoDetailUiState) {
        val binding = binding ?: return
        binding.progressBar.fadeVisible(state is InfoDetailUiState.Loading)
        binding.errorText.fadeVisible(state is InfoDetailUiState.Error)
        if (state is InfoDetailUiState.Error) {
            binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
        }
        val info = (state as? InfoDetailUiState.Success)?.info ?: return
        // Os personagens chegam depois: só eles mudam, o resto não é redesenhado.
        if (info.copy(characters = emptyList()) != shown?.copy(characters = emptyList())) bind(binding, info)
        shown = info
        val hasCharacters = info.characters.isNotEmpty()
        binding.charactersTitle.text = getString(info.charactersTitle, info.characterCount)
        listOf(binding.charactersTitle, binding.characters).forEach { it.fadeVisible(hasCharacters) }
        charactersAdapter.submitCarousel(info.characters, binding.characters)
    }

    private fun bind(binding: FragmentInfoDetailBinding, info: InfoDetail) {
        binding.title.text = info.title
        binding.subtitle.text = info.subtitle
        binding.image.load(info.imageUrl) { crossfade(true) }
        binding.stats.removeAllViews()
        info.stats.forEach { stat ->
            ItemStatBinding.inflate(layoutInflater, binding.stats, true).apply {
                value.text = stat.value
                label.setText(stat.label)
            }
        }
        binding.stats.fadeVisible(info.stats.isNotEmpty())
        val description = info.description?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT) }
        binding.description.text = description
        listOf(binding.aboutTitle, binding.description).forEach { it.fadeVisible(!description.isNullOrBlank()) }
        renderRating(viewModel.rated.value)
        binding.mapButton.fadeVisible(info.mapQuery != null)
        binding.mapButton.setOnClickListener { info.mapQuery?.let(::openMap) }
    }

    /** Filme: "Avaliar filme", ou a avaliação já feita (tocar edita). */
    private fun renderRating(rated: RatedMovie?) {
        val binding = binding ?: return
        val base = viewModel.movieBase ?: return
        binding.rateButton.text = when {
            rated == null -> getString(R.string.movie_rate)
            !rated.watched -> getString(R.string.movie_status_want)
            rated.rating == 0 -> getString(R.string.movie_watched_unrated)
            else -> getString(R.string.movie_your_rating, stars(rated.rating))
        }
        binding.rateButton.fadeVisible(true)
        binding.rateButton.setOnClickListener {
            showMovieDialog(base, viewModel.rated.value, viewModel::saveMovie, viewModel::removeMovie)
        }
    }

    /** Abre o lugar no app de mapas; sem app, no Google Maps do navegador. */
    private fun openMap(query: String) {
        val encoded = Uri.encode(query)
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"))
        try {
            startActivity(geo)
        } catch (_: ActivityNotFoundException) {
            startActivity(web)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_URL = "apiDetailUrl"
        const val ARG_TITLE = "title"
        const val KIND_CREATOR = "creator"
        const val KIND_MOVIE = "movie"
        const val KIND_LOCATION = "location"

        fun args(kind: String, apiDetailUrl: String, title: String) =
            bundleOf(ARG_KIND to kind, ARG_URL to apiDetailUrl, ARG_TITLE to title)
    }
}
