package com.projeto.marvel.ui.battle

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.projeto.marvel.R
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Stat
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.databinding.DialogFighterPreviewBinding
import com.projeto.marvel.databinding.ItemStatBarBinding
import com.projeto.marvel.ui.album.badged
import com.projeto.marvel.ui.color
import com.projeto.marvel.ui.icon
import com.projeto.marvel.ui.label
import kotlinx.coroutines.launch

/** Atributo, rótulo e a cor da barra (a do golpe que mais combina com ele). */
private val SHOWN_STATS = listOf(
    Triple(Stat.ATTACK, R.string.stat_attack, R.color.move_strike),
    Triple(Stat.DEFENSE, R.string.stat_defense, R.color.move_guard),
    Triple(Stat.SPEED, R.string.stat_speed, R.color.move_dodge),
    Triple(Stat.INTELLIGENCE, R.string.stat_intelligence, R.color.move_freeze)
)

/**
 * Ficha do lutador antes de escolher: vida, atributos (já com o bônus do [level]) e golpes.
 * [load] busca os atributos (vêm
 * dos poderes, no detalhe do personagem); "Escolher" só habilita quando a ficha carregou.
 */
fun Fragment.showFighterPreview(
    character: CharacterSummary,
    level: Int,
    upgrade: (Fighter) -> Fighter,
    load: suspend () -> Result<Fighter>,
    onChoose: () -> Unit
) {
    val binding = DialogFighterPreviewBinding.inflate(layoutInflater)
    val dialog = BottomSheetDialog(requireContext())
    dialog.setContentView(binding.root)
    // O painel de HQ é o fundo; a folha arredondada do Material por trás apareceria nos cantos.
    dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = null
    binding.name.text = if (level > 1) getString(R.string.battle_level_name, character.name, level) else character.name
    binding.image.load(character.image?.mediumUrl) { crossfade(true) }
    binding.chooseButton.setOnClickListener {
        dialog.dismiss()
        onChoose()
    }
    dialog.show()

    viewLifecycleOwner.lifecycleScope.launch {
        load().fold(
            // Com o nível do álbum: a ficha mostra os atributos que vão para a luta.
            onSuccess = { fighter -> binding.bindFighter(upgrade(fighter)) },
            onFailure = {
                binding.errorText.text = getString(R.string.home_error_retry) + "\n" + it.message
                binding.errorText.visibility = View.VISIBLE
            }
        )
        binding.progressBar.visibility = View.GONE
    }
}

private fun DialogFighterPreviewBinding.bindFighter(fighter: Fighter) {
    val context = root.context
    name.text = fighter.badged(context, name.text.toString())
    hp.text = context.getString(R.string.battle_preview_hp, fighter.maxHp())
    stats.removeAllViews()
    SHOWN_STATS.forEach { (stat, labelRes, colorRes) ->
        val value = fighter.stats.getValue(stat)
        ItemStatBarBinding.inflate(LayoutInflater.from(context), stats, true).apply {
            label.setText(labelRes)
            bar.setIndicatorColor(ContextCompat.getColor(context, colorRes))
            bar.progress = value
            this.value.text = value.toString()
        }
    }
    moves.removeAllViews()
    fighter.moves.forEach { move ->
        moves.addView(
            Chip(context).apply {
                text = context.getString(R.string.battle_move_chip, move.name, context.getString(move.type.label))
                setChipIconResource(move.type.icon)
                chipIconTint = ColorStateList.valueOf(ContextCompat.getColor(context, move.type.color))
                isClickable = false
            }
        )
    }
    chooseButton.isEnabled = true
}
