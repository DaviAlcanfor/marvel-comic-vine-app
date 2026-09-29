package com.projeto.marvel.ui.battle

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.projeto.marvel.R
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.StickerStore
import com.projeto.marvel.data.UpgradeStore
import com.projeto.marvel.data.upgraded
import com.projeto.marvel.data.levelFor
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.FragmentBattleSelectBinding
import com.projeto.marvel.ui.onEndReached
import com.projeto.marvel.ui.submitAnimated
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.characters.CharacterAdapter
import com.projeto.marvel.ui.characters.CharactersUiState
import com.projeto.marvel.ui.characters.CharactersViewModel
import kotlinx.coroutines.launch

/**
 * Usada em dois passos: sem `playerUrl` nos argumentos escolhe o lutador do jogador; com ele,
 * escolhe o adversário (ou sorteia). A escolha viaja pelos argumentos da navegação, então
 * sobrevive a rotação sem estado extra.
 */
class BattleSelectFragment : Fragment(R.layout.fragment_battle_select) {

    // A busca é idêntica à da Home: reaproveita a ViewModel em vez de duplicar a lógica.
    private val viewModel: CharactersViewModel by viewModels()
    private var binding: FragmentBattleSelectBinding? = null
    private val previewViewModel: FighterPreviewViewModel by viewModels()

