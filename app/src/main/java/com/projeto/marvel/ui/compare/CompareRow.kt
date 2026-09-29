package com.projeto.marvel.ui.compare

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.projeto.marvel.R
import com.projeto.marvel.data.Fighter
import com.projeto.marvel.data.Stat
import com.projeto.marvel.databinding.ItemCompareStatBinding
import com.projeto.marvel.ui.battle.maxHp

private const val STAT_MAX = 99

private val STATS = listOf(
    Stat.ATTACK to R.string.stat_attack,
    Stat.DEFENSE to R.string.stat_defense,
    Stat.SPEED to R.string.stat_speed,
    Stat.INTELLIGENCE to R.string.stat_intelligence
)

/** Linha da comparação: [max] é a escala das duas barras (nula = a do maior dos dois). */
class CompareRow(@StringRes val label: Int, val left: Int, val right: Int, val max: Int? = STAT_MAX)

/** Vida e atributos da Batalha de dois lutadores, na mesma ordem da ficha. */
fun fighterRows(left: Fighter, right: Fighter) =
    listOf(CompareRow(R.string.compare_hp, left.maxHp(), right.maxHp(), max = null)) +
        STATS.map { (stat, label) -> CompareRow(label, left.stats.getValue(stat), right.stats.getValue(stat)) }

/** Uma linha por [rows] em [container] (Comparar e o VS antes da luta). */
fun bindComparison(container: LinearLayout, rows: List<CompareRow>) {
    container.removeAllViews()
    val inflater = LayoutInflater.from(container.context)
    rows.forEach { ItemCompareStatBinding.inflate(inflater, container, true).bind(it) }
}

/** O maior lado fica colorido; o menor, apagado. Empate: os dois coloridos. */
private fun ItemCompareStatBinding.bind(row: CompareRow) {
    val max = row.max ?: maxOf(row.left, row.right, 1)
    val win = ContextCompat.getColor(root.context, R.color.accent)
    val lose = ContextCompat.getColor(root.context, R.color.text_secondary)
    label.setText(row.label)
    val sides = listOf(Triple(leftBar, leftValue, row.left), Triple(rightBar, rightValue, row.right))
    sides.forEach { (bar, text, value) ->
        val color = if (value >= maxOf(row.left, row.right)) win else lose
        bar.max = max
        bar.setProgressCompat(value, true)
        bar.setIndicatorColor(color)
        text.text = value.toString()
        text.setTextColor(color)
    }
}
