package com.projeto.marvel.ui.profile

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.chip.Chip
import com.projeto.marvel.R
import com.projeto.marvel.data.Achievement
import com.projeto.marvel.data.Favorite
import com.projeto.marvel.data.GENRES
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.data.ThemeMode
import com.projeto.marvel.data.ThemeStore
import com.projeto.marvel.databinding.FragmentProfileBinding
import com.projeto.marvel.ui.characters.CharactersFragment
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.comics.ComicAdapter
import com.projeto.marvel.ui.comics.ComicItem
import com.projeto.marvel.ui.comics.ComicSearchFragment
import com.projeto.marvel.ui.comics.showComicDialog
import com.projeto.marvel.ui.info.InfoDetailFragment
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch
import com.projeto.marvel.ui.eraEnter

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()
    private var binding: FragmentProfileBinding? = null
    private val adapter = ComicAdapter(flippable = true) { item ->
        showComicDialog(item, viewModel::save, viewModel::remove)
    }

    private val moviesAdapter by lazy {
        RatedMovieAdapter(resources.getDimensionPixelSize(R.dimen.member_card_width)) { movie ->
            val url = movie.apiDetailUrl ?: return@RatedMovieAdapter
            findNavController().navigate(
                R.id.infoDetailFragment,
                InfoDetailFragment.args(InfoDetailFragment.KIND_MOVIE, url, movie.title)
            )
        }
    }

    private val achievementsAdapter = AchievementAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Escolhas feitas nas telas de busca (herói / série) voltam por Fragment Result.
        setFragmentResultListener(CharactersFragment.PICK_HERO) { _, result ->
            viewModel.setHero(result.toFavorite(urlKey = CharactersFragment.KEY_URL))
        }
        setFragmentResultListener(ComicSearchFragment.PICK_SERIES) { _, result ->
            viewModel.setSeries(result.toFavorite(urlKey = null))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentProfileBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.shelf.adapter = adapter
        binding.moviesShelf.adapter = moviesAdapter
        binding.achievements.adapter = achievementsAdapter
        binding.applyEra()
        (binding.root.getChildAt(0) as ViewGroup).eraEnter()
        binding.addButton.setOnClickListener { findNavController().navigate(R.id.action_profile_to_comic_search) }
        binding.heroCard.setOnClickListener {
            findNavController().navigate(
                R.id.action_profile_to_pick_hero,
                bundleOf(CharactersFragment.ARG_PICK_HERO to true)
            )
        }
        binding.seriesCard.setOnClickListener {
            findNavController().navigate(
                R.id.action_profile_to_comic_search,
                bundleOf(ComicSearchFragment.ARG_PICK_SERIES to true)
            )
        }
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.themeButton.setOnClickListener { pickTheme() }
        binding.shareButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { shareProfileCard(requireContext(), viewModel.state.value) }
        }
        binding.logoutButton.setOnClickListener {
            viewModel.signOut()
            findNavController().navigate(R.id.action_profile_to_login)
        }
        val filters = mapOf(
            binding.filterRead.id to ReadingStatus.READ,
            binding.filterReading.id to ReadingStatus.READING,
            binding.filterWant.id to ReadingStatus.WANT_TO_READ
        )
        binding.filterGroup.setOnCheckedStateChangeListener { _, checked ->
            checked.firstOrNull()?.let { viewModel.showOnly(filters.getValue(it)) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh()
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: ProfileUiState) {
        val binding = binding ?: return
        val user = state.user
        binding.name.text = user?.name?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_guest)
        binding.email.text = user?.email.orEmpty()
        binding.avatar.load(user?.photoUrl) {
            crossfade(true)
            fallback(R.drawable.ic_person)
            error(R.drawable.ic_person)
        }

        val numbers = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply { maximumFractionDigits = 1 }
        binding.readValue.text = numbers.format(state.read.size)
        binding.ratingValue.text = state.averageRating?.let(numbers::format) ?: getString(R.string.profile_no_rating)
        binding.battlesValue.text = getString(R.string.battle_record, state.record.wins, state.record.losses)

        bindFavorite(state.preferences.hero, binding.heroImage, binding.heroName, R.drawable.ic_person)
        bindFavorite(state.preferences.series, binding.seriesImage, binding.seriesName, R.drawable.ic_move_magic)
        bindGenres(state.preferences.genres)

        val shelf = state.filtered
        adapter.submitList(shelf.map { ComicItem(it, onShelf = true) })
        binding.emptyText.text =
            getString(if (state.shelf.isEmpty()) R.string.profile_shelf_empty else R.string.profile_shelf_empty_filter)
        binding.emptyText.visibility = if (shelf.isEmpty()) View.VISIBLE else View.GONE
        moviesAdapter.submitList(state.movies)
        // Desbloqueadas primeiro (na ordem da tabela) e o placar no título.
        val medals = Achievement.entries.map { Medal(it, state.progress) }
            .sortedBy { !it.achievement.unlocked(it.progress) }
        achievementsAdapter.submitList(medals)
        val unlocked = medals.count { it.achievement.unlocked(it.progress) }
        binding.achievementsTitle.text = getString(R.string.profile_achievements_count, unlocked, medals.size)
        binding.moviesEmpty.visibility = if (state.movies.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun bindFavorite(favorite: Favorite?, image: ImageView, name: TextView, placeholder: Int) {
        name.text = favorite?.name ?: getString(R.string.profile_favorite_choose)
        image.load(favorite?.imageUrl) {
            crossfade(true)
            fallback(placeholder)
            error(placeholder)
        }
    }

    /** Gêneros marcados viram tags; o último chip abre a escolha. */
    private fun bindGenres(genres: List<String>) {
        val group = binding?.genresGroup ?: return
        group.removeAllViews()
        genres.forEach { genre -> group.addView(genreChip(genre)) }
        group.addView(
            Chip(requireContext()).apply {
                text = getString(R.string.profile_genres_edit)
                setOnClickListener { pickGenres(genres) }
            }
        )
    }

    /** Cada gênero com a sua cor (fixa pela posição em [GENRES]): fundo translúcido, borda cheia. */
    private fun genreChip(genre: String) = Chip(requireContext()).apply {
        val slot = GENRES.indexOf(genre).coerceAtLeast(0) % GENRE_COLORS.size
        val color = ContextCompat.getColor(context, GENRE_COLORS[slot])
        text = genre
        chipBackgroundColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, GENRE_FILL_ALPHA))
        chipStrokeColor = ColorStateList.valueOf(color)
        chipStrokeWidth = resources.getDimension(R.dimen.genre_chip_stroke)
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
    }

    private fun pickTheme() {
        val store = ThemeStore(requireContext())
        val labels = arrayOf(
            R.string.theme_system,
            R.string.theme_light,
            R.string.theme_retro_dark,
            R.string.theme_nineties,
            R.string.theme_dark
        )
            .map(::getString)
        requireContext().comicDialog()
            .setTitle(R.string.profile_theme_title)
            .setSingleChoiceItems(labels.toTypedArray(), ThemeMode.entries.indexOf(store.get())) { dialog, which ->
                dialog.dismiss()
                val before = store.get()
                store.set(ThemeMode.entries[which])
                // Retrô escuro e Anos 90 são sobreposição de tema, não modo noturno: o AppCompat
                // não recria sozinho quando só ela muda.
                if (before.overlay != ThemeMode.entries[which].overlay) requireActivity().recreate()
            }
            .setNeutralButton(R.string.opening_pick) { _, _ -> requireContext().pickOpening() }
            .show()
    }

    private fun pickGenres(current: List<String>) {
        val checked = GENRES.map { it in current }.toBooleanArray()
        requireContext().comicDialog()
            .setTitle(R.string.profile_genres_title)
            .setMultiChoiceItems(GENRES.toTypedArray(), checked) { _, index, isChecked -> checked[index] = isChecked }
            .setPositiveButton(R.string.profile_genres_save) { _, _ ->
                viewModel.setGenres(GENRES.filterIndexed { index, _ -> checked[index] })
            }
            .setNegativeButton(R.string.comics_dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}

/** As duas buscas devolvem as mesmas chaves (`id`, `name`, `image`); só o herói traz a url. */
private fun Bundle.toFavorite(urlKey: String?) = Favorite(
    id = getInt(CharactersFragment.KEY_ID),
    name = getString(CharactersFragment.KEY_NAME).orEmpty(),
    imageUrl = getString(CharactersFragment.KEY_IMAGE),
    apiDetailUrl = urlKey?.let(::getString)
)

private const val GENRE_FILL_ALPHA = 70

/** Paleta dos golpes (já testada em contraste no fundo escuro), em rodízio pelos gêneros. */
private val GENRE_COLORS = listOf(
    R.color.move_strike, R.color.move_blast, R.color.move_water, R.color.move_heal, R.color.move_magic,
    R.color.move_dodge, R.color.move_freeze, R.color.move_poison, R.color.move_drain, R.color.move_guard
)
