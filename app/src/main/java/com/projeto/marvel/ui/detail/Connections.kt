package com.projeto.marvel.ui.detail

import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.projeto.marvel.R
import com.projeto.marvel.data.ComicVineRepository
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.ui.comicDialog
import com.projeto.marvel.ui.info.InfoDetailFragment
import kotlinx.coroutines.launch

private const val MAX_TEAMS = 6
private const val MAX_ALLIES = 5
private const val MAX_CREATORS = 3
private const val HEIGHT_SHARE = 0.65f

// Prefixos de tipo dos ids da Comic Vine: 4005 personagem, 4060 time.
private fun characterUrl(id: Int?) = id?.let { "https://comicvine.gamespot.com/api/character/4005-$it/" }
private fun teamUrl(id: Int?) = id?.let { "https://comicvine.gamespot.com/api/team/4060-$it/" }

/** Nós da teia: o personagem e as ligações reais dele na Comic Vine (as listas vêm longas: corta). */
fun CharacterSummary.connections(): List<GraphNode> = buildList {
    add(GraphNode(name.orEmpty(), Link.HERO, null))
    teams.orEmpty().take(MAX_TEAMS).forEach { add(GraphNode(it.name.orEmpty(), Link.TEAM, teamUrl(it.id))) }
    friends.orEmpty().take(MAX_ALLIES).forEach { add(GraphNode(it.name.orEmpty(), Link.FRIEND, characterUrl(it.id))) }
    enemies.orEmpty().take(MAX_ALLIES).forEach { add(GraphNode(it.name.orEmpty(), Link.ENEMY, characterUrl(it.id))) }
    creators.orEmpty().take(MAX_CREATORS).forEach { add(GraphNode(it.name.orEmpty(), Link.CREATOR, it.apiDetailUrl)) }
}

/** Botão "Ver conexões" do Detalhe: busca as ligações (aliados/inimigos só vêm no detalhe completo). */
fun Fragment.showConnections(apiDetailUrl: String, button: Button) {
    button.isEnabled = false
    viewLifecycleOwner.lifecycleScope.launch {
        val result = ComicVineRepository().getCharacterDetail(apiDetailUrl, full = true)
        button.isEnabled = true
        result.onSuccess { openGraph(it.connections()) }
            .onFailure { Toast.makeText(requireContext(), it.message, Toast.LENGTH_LONG).show() }
    }
}

private fun Fragment.openGraph(nodes: List<GraphNode>) {
    var dialog: AlertDialog? = null
    val view = ConnectionsView(requireContext(), nodes) { node ->
        val url = node.url ?: return@ConnectionsView
        dialog?.dismiss()
        val (destination, args) = when (node.link) {
            Link.TEAM -> R.id.teamDetailFragment to bundleOf("apiDetailUrl" to url, "teamName" to node.label)
            Link.CREATOR ->
                R.id.infoDetailFragment to InfoDetailFragment.args(InfoDetailFragment.KIND_CREATOR, url, node.label)
            else -> R.id.characterDetailFragment to bundleOf("apiDetailUrl" to url, "characterName" to node.label)
        }
        findNavController().navigate(destination, args as Bundle)
    }
    view.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        (resources.displayMetrics.heightPixels * HEIGHT_SHARE).toInt()
    )
    dialog = requireContext().comicDialog()
        .setTitle(R.string.connections_title)
        .setMessage(R.string.connections_legend)
        .setView(view)
        .setPositiveButton(R.string.marathon_close, null)
        .show()
}
