package com.projeto.marvel.ui.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.projeto.marvel.R
import com.projeto.marvel.databinding.ItemChatMessageBinding

/** Balões de HQ: o personagem fala à esquerda (balão branco), o fã à direita (dourado). */
class ChatMessageAdapter : ListAdapter<ChatMessage, ChatMessageAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            val context = binding.root.context
            binding.text.text = message.text
            binding.text.setBackgroundResource(
                when {
                    message.error -> R.drawable.bg_balloon_error
                    message.fromUser -> R.drawable.bg_balloon_user
                    else -> R.drawable.bg_balloon_character
                }
            )
            binding.text.setTextColor(
                ContextCompat.getColor(context, if (message.error) R.color.text_primary else R.color.ink)
            )
            binding.text.updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = if (message.fromUser) Gravity.END else Gravity.START
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        // Mensagens não mudam depois de enviadas: a identidade é o próprio conteúdo.
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem === newItem
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }
}
