package com.projeto.marvel.ui.detail

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResultListener
import androidx.core.text.HtmlCompat
import androidx.core.widget.TextViewCompat
import androidx.navigation.fragment.findNavController
import androidx.palette.graphics.Palette
import coil.load
import com.projeto.marvel.databinding.ItemTagBinding
import com.google.android.material.transition.MaterialContainerTransform
import com.projeto.marvel.R
import com.projeto.marvel.ui.characters.CharactersFragment
import com.projeto.marvel.ui.chat.ChatFragment
import com.projeto.marvel.data.moveTypeOf
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentCharacterDetailBinding
import com.projeto.marvel.ui.color
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.tintCaption
import com.projeto.marvel.ui.icon
import com.projeto.marvel.ui.creators.CreatorAdapter
import com.projeto.marvel.ui.info.InfoDetailFragment
import com.projeto.marvel.ui.info.formatDate
import com.projeto.marvel.ui.movies.MovieAdapter
import com.projeto.marvel.ui.photo.PhotoFragment
import com.projeto.marvel.ui.staggerIn
import com.projeto.marvel.ui.submitCarousel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class CharacterDetailFragment : Fragment(R.layout.fragment_character_detail) {

    private val viewModel: CharacterDetailViewModel by viewModels()
    private var binding: FragmentCharacterDetailBinding? = null

    // A capa de fundo chega depois do personagem: sem isso, a tela inteira reanimaria.
    private var shownCharacter: CharacterSummary? = null

    private val creatorsAdapter by lazy {
        CreatorAdapter(resources.getDimensionPixelSize(R.dimen.member_card_width)) { person ->
            val url = person.apiDetailUrl ?: return@CreatorAdapter
            findNavController().navigate(
                R.id.infoDetailFragment,
                InfoDetailFragment.args(InfoDetailFragment.KIND_CREATOR, url, person.name)
            )
        }
    }

    private val moviesAdapter by lazy {
        MovieAdapter(resources.getDimensionPixelSize(R.dimen.member_card_width)) { movie ->
            val url = movie.apiDetailUrl ?: return@MovieAdapter
            findNavController().navigate(
                R.id.infoDetailFragment,
                InfoDetailFragment.args(InfoDetailFragment.KIND_MOVIE, url, movie.name)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Volta do seletor ("Comparar"): abre a comparação deste personagem com o escolhido.
        setFragmentResultListener(CharactersFragment.PICK_COMPARE) { _, result ->
            val current = (viewModel.state.value as? DetailUiState.Success)?.character?.apiDetailUrl
            val other = result.getString(CharactersFragment.KEY_URL)
            if (current != null && other != null) {
                findNavController().navigate(R.id.compareFragment, bundleOf("leftUrl" to current, "rightUrl" to other))
            }
        }
        // O card clicado na Home se expande até virar esta tela (e encolhe de volta no voltar).
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.nav_host_fragment
            duration = resources.getInteger(R.integer.motion_duration).toLong()
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(ContextCompat.getColor(requireContext(), R.color.background))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentCharacterDetailBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.root.transitionName = arguments?.getString("transitionName")

        binding.name.text = arguments?.getString("characterName")
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.compareButton.setOnClickListener {
            findNavController().navigate(
                R.id.charactersFragment,
                bundleOf(
                    CharactersFragment.ARG_PICK_HERO to true,
                    CharactersFragment.ARG_PICK_KEY to CharactersFragment.PICK_COMPARE,
                    CharactersFragment.ARG_PICK_TITLE to getString(R.string.compare_pick, binding.name.text)
                )
            )
        }
        binding.avatar.cameraDistance = TILT_CAMERA_DISTANCE * resources.displayMetrics.density
        viewLifecycleOwner.lifecycle.addObserver(TiltController(requireContext(), binding::tilt))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: DetailUiState) {
        val binding = binding ?: return
        binding.progressBar.fadeVisible(state is DetailUiState.Loading)
        binding.errorText.fadeVisible(state is DetailUiState.Error)

        when (state) {
            is DetailUiState.Success -> {
                if (state.character != shownCharacter) {
                    bindCharacter(state.character)
                    binding.bindFacts(state.character)
                }
                shownCharacter = state.character
                bindFirstIssue(binding, state.character, state.coverUrl)
                binding.creatorsList.adapter = binding.creatorsList.adapter ?: creatorsAdapter
                creatorsAdapter.submitCarousel(state.creators, binding.creatorsList)
                val hasCreators = state.creators.isNotEmpty()
                listOf(binding.creatorsTitle, binding.creatorsList).forEach { it.fadeVisible(hasCreators) }
                binding.moviesList.adapter = binding.moviesList.adapter ?: moviesAdapter
                moviesAdapter.submitCarousel(state.movies, binding.moviesList)
                listOf(binding.moviesTitle, binding.moviesList).forEach { it.fadeVisible(state.movies.isNotEmpty()) }
                binding.bindTimeline(state.timeline)
            }
            is DetailUiState.Error -> binding.errorText.text = state.message
            DetailUiState.Loading -> Unit
        }
    }

    private fun bindCharacter(character: CharacterSummary) {
        val binding = binding ?: return
        binding.name.text = character.name
        binding.subtitle.text = listOfNotNull(character.realName, character.origin?.name, character.publisher?.name)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        binding.loadPortrait(character.image?.mediumUrl)
        val photoUrl = character.image?.originalUrl ?: character.image?.mediumUrl
        binding.avatar.isClickable = photoUrl != null
        binding.avatar.importantForAccessibility =
            if (photoUrl != null) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
        binding.avatar.contentDescription = getString(R.string.detail_photo_open, character.name)
        binding.avatar.setOnClickListener { photoUrl?.let { showPhoto(it, character.name, binding.avatar.drawable) } }

        val powers = character.powers.orEmpty().map { it.name }
        val teams = character.teams.orEmpty().mapNotNull { it.name }
        val numbers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
        binding.appearancesValue.text = numbers.format(character.issueAppearances ?: 0)
        binding.teamsValue.text = numbers.format(teams.size)
        binding.powersValue.text = numbers.format(powers.size)

        // `deck` é o resumo curto (como no design); `description` vem como HTML longo.
        binding.description.text = character.deck?.takeIf { it.isNotBlank() }
            ?: character.description?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT) }
            ?: getString(R.string.detail_no_description)
        bindBioActions(character.id, binding.description, binding.translateButton, binding.listenButton)
        binding.startReadingButton.setOnClickListener {
            startReading(character.name.orEmpty(), binding.startReadingButton)
        }
        binding.connectionsButton.setOnClickListener {
            character.apiDetailUrl?.let { showConnections(it, binding.connectionsButton) }
        }

        bindTags(binding.powersGroup, powers).forEach { tag ->
            val type = moveTypeOf(tag.text.toString()) ?: return@forEach
            tag.setCompoundDrawablesRelativeWithIntrinsicBounds(type.icon, 0, 0, 0)
            TextViewCompat.setCompoundDrawableTintList(
                tag,
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), type.color))
            )
        }
        bindTeams(teams)

        binding.chatButton.text = getString(R.string.detail_chat, character.name)
        binding.chatButton.setOnClickListener {
            findNavController().navigate(R.id.action_detail_to_chat, ChatFragment.args(topic = character.name))
        }

        binding.photoButton.contentDescription = getString(R.string.detail_photo, character.name)
        binding.photoButton.setOnClickListener {
            val args = PhotoFragment.args(character.name, character.image?.mediumUrl)
            findNavController().navigate(R.id.photoFragment, args)
        }

        val actions = listOf(binding.stats, binding.chatButton, binding.photoButton, binding.compareButton)
        val sections = actions + listOf(binding.aboutTitle, binding.description) +
            listOf(binding.bioActions, binding.startReadingButton, binding.connectionsButton) +
            listOf(binding.powersTitle, binding.powersGroup).takeIf { powers.isNotEmpty() }.orEmpty() +
            listOf(binding.teamsTitle, binding.teamsGroup).takeIf { teams.isNotEmpty() }.orEmpty()
        sections.forEach { it.visibility = View.VISIBLE }
        staggerIn(listOf(binding.name, binding.subtitle) + sections)
    }

    /** Os [TEAMS_PREVIEW] primeiros times e um chip "ver todos (N)" que abre o resto no lugar. */
    private fun bindTeams(teams: List<String>) {
        val group = binding?.teamsGroup ?: return
        if (teams.size <= TEAMS_PREVIEW) {
            bindTags(group, teams)
            return
        }
        bindTags(group, teams.take(TEAMS_PREVIEW))
        ItemTagBinding.inflate(layoutInflater, group, true).root.apply {
            text = getString(R.string.detail_teams_all, teams.size)
            setTextColor(ContextCompat.getColor(context, R.color.accent_text))
            setOnClickListener { bindTags(group, teams) }
        }
    }

    private fun bindTags(group: ViewGroup, names: List<String>): List<TextView> {
        group.removeAllViews()
        return names.map { name -> ItemTagBinding.inflate(layoutInflater, group, true).root.apply { text = name } }
    }

    /** Foto em tela cheia (versão original, maior que a do avatar); toque em qualquer lugar fecha. */
    private fun showPhoto(url: String, name: String, placeholder: Drawable?) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val image = ImageView(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
            contentDescription = name
            setOnClickListener { dialog.dismiss() }
            load(url) {
                placeholder(placeholder)
                crossfade(true)
            }
        }
        dialog.setContentView(image)
        dialog.show()
    }

    /** Capa da primeira aparição: chega depois do personagem (ou nunca, se a API falhar). */
    private fun bindFirstIssue(
        binding: FragmentCharacterDetailBinding,
        character: CharacterSummary,
        coverUrl: String?
    ) {
        val issue = character.firstIssue
        if (coverUrl == null || issue == null || binding.firstIssueCover.tag == coverUrl) return
        binding.firstIssueCover.tag = coverUrl
        binding.firstIssueCover.load(coverUrl) { crossfade(true) }
        binding.firstIssueName.text = listOfNotNull(issue.name, issue.issueNumber?.let { "#$it" }).joinToString(" ")
        listOf(binding.firstIssueTitle, binding.firstIssue).forEach { it.fadeVisible(true) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shownCharacter = null
        binding = null
    }
}

