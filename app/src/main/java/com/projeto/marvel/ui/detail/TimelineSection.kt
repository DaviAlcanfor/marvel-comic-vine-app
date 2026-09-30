package com.projeto.marvel.ui.detail

import android.content.res.ColorStateList
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.TimelineEvent
import com.projeto.marvel.data.TimelineKind
import com.projeto.marvel.databinding.FragmentCharacterDetailBinding
import com.projeto.marvel.databinding.ItemTimelineBinding
import com.projeto.marvel.ui.fadeVisible

private const val YEAR_LENGTH = 4

/** Seção "Linha do tempo" do Detalhe: só redesenha quando a lista muda (render roda várias vezes). */
fun FragmentCharacterDetailBinding.bindTimeline(events: List<TimelineEvent>) {
    listOf(timelineTitle, timelineList).forEach { it.fadeVisible(events.isNotEmpty()) }
    if (timelineList.tag == events) return
    timelineList.tag = events
    timelineList.removeAllViews()
    val context = root.context
    val inflater = LayoutInflater.from(context)
    events.forEach { event ->
        ItemTimelineBinding.inflate(inflater, timelineList, true).apply {
            val (label, color) = when (event.kind) {
                TimelineKind.DEBUT -> R.string.timeline_debut to R.color.accent_text
                TimelineKind.TEAM -> R.string.timeline_team to R.color.move_water
                TimelineKind.DEATH -> R.string.timeline_death to R.color.primary
            }
            val tint = ContextCompat.getColor(context, color)
            year.text = event.date.take(YEAR_LENGTH)
            dot.backgroundTintList = ColorStateList.valueOf(tint)
            kind.setText(label)
            kind.setTextColor(tint)
            title.text = event.title
            issue.text = event.issue
            cover.load(event.imageUrl) { crossfade(true) }
        }
    }
}
