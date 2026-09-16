package com.projeto.marvel.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.ItemCharacterBinding

class CharacterAdapter(
    private val onClick: (CharacterSummary) -> Unit
) : ListAdapter<CharacterSummary, CharacterAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(character: CharacterSummary) {
            binding.name.text = character.name
            binding.subtitle.text = character.realName ?: character.deck.orEmpty()
            binding.thumbnail.load(character.image?.mediumUrl)
            binding.root.setOnClickListener { onClick(character) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CharacterSummary>() {
        override fun areItemsTheSame(oldItem: CharacterSummary, newItem: CharacterSummary) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CharacterSummary, newItem: CharacterSummary) = oldItem == newItem
    }
}
