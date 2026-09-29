package com.projeto.marvel.ui.profile

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.projeto.marvel.R
import com.projeto.marvel.data.Achievement
import com.projeto.marvel.data.Progress
import com.projeto.marvel.databinding.ItemAchievementBinding

/** Medalha com o progresso atual. */
data class Medal(val achievement: Achievement, val progress: Progress)

/** Grade de conquistas: colorida se desbloqueada, cinza se não; toque mostra como ganhar. */
class AchievementAdapter : ListAdapter<Medal, AchievementAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(private val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(medal: Medal) {
            val context = binding.root.context
            val achievement = medal.achievement
            val unlocked = achievement.unlocked(medal.progress)
            val fill = ContextCompat.getColor(context, if (unlocked) achievement.color else R.color.surface_variant)
            val icon = ContextCompat.getColor(context, if (unlocked) R.color.ink else R.color.text_secondary)
            binding.medal.setImageResource(achievement.icon)
            binding.medal.backgroundTintList = ColorStateList.valueOf(fill)
            binding.medal.imageTintList = ColorStateList.valueOf(icon)
            binding.medal.alpha = if (unlocked) 1f else LOCKED_ALPHA
            binding.name.setText(achievement.title)
            binding.progress.text = context.getString(
                R.string.achievement_progress,
                achievement.current(medal.progress),
                achievement.goal
            )
            binding.root.contentDescription = context.getString(achievement.title) + ". " +
                context.getString(achievement.description)
            binding.root.setOnClickListener {
                Toast.makeText(context, achievement.description, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Medal>() {
        override fun areItemsTheSame(oldItem: Medal, newItem: Medal) = oldItem.achievement == newItem.achievement
        override fun areContentsTheSame(oldItem: Medal, newItem: Medal) = oldItem == newItem
    }

    private companion object {
        const val LOCKED_ALPHA = 0.6f
    }
}
