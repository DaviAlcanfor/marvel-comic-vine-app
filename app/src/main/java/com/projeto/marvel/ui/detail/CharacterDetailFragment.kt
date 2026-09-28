package com.projeto.marvel.ui.detail

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.databinding.ItemTagBinding
import com.google.android.material.transition.MaterialContainerTransform
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentCharacterDetailBinding
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class CharacterDetailFragment : Fragment(R.layout.fragment_character_detail) {

    private val viewModel: CharacterDetailViewModel by viewModels()
    private var binding: FragmentCharacterDetailBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            is DetailUiState.Success -> bindCharacter(state.character)
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
        binding.avatar.load(character.image?.mediumUrl) { crossfade(true) }

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

        bindTags(binding.powersGroup, powers)
        bindTags(binding.teamsGroup, teams)

        val sections = listOf(binding.stats, binding.aboutTitle, binding.description) +
            listOf(binding.powersTitle, binding.powersGroup).takeIf { powers.isNotEmpty() }.orEmpty() +
            listOf(binding.teamsTitle, binding.teamsGroup).takeIf { teams.isNotEmpty() }.orEmpty()
        sections.forEach { it.visibility = View.VISIBLE }
        staggerIn(listOf(binding.name, binding.subtitle) + sections)
    }

    private fun bindTags(group: ViewGroup, names: List<String>) {
        group.removeAllViews()
        names.forEach { name -> ItemTagBinding.inflate(layoutInflater, group, true).root.text = name }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
