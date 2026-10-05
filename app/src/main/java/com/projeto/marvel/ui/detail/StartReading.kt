package com.projeto.marvel.ui.detail

import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.projeto.marvel.databinding.ItemReadingPickBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.retroInk
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
    val context = requireContext()
    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val burstPad = resources.getDimensionPixelSize(R.dimen.space_md)
    picks.forEachIndexed { index, pick ->
        ItemReadingPickBinding.inflate(layoutInflater, list, true).apply {
            number.text = (index + 1).toString()
            number.comicBox(BoxStyle.BURST, context.retroInk(index))
            // A folga padrão da explosão é para palavras; para um número só, bem menos.
            number.setPadding(burstPad, burstPad, burstPad, burstPad)
            name.text = pick.series.name.orEmpty()
            // Cada série numa tinta diferente da explosão ao lado.
            name.comicBox(BoxStyle.CAPTION, context.retroInk(index + PICK_INK_SHIFT))
            meta.text = getString(R.string.start_reading_meta, pick.series.startYear.orEmpty(), pick.series.issues ?: 0)
            why.text = pick.why
        }
    }
    val pad = resources.getDimensionPixelSize(R.dimen.space_lg)
    list.setPadding(pad, pad, pad, 0)
    context.comicDialog()
        .setTitle(getString(R.string.start_reading_title, hero))
        .setView(ScrollView(context).apply { addView(list) })
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

private const val PICK_INK_SHIFT = 2

private fun Fragment.toast(message: String) = Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
