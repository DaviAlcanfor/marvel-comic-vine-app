package com.projeto.marvel.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.text.HtmlCompat
import androidx.navigation.fragment.findNavController
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentCharacterDetailBinding
import kotlinx.coroutines.launch

class CharacterDetailFragment : Fragment(R.layout.fragment_character_detail) {

    private val viewModel: CharacterDetailViewModel by viewModels()
    private var binding: FragmentCharacterDetailBinding? = null

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
        binding.progressBar.visibility = if (state is DetailUiState.Loading) View.VISIBLE else View.GONE
        binding.errorText.visibility = if (state is DetailUiState.Error) View.VISIBLE else View.GONE

        when (state) {
            is DetailUiState.Success -> bindCharacter(state.character)
            is DetailUiState.Error -> binding.errorText.text = state.message
            DetailUiState.Loading -> Unit
        }
    }

    private fun bindCharacter(character: CharacterSummary) {
        val binding = binding ?: return
        binding.name.text = character.name
        binding.realName.text = character.realName
        binding.realName.visibility = if (character.realName.isNullOrBlank()) View.GONE else View.VISIBLE
        // A Comic Vine devolve `description` como HTML bruto.
        binding.description.text = character.description
            ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT) }
            ?: getString(R.string.detail_no_description)
        binding.avatar.load(character.image?.mediumUrl)

        val publisher = character.publisher?.name
        binding.publisherBadge.text = publisher
        binding.publisherBadge.visibility = if (publisher.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
