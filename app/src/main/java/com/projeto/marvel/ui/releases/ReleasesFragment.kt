package com.projeto.marvel.ui.releases

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.ReleasesRepository
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.databinding.FragmentCatalogBinding
import com.projeto.marvel.databinding.ItemComicBinding
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.comics.applyEra
import com.projeto.marvel.ui.fadeVisible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface ReleasesUiState {
    data object Loading : ReleasesUiState
    data class Success(val issues: List<Issue>) : ReleasesUiState
    data class Error(val message: String) : ReleasesUiState
}

class ReleasesViewModel @JvmOverloads constructor(
    application: android.app.Application,
    private val repository: ReleasesRepository = ReleasesRepository()
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ReleasesUiState>(ReleasesUiState.Loading)
    val state: StateFlow<ReleasesUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ReleasesUiState.Loading
        viewModelScope.launch {
            _state.value = repository.thisWeek().fold(
                onSuccess = { ReleasesUiState.Success(it) },
                onFailure = { ReleasesUiState.Error(it.message ?: "Não deu para buscar os lançamentos.") }
            )
        }
    }
}

/** Aba Bancas do Descobrir: as HQs da Marvel que chegaram às bancas nesta semana. */
class ReleasesFragment : Fragment(R.layout.fragment_catalog) {

    private val viewModel: ReleasesViewModel by viewModels()
    private var binding: FragmentCatalogBinding? = null
    private val adapter = ReleaseAdapter { issue -> offer(issue) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentCatalogBinding.inflate(inflater, container, false)
        this.binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = requireNotNull(binding)
        binding.intro.setText(R.string.releases_intro)
        binding.searchInput.isVisible = false
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), GRID_COLUMNS)
        binding.recyclerView.adapter = adapter
        binding.messageText.setOnClickListener { viewModel.load() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.state.collect(::render) }
        }
    }

    private fun render(state: ReleasesUiState) {
        val binding = binding ?: return
        val issues = (state as? ReleasesUiState.Success)?.issues.orEmpty()
        binding.progressBar.fadeVisible(state is ReleasesUiState.Loading)
        binding.recyclerView.fadeVisible(issues.isNotEmpty())
        val empty = state is ReleasesUiState.Success && issues.isEmpty()
        binding.messageText.fadeVisible(state is ReleasesUiState.Error || empty)
        binding.messageText.text = when (state) {
            is ReleasesUiState.Error -> getString(R.string.home_error_retry) + "\n" + state.message
            else -> getString(R.string.releases_empty)
        }
        adapter.submitList(issues)
    }

    /** Toque numa edição: guardar em "Quero ler" ou lembrar na agenda do celular. */
    private fun offer(issue: Issue) {
        val options = arrayOf(getString(R.string.releases_want), getString(R.string.releases_remind))
        requireContext().comicDialog()
            .setTitle(issue.title)
            .setItems(options) { _, which -> if (which == 0) wantToRead(issue) else remind(issue) }
            .show()
    }

    private fun wantToRead(issue: Issue) {
        val store = ReadingStore(requireContext(), AuthRepository().currentUser?.uid)
        store.save(ReadComic(issue.id, issue.title, issue.image?.mediumUrl).withStatus(ReadingStatus.WANT_TO_READ))
        Toast.makeText(requireContext(), R.string.releases_added, Toast.LENGTH_SHORT).show()
    }

    /** Abre o Calendário com o lembrete pronto (sem pedir permissão: quem salva é o app de agenda). */
    private fun remind(issue: Issue) {
        val day = issue.storeDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, getString(R.string.releases_event, issue.title))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(requireContext(), R.string.releases_no_calendar, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private companion object {
        const val GRID_COLUMNS = 3
    }
}

private val DATE = DateTimeFormatter.ofPattern("dd/MM")

/** Capa da edição (mesmo card das HQs) com o dia em que chegou às bancas. */
private class ReleaseAdapter(private val onClick: (Issue) -> Unit) :
    ListAdapter<Issue, ReleaseAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply { applyEra() })

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val issue = getItem(position)
        val binding = holder.binding
        binding.title.text = issue.title
        binding.cover.load(issue.image?.mediumUrl) { crossfade(true) }
        val day = issue.storeDate?.let { runCatching { LocalDate.parse(it).format(DATE) }.getOrNull() }
        binding.rating.text = day?.let { binding.root.context.getString(R.string.releases_day, it) }.orEmpty()
        binding.root.setOnClickListener { onClick(issue) }
    }

    class ViewHolder(val binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root)

    private object Diff : DiffUtil.ItemCallback<Issue>() {
        override fun areItemsTheSame(oldItem: Issue, newItem: Issue) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Issue, newItem: Issue) = oldItem == newItem
    }
}
