package com.projeto.marvel.ui.teams

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.Team
import com.projeto.marvel.databinding.ItemTeamBinding

class TeamAdapter : ListAdapter<Team, TeamAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTeamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTeamBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(team: Team) {
            binding.name.text = team.name
            binding.memberCount.text = team.memberCount?.let {
                binding.root.context.getString(R.string.teams_member_count, it)
            }.orEmpty()
            binding.thumbnail.load(team.image?.mediumUrl) { crossfade(true) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Team>() {
        override fun areItemsTheSame(oldItem: Team, newItem: Team) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Team, newItem: Team) = oldItem == newItem
    }
}
