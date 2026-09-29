package com.projeto.marvel.ui.profile

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.projeto.marvel.R
import com.projeto.marvel.data.Achievement

// Nome, descrição, ícone e cor de cada conquista (a regra fica em data/Achievements.kt).

@get:StringRes
val Achievement.title
    get() = when (this) {
        Achievement.FIRST_WIN -> R.string.achievement_first_win
        Achievement.VETERAN -> R.string.achievement_veteran
        Achievement.PATHFINDER -> R.string.achievement_pathfinder
        Achievement.AVENGER -> R.string.achievement_avenger
        Achievement.MAX_POWER -> R.string.achievement_max_power
        Achievement.BOOKWORM -> R.string.achievement_bookworm
        Achievement.CRITIC -> R.string.achievement_critic
        Achievement.CINEPHILE -> R.string.achievement_cinephile
        Achievement.SELF_KNOWLEDGE -> R.string.achievement_self_knowledge
        Achievement.FANBOY -> R.string.achievement_fanboy
        Achievement.DETECTIVE -> R.string.achievement_detective
        Achievement.COLLECTOR -> R.string.achievement_collector
    }

@get:StringRes
val Achievement.description
    get() = when (this) {
        Achievement.FIRST_WIN -> R.string.achievement_first_win_desc
        Achievement.VETERAN -> R.string.achievement_veteran_desc
        Achievement.PATHFINDER -> R.string.achievement_pathfinder_desc
        Achievement.AVENGER -> R.string.achievement_avenger_desc
        Achievement.MAX_POWER -> R.string.achievement_max_power_desc
        Achievement.BOOKWORM -> R.string.achievement_bookworm_desc
        Achievement.CRITIC -> R.string.achievement_critic_desc
        Achievement.CINEPHILE -> R.string.achievement_cinephile_desc
        Achievement.SELF_KNOWLEDGE -> R.string.achievement_self_knowledge_desc
        Achievement.FANBOY -> R.string.achievement_fanboy_desc
        Achievement.DETECTIVE -> R.string.achievement_detective_desc
        Achievement.COLLECTOR -> R.string.achievement_collector_desc
    }

@get:DrawableRes
val Achievement.icon
    get() = when (this) {
        Achievement.FIRST_WIN, Achievement.VETERAN -> R.drawable.ic_swords
        Achievement.PATHFINDER -> R.drawable.ic_explore
        Achievement.AVENGER -> R.drawable.ic_group
        Achievement.MAX_POWER -> R.drawable.ic_move_magic
        Achievement.BOOKWORM, Achievement.CRITIC -> R.drawable.ic_move_strike
        Achievement.CINEPHILE -> R.drawable.ic_mask
        Achievement.SELF_KNOWLEDGE -> R.drawable.ic_person
        Achievement.FANBOY -> R.drawable.ic_send
        Achievement.DETECTIVE -> R.drawable.ic_search
        Achievement.COLLECTOR -> R.drawable.ic_bolt
    }

@get:ColorRes
val Achievement.color
    get() = when (this) {
        Achievement.FIRST_WIN -> R.color.move_heal
        Achievement.VETERAN -> R.color.move_strike
        Achievement.PATHFINDER -> R.color.move_dodge
        Achievement.AVENGER -> R.color.move_water
        Achievement.MAX_POWER -> R.color.move_magic
        Achievement.BOOKWORM -> R.color.move_blast
        Achievement.CRITIC -> R.color.move_poison
        Achievement.CINEPHILE -> R.color.move_freeze
        Achievement.SELF_KNOWLEDGE -> R.color.move_drain
        Achievement.FANBOY -> R.color.accent
        Achievement.DETECTIVE -> R.color.primary
        Achievement.COLLECTOR -> R.color.move_water
    }
