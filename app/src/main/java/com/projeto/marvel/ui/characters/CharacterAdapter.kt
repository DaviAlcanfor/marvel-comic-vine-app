package com.projeto.marvel.ui.characters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.ItemCharacterBinding

class CharacterAdapter(
    /** Largura fixa do card para listas horizontais (Detalhe do Time); null = ocupa a coluna. */
    private val cardWidth: Int? = null,
    /** Recebe também a View do card: origem da transição de container até o Detalhe. */
    private val onClick: (CharacterSummary, View) -> Unit
) : ListAdapter<CharacterSummary, CharacterAdapter.ViewHolder>(Diff) {

    /** Escolhidos (trio do 3×3), na ordem: card amarelo com o número da escolha no nome. */
    var selected: List<Int> = emptyList()
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    /**
     * Nível de cada personagem pelo álbum (Batalha): 0 = bloqueado (card apagado com cadeado);
     * null = sem progressão (Descobrir, Detalhe…).
     */
    var levels: Map<Int, Int>? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCharacterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        cardWidth?.let { width -> binding.root.updateLayoutParams { this.width = width } }
        if (selectable == 0) {
            val attrs = parent.context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            selectable = attrs.getResourceId(0, 0)
            attrs.recycle()
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCharacterBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(character: CharacterSummary) {
            val order = selected.indexOf(character.id)
            binding.name.text = if (order >= 0) "${order + 1} · ${character.name}" else character.name
            binding.root.foreground = if (order >= 0) {
                ContextCompat.getDrawable(binding.root.context, R.drawable.fg_selected)
            } else {
                ContextCompat.getDrawable(binding.root.context, selectable)
            }
            val context = binding.root.context
            val level = levels?.let { it[character.id] ?: 0 }
            binding.root.alpha = if (level == 0) LOCKED_ALPHA else 1f
            binding.subtitle.text = when (level) {
                null -> character.realName ?: character.deck.orEmpty()
                0 -> context.getString(R.string.battle_locked)
                else -> context.getString(R.string.battle_level, level)
            }
            binding.thumbnail.load(character.image?.mediumUrl) { crossfade(true) }
            binding.root.transitionName = "character_${character.id}"
            binding.root.setOnClickListener { onClick(character, binding.root) }
        }
    }

    // Foreground padrão do card (ripple), para voltar ao normal quando sai da seleção.
    private var selectable = 0

    private companion object {
        const val LOCKED_ALPHA = 0.4f
    }

    private object Diff : DiffUtil.ItemCallback<CharacterSummary>() {
        override fun areItemsTheSame(oldItem: CharacterSummary, newItem: CharacterSummary) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CharacterSummary, newItem: CharacterSummary) = oldItem == newItem
    }
}
