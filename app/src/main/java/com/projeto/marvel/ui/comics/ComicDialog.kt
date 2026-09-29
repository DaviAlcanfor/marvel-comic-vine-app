package com.projeto.marvel.ui.comics

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.projeto.marvel.R
import com.projeto.marvel.data.ReadComic
import com.projeto.marvel.data.ReadingStatus
import com.projeto.marvel.databinding.DialogComicBinding

/**
 * Coloca [item] na estante com um status (quero ler, lendo, já li); em "já li" também dá nota
 * (0 a 5, 0 = sem nota) e resenha. Se já estava na estante, oferece tirar de lá. Usado na busca
 * e na estante do Perfil.
 */
fun Fragment.showComicDialog(item: ComicItem, onSave: (ReadComic) -> Unit, onRemove: (Int) -> Unit) {
    val binding = DialogComicBinding.inflate(layoutInflater)
    val buttons = mapOf(
        ReadingStatus.WANT_TO_READ to binding.wantButton,
        ReadingStatus.READING to binding.readingButton,
        ReadingStatus.READ to binding.readButton
    )
    fun selected() = buttons.entries.first { it.value.id == binding.statusGroup.checkedButtonId }.key
    fun showReadFields() {
        val read = selected() == ReadingStatus.READ
        binding.ratingBar.visibility = if (read) View.VISIBLE else View.GONE
        binding.reviewInput.visibility = if (read) View.VISIBLE else View.GONE
    }

    binding.statusGroup.check(buttons.getValue(if (item.onShelf) item.comic.status else ReadingStatus.READ).id)
    binding.ratingBar.rating = item.comic.rating.toFloat()
    binding.reviewInput.setText(item.comic.review)
    binding.statusGroup.addOnButtonCheckedListener { _, _, _ -> showReadFields() }
    showReadFields()

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(item.comic.title)
        .setView(binding.root)
        .setPositiveButton(if (item.onShelf) R.string.comics_dialog_update else R.string.comics_dialog_save) { _, _ ->
            val status = selected()
            val read = status == ReadingStatus.READ
            onSave(
                item.comic.withStatus(status).copy(
                    rating = if (read) binding.ratingBar.rating.toInt() else 0,
                    review = binding.reviewInput.text.toString().trim().takeIf { read && it.isNotEmpty() }
                )
            )
        }
        .setNegativeButton(R.string.comics_dialog_cancel, null)
        .apply { if (item.onShelf) setNeutralButton(R.string.comics_dialog_remove) { _, _ -> onRemove(item.comic.id) } }
        .show()
}
