package com.projeto.marvel.ui.home

import android.Manifest
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.Favorite
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentHomeBinding
import com.projeto.marvel.ui.comics.ComicAdapter
import com.projeto.marvel.ui.comics.ComicItem
import com.projeto.marvel.ui.characters.CharacterAdapter
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.era
import com.projeto.marvel.ui.detail.readableOn
import com.projeto.marvel.ui.info.InfoDetailFragment
import com.projeto.marvel.ui.movies.MovieAdapter
import com.projeto.marvel.ui.submitCarousel
import kotlinx.coroutines.launch
import com.projeto.marvel.ui.eraEnter

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val weatherCard: WeatherCard =
        WeatherCard(this) { askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    private val askLocation: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            binding?.let { if (granted) weatherCard.load(it) }
        }

    private val viewModel: HomeViewModel by viewModels()
    private var binding: FragmentHomeBinding? = null

    // Toque numa HQ em leitura leva à estante, onde dá para mudar o status.
    private val readingAdapter by lazy {
        ComicAdapter(cardWidth = resources.getDimensionPixelSize(R.dimen.member_card_width)) {
            findNavController().navigate(R.id.profileFragment)
        }
    }

    private val heroMoviesAdapter by lazy {
        MovieAdapter(resources.getDimensionPixelSize(R.dimen.member_card_width)) { movie ->
            val url = movie.apiDetailUrl ?: return@MovieAdapter
            findNavController().navigate(
                R.id.infoDetailFragment,
                InfoDetailFragment.args(InfoDetailFragment.KIND_MOVIE, url, movie.name)
            )
        }
    }

    private val debutsAdapter by lazy {
        CharacterAdapter(resources.getDimensionPixelSize(R.dimen.member_card_width)) { character, _ ->
            openCharacter(character.apiDetailUrl, character.name)
        }
    }

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
        binding.applyEra()
        weatherCard.bind(binding)
        (binding.root.getChildAt(0) as ViewGroup).eraEnter()
        binding.readingList.adapter = readingAdapter
        binding.heroMoviesList.adapter = heroMoviesAdapter
        binding.debutsList.adapter = debutsAdapter
        binding.avatar.setOnClickListener { findNavController().navigate(R.id.profileFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh()
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: HomeUiState) {
        val binding = binding ?: return
        binding.greeting.text = state.userName?.let { getString(R.string.home_greeting, it) }
            ?: getString(R.string.home_greeting_anonymous)

        val hero = state.heroOfTheDay
        binding.heroCard.fadeVisible(hero != null)
        if (hero != null && binding.heroImage.tag != hero.id) {
            binding.bindHero(hero)
            binding.heroCard.setOnClickListener { openCharacter(hero.apiDetailUrl, hero.name) }
        }

        binding.bindHeader(state) { favorite ->
            if (favorite != null) {
                openCharacter(favorite.apiDetailUrl, favorite.name)
            } else {
                findNavController().navigate(R.id.profileFragment)
            }
        }

        heroMoviesAdapter.submitCarousel(state.heroMovies, binding.heroMoviesList)
        val hasMovies = state.heroMovies.isNotEmpty()
        listOf(binding.heroMoviesTitle, binding.heroMoviesList).forEach { it.fadeVisible(hasMovies) }
        val roomForSfx = hasMovies && state.heroMovies.size == 1 && requireContext().era() != Era.MODERN
        binding.heroMoviesSfx.fadeVisible(roomForSfx)
        debutsAdapter.submitCarousel(state.debutedToday, binding.debutsList)
        listOf(binding.debutsTitle, binding.debutsList).forEach { it.fadeVisible(state.debutedToday.isNotEmpty()) }
        binding.missionList.bindMissions(state.missions, state.missionArt, viewModel::claim)

        binding.bindDailyTrail(state.dailyTrail) { trail ->
            findNavController().navigate(
                R.id.battleSelectFragment,
                bundleOf("teamUrl" to trail.teamUrl, "teamName" to trail.teamName)
            )
        }

        val reading = state.reading
        binding.readingTitle.visibility = if (reading.isNotEmpty()) View.VISIBLE else View.GONE
        binding.readingList.visibility = if (reading.isNotEmpty()) View.VISIBLE else View.GONE
        readingAdapter.submitCarousel(reading.map { ComicItem(it, onShelf = true) }, binding.readingList)
    }

    private fun openCharacter(apiDetailUrl: String?, name: String) {
        findNavController().navigate(
            R.id.action_home_to_detail,
            bundleOf("apiDetailUrl" to (apiDetailUrl ?: return), "characterName" to name)
        )
    }


    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}

/** Card da trilha do dia: adversários em fileira, do primeiro ao chefe (maior, com ★). */
private fun FragmentHomeBinding.bindDailyTrail(trail: DailyTrail?, onStart: (DailyTrail) -> Unit) {
    listOf(dailyTrailTitle, dailyTrailCard).forEach { it.fadeVisible(trail != null) }
    if (trail == null || dailyTrailTeam.text == trail.teamName) return
    val context = root.context
    dailyTrailTeam.text = trail.teamName
    dailyTrailHint.text = context.getString(R.string.home_daily_trail_hint, trail.rivals.size)
    dailyTrailRivals.removeAllViews()
    trail.rivals.forEachIndexed { index, rival ->
        val boss = index == trail.rivals.lastIndex
        val sizeRes = if (boss) R.dimen.trail_boss_size else R.dimen.trail_rival_size
        val size = context.resources.getDimensionPixelSize(sizeRes)
        dailyTrailRivals.addView(
            ImageView(context).apply {
                setBackgroundResource(if (boss) R.drawable.bg_trail_current else R.drawable.bg_circle)
                clipToOutline = true
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = rival.name
                load(rival.image?.mediumUrl) { crossfade(true) }
            },
            LinearLayout.LayoutParams(size, size).apply { marginEnd = size / TRAIL_GAP_DIVISOR }
        )
    }
    dailyTrailButton.setOnClickListener { onStart(trail) }
}

private const val TRAIL_GAP_DIVISOR = 4

/**
 * Cabeçalho: avatar = foto do herói preferido (ou do usuário); a linha abaixo da saudação leva ao
 * herói ([onFavorite] com ele) ou convida a escolher um (com null).
 */
private fun FragmentHomeBinding.bindHeader(state: HomeUiState, onFavorite: (Favorite?) -> Unit) {
    val favorite = state.favoriteHero
    val avatarUrl = favorite?.imageUrl ?: state.userPhoto
    if (avatar.tag != avatarUrl) {
        avatar.tag = avatarUrl
        avatar.load(avatarUrl) { error(R.drawable.ic_person) }
    }
    val context = root.context
    favoriteLine.text = favorite?.let { context.getString(R.string.home_your_hero_line, it.name) }
        ?: context.getString(R.string.home_pick_hero)
    favoriteLine.setOnClickListener { onFavorite(favorite) }
}

/** Herói do dia: foto, nome e resumo. */
private fun FragmentHomeBinding.bindHero(hero: CharacterSummary) {
    heroImage.tag = hero.id
    heroImage.load(hero.image?.mediumUrl) { crossfade(true) }
    // Retrô: o nome estoura numa explosão, com exclamação de capa.
    heroName.text = if (root.context.era() == Era.RETRO) "${hero.name}!" else hero.name
    heroDeck.text = hero.deck.orEmpty()
}
