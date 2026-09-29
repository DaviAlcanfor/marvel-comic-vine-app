package com.projeto.marvel.ui.creators

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.data.remote.Person
import com.projeto.marvel.databinding.ItemCharacterBinding

/** Mesmo card da lista de personagens: foto, nome e resumo. */
class CreatorAdapter(
    /** Largura fixa para carrosséis (Detalhe do personagem); null = ocupa a coluna da grade. */
    private val cardWidth: Int? = null,
    private val onClick: (Person) -> Unit
) : ListAdapter<Person, CreatorAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        cardWidth?.let { width -> binding.root.updateLayoutParams { this.width = width } }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(person: Person) {
            binding.name.text = person.name
            binding.subtitle.text = person.deck.orEmpty()
            binding.thumbnail.load(person.image?.mediumUrl) { crossfade(true) }
            binding.root.setOnClickListener { onClick(person) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Person>() {
        override fun areItemsTheSame(oldItem: Person, newItem: Person) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Person, newItem: Person) = oldItem == newItem
    }
}
