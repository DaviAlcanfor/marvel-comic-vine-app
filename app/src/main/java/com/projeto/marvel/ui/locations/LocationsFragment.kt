package com.projeto.marvel.ui.locations

import com.projeto.marvel.ui.dressCard
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.databinding.FragmentCatalogBinding
import com.projeto.marvel.databinding.ItemCharacterBinding
import com.projeto.marvel.ui.fadeVisible
import com.projeto.marvel.ui.info.InfoDetailFragment
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/** Aba Lugares do Descobrir: cidades reais (com mapa) e lugares fictícios das HQs. */
class LocationsFragment : Fragment(R.layout.fragment_catalog) {

    private val viewModel: LocationsViewModel by viewModels()
    private var binding: FragmentCatalogBinding? = null
    private val adapter = PlaceAdapter { place ->
        val url = place.location.apiDetailUrl ?: return@PlaceAdapter
        findNavController().navigate(
            R.id.infoDetailFragment,
            InfoDetailFragment.args(InfoDetailFragment.KIND_LOCATION, url, place.location.name)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentCatalogBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.intro.setText(R.string.locations_intro)
        binding.searchInput.setHint(R.string.locations_search_hint)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerView.adapter = adapter
        binding.messageText.setOnClickListener { viewModel.load() }
        binding.searchInput.doAfterTextChanged { viewModel.search(it?.toString()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: LocationsUiState) {
        val binding = binding ?: return
        val places = (state as? LocationsUiState.Success)?.places.orEmpty()
        binding.progressBar.fadeVisible(state is LocationsUiState.Loading)
        binding.recyclerView.fadeVisible(places.isNotEmpty())
        val empty = state is LocationsUiState.Success && places.isEmpty()
        binding.messageText.fadeVisible(state is LocationsUiState.Error || empty)
        binding.messageText.text = when (state) {
            is LocationsUiState.Error -> getString(R.string.home_error_retry) + "\n" + state.message
            else -> getString(R.string.locations_empty)
        }
        adapter.submitList(places)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}

/** Mesmo card da lista de personagens: imagem, nome e "Lugar real · 24.171 aparições". */
class PlaceAdapter(
    private val onClick: (Place) -> Unit
) : ListAdapter<Place, PlaceAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
                dressCard(root, thumbnail, name, subtitle)
            }
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(place: Place) {
            val context = binding.root.context
            val appearances = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"))
                .format(place.location.appearances ?: 0)
            binding.name.text = place.location.name
            binding.subtitle.text = context.getString(
                if (place.real) R.string.locations_real else R.string.locations_fictional,
                appearances
            )
            binding.thumbnail.load(place.location.image?.mediumUrl) { crossfade(true) }
            binding.root.setOnClickListener { onClick(place) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Place>() {
        override fun areItemsTheSame(oldItem: Place, newItem: Place) = oldItem.location.id == newItem.location.id
        override fun areContentsTheSame(oldItem: Place, newItem: Place) = oldItem == newItem
    }
}
