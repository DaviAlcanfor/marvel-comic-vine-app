package com.projeto.marvel.ui

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.projeto.marvel.R
import com.projeto.marvel.data.MoveType

// Identidade visual de cada tipo de golpe, usada na Batalha e nas tags de poder do Detalhe.

@get:DrawableRes
val MoveType.icon
    get() = when (this) {
        MoveType.STRIKE -> R.drawable.ic_move_strike
        MoveType.BLAST -> R.drawable.ic_bolt
        MoveType.POISON -> R.drawable.ic_move_poison
        MoveType.HEAL -> R.drawable.ic_move_heal
        MoveType.GUARD -> R.drawable.ic_move_guard
        MoveType.DODGE -> R.drawable.ic_move_dodge
        MoveType.FREEZE -> R.drawable.ic_move_freeze
        MoveType.WATER -> R.drawable.ic_move_water
        MoveType.MAGIC -> R.drawable.ic_move_magic
        MoveType.DRAIN -> R.drawable.ic_move_drain
    }

@get:ColorRes
val MoveType.color
    get() = when (this) {
        MoveType.STRIKE -> R.color.move_strike
        MoveType.BLAST -> R.color.move_blast
        MoveType.POISON -> R.color.move_poison
        MoveType.HEAL -> R.color.move_heal
        MoveType.GUARD -> R.color.move_guard
        MoveType.DODGE -> R.color.move_dodge
        MoveType.FREEZE -> R.color.move_freeze
        MoveType.WATER -> R.color.move_water
        MoveType.MAGIC -> R.color.move_magic
        MoveType.DRAIN -> R.color.move_drain
    }

@get:StringRes
val MoveType.label
    get() = when (this) {
        MoveType.STRIKE -> R.string.move_type_strike
        MoveType.BLAST -> R.string.move_type_blast
        MoveType.POISON -> R.string.move_type_poison
        MoveType.HEAL -> R.string.move_type_heal
        MoveType.GUARD -> R.string.move_type_guard
        MoveType.DODGE -> R.string.move_type_dodge
        MoveType.FREEZE -> R.string.move_type_freeze
        MoveType.WATER -> R.string.move_type_water
        MoveType.MAGIC -> R.string.move_type_magic
        MoveType.DRAIN -> R.string.move_type_drain
    }