    // Toque abre a ficha (atributos e golpes); a escolha só acontece no botão "Escolher" dela.
    // Já no trio: o toque só desmarca, sem abrir a ficha de novo.
    // Escolhendo o SEU lutador, só quem você tem no álbum (nível pelas repetidas).
    private val adapter: CharacterAdapter = CharacterAdapter { character, _ ->
        val url = character.apiDetailUrl ?: return@CharacterAdapter
        val level = adapter.levels?.get(character.id) ?: if (gating) 0 else 1
        when {
            squad.any { it.id == character.id } -> pickForSquad(character)
            level == 0 -> {
                val message = getString(R.string.battle_locked_toast, character.name)
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
            else -> {
                val upgrades = UpgradeStore(requireContext())
                val id = character.id
                // Só o seu lutador vem melhorado; o adversário aparece como a CPU vai usar (Nv 1).
                val upgrade: (Fighter) -> Fighter = { fighter ->
                    if (gating) fighter.upgraded(level, upgrades.allocation(id), upgrades.isGolden(id)) else fighter
                }
                val load = suspend { previewViewModel.fighter(url) }
                showFighterPreview(character, level, upgrade, load) { select(character) }
            }
        }
    }

    /** Passo de escolher o próprio lutador (e não o adversário): vale a progressão do álbum. */
    private val gating get() = playerUrl == null

    private val playerUrl get() = arguments?.getString("playerUrl")

    // 3×3: o trio sendo montado. ponytail: some se a tela girar no meio; salvar no Bundle se incomodar.
    private val squad = mutableListOf<CharacterSummary>()
    private val teamUrl get() = arguments?.getString("teamUrl")

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

        binding.title.text = when {
            teamUrl != null -> getString(R.string.battle_select_team, arguments?.getString("teamName"))
            choosingOpponent -> getString(R.string.battle_select_opponent)
            else -> getString(R.string.battle_select_player)
        }
        binding.selectedPlayer.text = getString(R.string.battle_selected_player, arguments?.getString("playerName"))
        binding.selectedPlayer.visibility = if (choosingOpponent) View.VISIBLE else View.GONE
        binding.randomButton.visibility = if (choosingOpponent) View.VISIBLE else View.GONE
        binding.pvpSwitch.visibility = if (choosingOpponent) View.VISIBLE else View.GONE
        binding.squadSwitch.visibility = if (!choosingOpponent && teamUrl == null) View.VISIBLE else View.GONE
        binding.squadSwitch.setOnCheckedChangeListener { _, _ ->
            squad.clear()
            adapter.selected = emptyList()
            binding.selectedPlayer.visibility = View.GONE
        }
        // Primeiro passo é uma aba da barra inferior: voltar só faz sentido no passo do adversário
        // ou quando veio de um time.
        binding.backButton.visibility = if (choosingOpponent || teamUrl != null) View.VISIBLE else View.GONE
        binding.lockHint.visibility = if (gating) View.VISIBLE else View.GONE
        binding.randomButton.setOnClickListener { startBattle(opponentUrl = null) }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.onEndReached { viewModel.loadMore() }
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

    private fun render(state: CharactersUiState) {
        val binding = binding ?: return
        val isEmptySuccess = state is CharactersUiState.Success && state.characters.isEmpty()
        binding.progressBar.fadeVisible(state is CharactersUiState.Loading)
        binding.errorText.fadeVisible(state is CharactersUiState.Error || isEmptySuccess)
        binding.recyclerView.fadeVisible(state is CharactersUiState.Success && !isEmptySuccess)

        when (state) {
            is CharactersUiState.Success -> {
                // Relê o álbum a cada vez: abrir pacotes e voltar já libera/sobe os lutadores.
                val levels = if (gating) {
                    StickerStore(requireContext()).counts().mapValues { levelFor(it.value) }
                } else {
                    null
                }
                adapter.levels = levels
                val list = levels?.let { owned -> state.characters.sortedByDescending { owned[it.id] ?: 0 } }
                adapter.submitAnimated(list ?: state.characters, binding.recyclerView)
                if (isEmptySuccess) binding.errorText.text = getString(R.string.home_empty)
            }
            is CharactersUiState.Error ->
                binding.errorText.text = getString(R.string.home_error_retry) + "\n" + state.message
            CharactersUiState.Loading -> Unit
        }
    }

    private fun select(character: CharacterSummary) {
        val url = character.apiDetailUrl ?: return
        if (binding?.squadSwitch?.isChecked == true && playerUrl == null) {
            pickForSquad(character)
        } else if (teamUrl != null) {
            val args = Bundle().apply {
                putString("playerUrl", url)
                putString("teamUrl", teamUrl)
                putString("teamName", arguments?.getString("teamName"))
            }
            findNavController().navigate(R.id.action_battle_select_to_arena, args)
        } else if (playerUrl == null) {
            val args = Bundle().apply {
                putString("playerUrl", url)
                putString("playerName", character.name)
            }
            findNavController().navigate(R.id.action_battle_select_to_opponent, args)
        } else {
            startBattle(opponentUrl = url)
        }
    }

    /** Junta até 3; no terceiro vai direto para a arena contra um trio sorteado. */
    private fun pickForSquad(character: CharacterSummary) {
        // Tocar de novo num escolhido tira ele do trio.
        if (squad.none { it.id == character.id }) squad += character else squad.removeAll { it.id == character.id }
        adapter.selected = squad.map { it.id }
        val binding = binding ?: return
        binding.selectedPlayer.text = getString(R.string.battle_squad_picked, squad.size)
        binding.selectedPlayer.visibility = if (squad.isEmpty()) View.GONE else View.VISIBLE
        if (squad.size < SQUAD_SIZE) return
        val urls = squad.mapNotNull { it.apiDetailUrl }
        squad.clear()
        adapter.selected = emptyList()
        val args = Bundle().apply {
            putString("playerUrl", urls.first())
            putString("playerUrls", urls.joinToString(","))
        }
        findNavController().navigate(R.id.action_battle_select_to_arena, args)
    }

    private fun startBattle(opponentUrl: String?) {
        val args = Bundle().apply {
            putString("playerUrl", playerUrl)
            putString("opponentUrl", opponentUrl)
            putBoolean("pvp", binding?.pvpSwitch?.isChecked == true)
        }
        findNavController().navigate(R.id.action_battle_select_to_arena, args)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private companion object {
        const val SQUAD_SIZE = 3
    }
}
