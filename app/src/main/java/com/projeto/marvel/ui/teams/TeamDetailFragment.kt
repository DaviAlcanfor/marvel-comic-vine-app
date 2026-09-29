package com.projeto.marvel.ui.teams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentTeamDetailBinding
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.characters.CharacterAdapter
import com.projeto.marvel.ui.staggerIn
import com.projeto.marvel.ui.submitCarousel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class TeamDetailFragment : Fragment(R.layout.fragment_team_detail) {

    private val viewModel: TeamDetailViewModel by viewModels()
    private var binding: FragmentTeamDetailBinding? = null
    private var membersAdapter: CharacterAdapter? = null
    private var enemiesAdapter: CharacterAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTeamDetailBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.name.text = arguments?.getString("teamName")
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.errorText.setOnClickListener { viewModel.load() }
        binding.challengeButton.setOnClickListener {
            val args = Bundle().apply {
                putString("teamUrl", arguments?.getString("apiDetailUrl"))
                putString("teamName", binding.name.text.toString())
            }
            findNavController().navigate(R.id.action_team_detail_to_battle_select, args)
        }

        val cardWidth = resources.getDimensionPixelSize(R.dimen.member_card_width)
        membersAdapter = CharacterAdapter(cardWidth) { character, _ -> openCharacter(character) }
            .also { binding.membersList.adapter = it }
        enemiesAdapter = CharacterAdapter(cardWidth) { character, _ -> openCharacter(character) }
            .also { binding.enemiesList.adapter = it }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: TeamDetailUiState) {
        val binding = binding ?: return
        binding.progressBar.fadeVisible(state is TeamDetailUiState.Loading)
        binding.errorText.fadeVisible(state is TeamDetailUiState.Error)
        when (state) {
            is TeamDetailUiState.Success -> bindTeam(state)
            is TeamDetailUiState.Error ->
                binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            TeamDetailUiState.Loading -> Unit
        }
    }

    private fun bindTeam(state: TeamDetailUiState.Success) {
        val binding = binding ?: return
        val team = state.team
        binding.name.text = team.name
        binding.subtitle.text = team.publisher?.name.orEmpty()
        binding.image.load(team.image?.mediumUrl) { crossfade(true) }

        val members = state.members
        val enemies = state.enemies
        val numbers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
        binding.membersValue.text = numbers.format(team.memberCount ?: members.size)
        binding.enemiesValue.text = numbers.format(team.enemies.orEmpty().size)

        // `deck` é o resumo curto; `description` vem como HTML longo.
        binding.description.text = team.deck?.takeIf { it.isNotBlank() }
            ?: team.description?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT) }
            ?: getString(R.string.detail_no_description)

        membersAdapter?.submitCarousel(members, binding.membersList)
        enemiesAdapter?.submitCarousel(enemies, binding.enemiesList)

        val sections = listOf(binding.stats, binding.challengeButton, binding.aboutTitle, binding.description) +
            listOf(binding.membersTitle, binding.membersList).takeIf { members.isNotEmpty() }.orEmpty() +
            listOf(binding.enemiesTitle, binding.enemiesList).takeIf { enemies.isNotEmpty() }.orEmpty()
        sections.forEach { it.visibility = View.VISIBLE }
        staggerIn(listOf(binding.name, binding.subtitle) + sections)
    }

    private fun openCharacter(character: CharacterSummary) {
        val args = Bundle().apply {
            putString("apiDetailUrl", character.apiDetailUrl ?: return)
            putString("characterName", character.name)
        }
        findNavController().navigate(R.id.action_team_detail_to_character, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        membersAdapter = null
        enemiesAdapter = null
        binding = null
    }
}
