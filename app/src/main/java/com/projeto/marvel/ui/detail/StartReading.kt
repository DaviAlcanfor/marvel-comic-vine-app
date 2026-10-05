package com.projeto.marvel.ui.detail

import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.projeto.marvel.R
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingGuide
import com.projeto.marvel.data.ReadingPick
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.ui.comicDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** "Por onde começar a ler?" no Detalhe: até 5 séries em ordem, com o porquê, e "guardar todas". */
fun Fragment.startReading(hero: String, button: Button) {
    button.isEnabled = false
    button.setText(R.string.start_reading_loading)
    viewLifecycleOwner.lifecycleScope.launch {
        val result = ReadingGuide().startHere(hero)
        // runCatching engole o cancelamento (saiu da tela no meio): não é erro e não há mais contexto.
        button.isEnabled = true
        button.setText(R.string.start_reading)
        result.onSuccess { picks ->
            if (picks.isEmpty()) toast(getString(R.string.start_reading_none)) else show(hero, picks)
        }.onFailure { if (it !is CancellationException) toast(it.message.orEmpty()) }
    }
}

private fun Fragment.show(hero: String, picks: List<ReadingPick>) {
    val text = picks.mapIndexed { index, pick ->
        getString(
            R.string.start_reading_item,
            index + 1,
            pick.series.name.orEmpty(),
            pick.series.startYear.orEmpty(),
            pick.series.issues ?: 0,
            pick.why
        )
    }.joinToString("\n\n")
    requireContext().comicDialog()
        .setTitle(getString(R.string.start_reading_title, hero))
        .setMessage(text)
        .setNegativeButton(R.string.marathon_close, null)
        .setPositiveButton(R.string.start_reading_save) { _, _ ->
            val store = ReadingStore(requireContext(), AuthRepository().currentUser?.uid)
            // Ao contrário: a 1ª da ordem fica no topo da estante.
            picks.asReversed().forEach { pick ->
                store.save(
                    ReadComic(pick.series.id, pick.series.name.orEmpty(), pick.series.image?.mediumUrl)
                        .withStatus(ReadingStatus.WANT_TO_READ)
                )
            }
            toast(getString(R.string.start_reading_saved, picks.size))
        }
        .show()
}

private fun Fragment.toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
