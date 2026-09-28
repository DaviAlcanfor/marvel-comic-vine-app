package com.projeto.marvel.ui.battle

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentBattleSelectBinding
import com.projeto.marvel.ui.animateItemsIn
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.home.CharacterAdapter
import com.projeto.marvel.ui.home.HomeUiState
import com.projeto.marvel.ui.home.HomeViewModel
import kotlinx.coroutines.launch

/**
 * Usada em dois passos: sem `playerUrl` nos argumentos escolhe o lutador do jogador; com ele,
 * escolhe o adversário (ou sorteia). A escolha viaja pelos argumentos da navegação, então
 * sobrevive a rotação sem estado extra.
 */
class BattleSelectFragment : Fragment(R.layout.fragment_battle_select) {

    // A busca é idêntica à da Home: reaproveita a ViewModel em vez de duplicar a lógica.
    private val viewModel: HomeViewModel by viewModels()
    private var binding: FragmentBattleSelectBinding? = null
    private val adapter = CharacterAdapter { character, _ -> select(character) }

    private val playerUrl get() = arguments?.getString("playerUrl")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentBattleSelectBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        val choosingOpponent = playerUrl != null

        binding.title.setText(if (choosingOpponent) R.string.battle_select_opponent else R.string.battle_select_player)
        binding.selectedPlayer.text = getString(R.string.battle_selected_player, arguments?.getString("playerName"))
        binding.selectedPlayer.visibility = if (choosingOpponent) View.VISIBLE else View.GONE
        binding.randomButton.visibility = if (choosingOpponent) View.VISIBLE else View.GONE
        // Primeiro passo é uma aba da barra inferior: voltar só faz sentido no passo do adversário.
        binding.backButton.visibility = if (choosingOpponent) View.VISIBLE else View.GONE
        binding.randomButton.setOnClickListener { startBattle(opponentUrl = null) }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.errorText.setOnClickListener { viewModel.retry() }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString())
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: HomeUiState) {
        val binding = binding ?: return
        val isEmptySuccess = state is HomeUiState.Success && state.characters.isEmpty()
        binding.progressBar.fadeVisible(state is HomeUiState.Loading)
        binding.errorText.fadeVisible(state is HomeUiState.Error || isEmptySuccess)
        binding.recyclerView.fadeVisible(state is HomeUiState.Success && !isEmptySuccess)

        when (state) {
            is HomeUiState.Success -> {
                val isNewList = adapter.currentList != state.characters
                adapter.submitList(state.characters) { if (isNewList) binding.recyclerView.animateItemsIn() }
                if (isEmptySuccess) binding.errorText.text = getString(R.string.home_empty)
            }
            is HomeUiState.Error -> binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            HomeUiState.Loading -> Unit
        }
    }

    private fun select(character: CharacterSummary) {
        val url = character.apiDetailUrl ?: return
        if (playerUrl == null) {
            val args = Bundle().apply {
                putString("playerUrl", url)
                putString("playerName", character.name)
            }
            findNavController().navigate(R.id.action_battle_select_to_opponent, args)
        } else {
            startBattle(opponentUrl = url)
        }
    }

    private fun startBattle(opponentUrl: String?) {
        val args = Bundle().apply {
            putString("playerUrl", playerUrl)
            putString("opponentUrl", opponentUrl)
        }
        findNavController().navigate(R.id.action_battle_select_to_arena, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