private const val COVER_BLUR = 12f

/**
 * Foto do personagem no avatar e, ampliada e desfocada, no fundo; a cor dela (Palette) pinta a tela.
 * O bitmap é carregado sem "hardware" porque a Palette precisa ler os pixels.
 */
private fun FragmentCharacterDetailBinding.loadPortrait(url: String?) {
    avatar.load(url) {
        crossfade(true)
        allowHardware(false)
        listener(onSuccess = { _, result ->
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@listener
            Palette.from(bitmap).generate { palette ->
                val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch ?: return@generate
                // A View pode ter sido destruída enquanto a Palette calculava.
                if (root.isAttachedToWindow) applyAccent(swatch.rgb)
            }
        })
    }
    cover.setRenderEffect(RenderEffect.createBlurEffect(COVER_BLUR, COVER_BLUR, Shader.TileMode.DECAL))
    cover.load(url) { crossfade(true) }
}

/**
 * Pinta o Detalhe com a cor do personagem (vermelho do Homem-Aranha, verde do Hulk): caixas de
 * legenda e números. A cor é ajustada para continuar legível no fundo escuro.
 */
private fun FragmentCharacterDetailBinding.applyAccent(color: Int) {
    val background = ContextCompat.getColor(root.context, R.color.background)
    listOf(aboutTitle, powersTitle, teamsTitle, factsTitle, creatorsTitle, moviesTitle, firstIssueTitle)
        .forEach { it.tintCaption(color) }
    val readable = readableOn(background, color)
    listOf(appearancesValue, teamsValue, powersValue).forEach { it.setTextColor(readable) }
}


