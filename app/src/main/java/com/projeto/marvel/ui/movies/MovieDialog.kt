package com.projeto.marvel.ui.movies

import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.projeto.marvel.R
import com.projeto.marvel.data.RatedMovie
import com.projeto.marvel.databinding.DialogComicBinding

/**
 * Avaliar filme (estante de filmes): "quero ver" ou "já vi"; em "já vi", nota e resenha. Mesmo
 * layout do diálogo de HQ, sem o "lendo". [current] null = ainda não está na estante.
 */
fun Fragment.showMovieDialog(
    base: RatedMovie,
    current: RatedMovie?,
    onSave: (RatedMovie) -> Unit,
    onRemove: (Int) -> Unit
) {
    val binding = DialogComicBinding.inflate(layoutInflater)
    binding.wantButton.setText(R.string.movie_status_want)
    binding.readButton.setText(R.string.movie_status_watched)
    binding.readingButton.visibility = View.GONE
    fun watched() = binding.statusGroup.checkedButtonId == binding.readButton.id
    fun showWatchedFields() {
        binding.ratingBar.visibility = if (watched()) View.VISIBLE else View.GONE
        binding.reviewInput.visibility = if (watched()) View.VISIBLE else View.GONE
    }
    binding.statusGroup.check(if (current?.watched == false) binding.wantButton.id else binding.readButton.id)
    binding.ratingBar.rating = (current?.rating ?: 0).toFloat()
    binding.reviewInput.setText(current?.review)
    binding.statusGroup.addOnButtonCheckedListener { _, _, _ -> showWatchedFields() }
    showWatchedFields()

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(base.title)
        .setView(binding.root)
        .setPositiveButton(if (current != null) R.string.comics_dialog_update else R.string.movie_dialog_save) { _, _ ->
            val seen = watched()
            onSave(
                base.copy(
                    watched = seen,
                    rating = if (seen) binding.ratingBar.rating.toInt() else 0,
                    review = binding.reviewInput.text.toString().trim().takeIf { seen && it.isNotEmpty() }
                )
            )
        }
        .setNegativeButton(R.string.comics_dialog_cancel, null)
        .apply { if (current != null) setNeutralButton(R.string.movie_dialog_remove) { _, _ -> onRemove(base.id) } }
        .show()
}
