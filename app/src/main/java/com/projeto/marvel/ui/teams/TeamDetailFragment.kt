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
import com.projeto.marvel.data.remote.CharacterRef
import com.projeto.marvel.data.remote.TeamDetail
import com.projeto.marvel.databinding.FragmentTeamDetailBinding
import com.projeto.marvel.databinding.ItemTagLinkBinding
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.staggerIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class TeamDetailFragment : Fragment(R.layout.fragment_team_detail) {

    private val viewModel: TeamDetailViewModel by viewModels()
    private var binding: FragmentTeamDetailBinding? = null

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
            is TeamDetailUiState.Success -> bindTeam(state.team)
            is TeamDetailUiState.Error ->
                binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            TeamDetailUiState.Loading -> Unit
        }
    }

    private fun bindTeam(team: TeamDetail) {
        val binding = binding ?: return
        binding.name.text = team.name
        binding.subtitle.text = team.publisher?.name.orEmpty()
        binding.image.load(team.image?.mediumUrl) { crossfade(true) }

        val members = team.members.orEmpty()
        val enemies = team.enemies.orEmpty()
        val numbers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
        binding.membersValue.text = numbers.format(team.memberCount ?: members.size)
        binding.enemiesValue.text = numbers.format(enemies.size)

        // `deck` é o resumo curto; `description` vem como HTML longo.
        binding.description.text = team.deck?.takeIf { it.isNotBlank() }
            ?: team.description?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT) }
            ?: getString(R.string.detail_no_description)

        bindPeople(binding.membersGroup, members)
        bindPeople(binding.enemiesGroup, enemies)

        val sections = listOf(binding.stats, binding.aboutTitle, binding.description) +
            listOf(binding.membersTitle, binding.membersGroup).takeIf { members.isNotEmpty() }.orEmpty() +
            listOf(binding.enemiesTitle, binding.enemiesGroup).takeIf { enemies.isNotEmpty() }.orEmpty()
        sections.forEach { it.visibility = View.VISIBLE }
        staggerIn(listOf(binding.name, binding.subtitle) + sections)
    }

    /** Cada pessoa vira uma tag clicável que abre o detalhe do personagem. */
    private fun bindPeople(group: ViewGroup, people: List<CharacterRef>) {
        group.removeAllViews()
        people.forEach { person ->
            val name = person.name ?: return@forEach
            val url = person.apiDetailUrl ?: return@forEach
            ItemTagLinkBinding.inflate(layoutInflater, group, true).root.apply {
                text = name
                contentDescription = getString(R.string.team_member_action, name)
                setOnClickListener { openCharacter(url, name) }
            }
        }
    }

    private fun openCharacter(apiDetailUrl: String, name: String) {
        val args = Bundle().apply {
            putString("apiDetailUrl", apiDetailUrl)
            putString("characterName", name)
        }
        findNavController().navigate(R.id.action_team_detail_to_character, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
