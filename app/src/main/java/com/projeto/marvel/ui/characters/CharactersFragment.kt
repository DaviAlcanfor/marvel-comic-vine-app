package com.projeto.marvel.ui.characters

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.os.bundleOf
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.transition.Hold
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentCharactersBinding
import com.projeto.marvel.databinding.ItemFilterChipBinding
import com.projeto.marvel.ui.onEndReached
import com.projeto.marvel.ui.submitAnimated
import com.projeto.marvel.ui.discover.fitInDiscover
import com.projeto.marvel.ui.bindRecentSearches
import com.projeto.marvel.ui.fadeVisible
import kotlinx.coroutines.launch

/**
 * Lista/busca de personagens. Com `pickHero` nos argumentos, vira seletor do herói preferido do
 * Perfil: o toque devolve o personagem via Fragment Result ([PICK_HERO]) em vez de abrir o Detalhe.
 */
class CharactersFragment : Fragment(R.layout.fragment_characters) {

    private val viewModel: CharactersViewModel by viewModels()
    private var binding: FragmentCharactersBinding? = null
    private val adapter = CharacterAdapter(onClick = ::onCharacterClick)
    private val picking get() = arguments?.getBoolean(ARG_PICK_HERO) == true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentCharactersBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)

        // Na volta do Detalhe, o card de destino da transição só existe depois do layout da lista.
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.onEndReached { viewModel.loadMore() }

        if (picking) binding.title.text = arguments?.getString(ARG_PICK_TITLE) ?: getString(R.string.profile_pick_hero)
        binding.intro.isVisible = !picking
        fitInDiscover(binding.root, binding.title)
        bindOrigins(binding)

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString())
                renderRecent(viewModel.recentSearches.value)
            }
        })
        binding.searchInput.setOnEditorActionListener { input, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            viewModel.rememberSearch()
            ViewCompat.getWindowInsetsController(input)?.hide(WindowInsetsCompat.Type.ime())
            true
        }

        binding.errorText.setOnClickListener { viewModel.retry() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.recentSearches.collect(::renderRecent) }
                viewModel.state.collect(::render)
            }
        }
    }

    /** Chips "Todas" + origens da Comic Vine; o nome em inglês é o que a API devolve. */
    private fun bindOrigins(binding: FragmentCharactersBinding) {
        binding.originGroup.removeAllViews()
        (listOf<Pair<String?, Int>>(null to R.string.origin_all) + ORIGINS).forEach { (origin, label) ->
            ItemFilterChipBinding.inflate(layoutInflater, binding.originGroup, true).root.apply {
                id = View.generateViewId()
                setText(label)
                isChecked = origin == viewModel.origin
                setOnClickListener { viewModel.filterOrigin(origin) }
            }
        }
    }

    private fun renderRecent(queries: List<String>) {
        val binding = binding ?: return
        bindRecentSearches(
            binding.recentScroll,
            binding.recentGroup,
            binding.searchInput,
            queries,
            viewModel::forgetSearch
        )
    }

    private fun render(state: CharactersUiState) {
        val binding = binding ?: return
        val isEmptySuccess = state is CharactersUiState.Success && state.characters.isEmpty()
        binding.progressBar.fadeVisible(state is CharactersUiState.Loading)
        binding.errorText.fadeVisible(state is CharactersUiState.Error || isEmptySuccess)
        binding.recyclerView.fadeVisible(state is CharactersUiState.Success && !isEmptySuccess)

        when (state) {
            is CharactersUiState.Success -> {
                adapter.submitAnimated(state.characters, binding.recyclerView)
                if (isEmptySuccess) {
                    val empty = if (viewModel.origin != null) R.string.characters_filter_empty else R.string.home_empty
                    binding.errorText.text = getString(empty)
                }
            }
            is CharactersUiState.Error ->
                binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            CharactersUiState.Loading -> Unit
        }
    }

    private fun onCharacterClick(character: CharacterSummary, card: View) {
        viewModel.rememberSearch()
        if (!picking) return openDetail(character, card)
        setFragmentResult(
            arguments?.getString(ARG_PICK_KEY) ?: PICK_HERO,
            bundleOf(
                KEY_ID to character.id,
                KEY_NAME to character.name,
                KEY_IMAGE to character.image?.mediumUrl,
                KEY_URL to character.apiDetailUrl
            )
        )
        findNavController().navigateUp()
    }

    private fun openDetail(character: CharacterSummary, card: View) {
        val apiDetailUrl = character.apiDetailUrl ?: return
        val args = Bundle().apply {
            putString("apiDetailUrl", apiDetailUrl)
            putString("characterName", character.name)
            putString("transitionName", card.transitionName)
        }
        // Hold: a lista fica parada por baixo enquanto o card se expande no Detalhe. Dentro do
        // Descobrir, quem sai de cena é o fragment da aba (o pai), não esta página.
        (parentFragment ?: this).exitTransition =
            Hold().apply { duration = resources.getInteger(R.integer.motion_duration).toLong() }
        findNavController().navigate(
            R.id.characterDetailFragment,
            args,
            null,
            FragmentNavigatorExtras(card to card.transitionName)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val ARG_PICK_HERO = "pickHero"
        const val ARG_PICK_TITLE = "pickTitle"

        /** Chave do resultado; outra que não [PICK_HERO] evita trocar o listener do Perfil. */
        const val ARG_PICK_KEY = "pickKey"
        const val PICK_COMPARE = "pick_compare"
        const val PICK_HERO = "pick_hero"
        const val KEY_ID = "id"
        const val KEY_NAME = "name"
        const val KEY_IMAGE = "image"
        const val KEY_URL = "url"

        private val ORIGINS = listOf(
            "Human" to R.string.origin_human,
            "Mutant" to R.string.origin_mutant,
            "Alien" to R.string.origin_alien,
            "God/Eternal" to R.string.origin_god,
            "Radiation" to R.string.origin_radiation,
            "Robot" to R.string.origin_robot,
            "Cyborg" to R.string.origin_cyborg,
            "Animal" to R.string.origin_animal,
            "Infection" to R.string.origin_infection,
            "Other" to R.string.origin_other
        )
    }
}