/** Curiosidades (apelidos, estreia, "morreu em") e criadores, que abrem o detalhe do criador. */
/** Curiosidades: apelidos, estreia e "morreu em". */
private fun FragmentCharacterDetailBinding.bindFacts(character: CharacterSummary) {
    val context = root.context
    val aliases = character.aliases?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(MAX_ALIASES)
    val facts = listOfNotNull(
        aliases?.takeIf { it.isNotEmpty() }?.let { context.getString(R.string.detail_fact_aliases, it.joinToString()) },
        formatDate(character.birth)?.let { context.getString(R.string.detail_fact_debut, it) },
        character.issuesDiedIn?.firstOrNull()?.name?.let { context.getString(R.string.detail_fact_died, it) }
    )
    this.facts.text = facts.joinToString("\n\n")
    listOf(factsTitle, this.facts).forEach { it.fadeVisible(facts.isNotEmpty()) }
}

private const val MAX_ALIASES = 6
private const val TEAMS_PREVIEW = 8

/**
 * 3D pelo giroscópio: o avatar inclina com o celular e o fundo desliza para o lado oposto
 * (parallax), como se estivessem em profundidades diferentes.
 */
private fun FragmentCharacterDetailBinding.tilt(pitch: Float, roll: Float) {
    avatar.rotationX = -pitch
    avatar.rotationY = roll
    val shift = root.resources.displayMetrics.density * PARALLAX_DP_PER_DEGREE
    cover.translationX = -roll * shift
    cover.translationY = pitch * shift
}

private const val TILT_CAMERA_DISTANCE = 8000f
private const val PARALLAX_DP_PER_DEGREE = 1.2f
