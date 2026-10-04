package com.projeto.marvel.ui.movies

import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.projeto.marvel.R
import com.projeto.marvel.data.AuthRepository
import com.projeto.marvel.data.MCU_MARATHON
import com.projeto.marvel.data.MarathonMovie
import com.projeto.marvel.data.RatedMovie
import com.projeto.marvel.data.ReadingStore
import com.projeto.marvel.data.marathonProgress
import com.projeto.marvel.data.remote.Movie
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.info.InfoDetailFragment

private const val MINUTES_PER_HOUR = 60

/**
 * Maratona do MCU na aba Filmes: [summary] mostra quantos você viu, as horas que faltam e o próximo;
 * tocar abre a lista na ordem da história para marcar. Os vistos ficam na estante de filmes do Perfil.
 */
fun Fragment.bindMarathon(summary: TextView, loaded: () -> List<Movie>) {
    val store = ReadingStore(requireContext(), AuthRepository().currentUser?.uid)
    fun refresh() {
        val progress = marathonProgress(store.movies().filter { it.watched }.map { it.id }.toSet())
        summary.text = progress.next?.let { next ->
            getString(
                R.string.marathon_summary,
                progress.watched,
                progress.total,
                progress.minutesLeft / MINUTES_PER_HOUR,
                next.title
            )
        } ?: getString(R.string.marathon_done, progress.total)
    }
    refresh()
    summary.setOnClickListener {
        val watched = store.movies().filter { it.watched }.map { it.id }.toSet()
        val titles = MCU_MARATHON.mapIndexed { index, movie -> "${index + 1}. ${movie.title}" }.toTypedArray()
        val checked = MCU_MARATHON.map { it.movieId in watched }.toBooleanArray()
        requireContext().comicDialog()
            .setTitle(R.string.marathon_title)
            .setMultiChoiceItems(titles, checked) { _, index, isChecked ->
                mark(store, MCU_MARATHON[index], isChecked, loaded())
                refresh()
            }
            .setPositiveButton(R.string.marathon_close, null)
            .setNeutralButton(R.string.marathon_open_next) { _, _ ->
                marathonProgress(store.movies().filter { it.watched }.map { it.id }.toSet()).next
                    ?.let { openMovie(it, loaded()) }
            }
            .show()
    }
}

/** Desmarcar não apaga nota/resenha: o filme só volta para "quero ver". */
private fun mark(store: ReadingStore, movie: MarathonMovie, watched: Boolean, loaded: List<Movie>) {
    val existing = store.movies().firstOrNull { it.id == movie.movieId }
    val card = loaded.firstOrNull { it.id == movie.movieId }
    store.saveMovie(
        existing?.copy(watched = watched) ?: RatedMovie(
            id = movie.movieId,
            title = card?.name ?: movie.title,
            posterUrl = card?.image?.mediumUrl,
            apiDetailUrl = card?.apiDetailUrl ?: detailUrl(movie.movieId),
            watched = watched
        )
    )
}

private fun Fragment.openMovie(movie: MarathonMovie, loaded: List<Movie>) {
    val url = loaded.firstOrNull { it.id == movie.movieId }?.apiDetailUrl ?: detailUrl(movie.movieId)
    findNavController().navigate(
        R.id.infoDetailFragment,
        InfoDetailFragment.args(InfoDetailFragment.KIND_MOVIE, url, movie.title)
    )
}

// 4025 é o prefixo de tipo "movie" nos ids da Comic Vine.
private fun detailUrl(id: Int) = "https://comicvine.gamespot.com/api/movie/4025-$id/"
