package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Progressão dos lutadores pelo álbum: só luta quem você tem a figurinha; cada repetida sobe um
// nível, e cada nível dá pontos para distribuir nos atributos. Carta dourada: variante mais forte
// (sorte no pacote ou evoluindo repetidas). Regras puras testadas em ProgressionTest.

const val MAX_LEVEL = 10
const val POINTS_PER_LEVEL = 8
const val EVOLVE_COST = 5
private const val GOLDEN_BONUS = 5
private const val STAT_MAX = 99

/** Atributos que recebem pontos (FAMA não: vem das HQs). */
val UPGRADABLE = listOf(Stat.ATTACK, Stat.DEFENSE, Stat.SPEED, Stat.INTELLIGENCE)

/** Nível pela quantidade de figurinhas: 0 = bloqueado, ×1 = Nv 1, ×2 = Nv 2… até [MAX_LEVEL]. */
fun levelFor(count: Int) = count.coerceIn(0, MAX_LEVEL)

/** Pontos que o nível [level] dá para distribuir. */
fun pointsFor(level: Int) = (level.coerceIn(1, MAX_LEVEL) - 1) * POINTS_PER_LEVEL

/**
 * Lutador no nível [level], com os pontos distribuídos em [allocation]; o que sobrar sem
 * distribuir entra dividido por igual (quem não mexe não fica para trás). Distribuição que passa
 * dos pontos do nível (o nível caiu ao evoluir) é descartada. Dourada: +[GOLDEN_BONUS] em tudo.
 */
fun Fighter.upgraded(level: Int, allocation: Map<Stat, Int> = emptyMap(), golden: Boolean = false): Fighter {
    val points = pointsFor(level)
    val spent = allocation.values.sum()
    val valid = if (spent <= points) allocation else emptyMap()
    val free = points - valid.values.sum()
    val share = free / UPGRADABLE.size
    val remainder = free % UPGRADABLE.size
    val boosted = stats.mapValues { (stat, value) ->
        val index = UPGRADABLE.indexOf(stat)
        if (index < 0) {
            value
        } else {
            val extra = (valid[stat] ?: 0) + share + (if (index < remainder) 1 else 0) +
                (if (golden) GOLDEN_BONUS else 0)
            (value + extra).coerceAtMost(STAT_MAX)
        }
    }
    return copy(stats = boosted, level = level.coerceIn(1, MAX_LEVEL), golden = golden)
}

/** Só o nível, pontos divididos por igual (CPU e testes). */
fun Fighter.leveled(level: Int) = upgraded(level)

private const val RIVAL_LEVEL_GAP = 2

/**
 * Nível da CPU: acompanha o seu time [RIVAL_LEVEL_GAP] níveis abaixo (upar dá vantagem, mas a
 * luta continua disputada); o chefe da trilha vem no seu nível.
 */
fun rivalLevel(team: List<Fighter>, boss: Boolean): Int {
    val yours = team.map { it.level }.average().takeIf { !it.isNaN() }?.toInt() ?: 1
    return (if (boss) yours else yours - RIVAL_LEVEL_GAP).coerceIn(1, MAX_LEVEL)
}

/** Chance (%) de cada figurinha do pacote vir dourada. */
@Suppress("MagicNumber") // a tabela de sorte é a própria regra
fun goldenChance(type: PackType) = when (type) {
    PackType.BASIC -> 3
    PackType.SILVER -> 6
    PackType.GOLD -> 12
}

/** Dá para evoluir: tem [EVOLVE_COST] figurinhas e ainda não é dourada. */
fun canEvolve(count: Int, golden: Boolean) = !golden && count >= EVOLVE_COST

/**
 * Melhorias de cada personagem no aparelho: pontos distribuídos (id → atributo → pontos) e quais
 * já são douradas.
 */
class UpgradeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun allocations(): Map<Int, Map<Stat, Int>> =
        prefs.getString(KEY_ALLOCATIONS, null)?.let { gson.fromJson<Map<Int, Map<Stat, Int>>>(it, MAP_TYPE) }
            .orEmpty()

    fun allocation(id: Int): Map<Stat, Int> = allocations()[id].orEmpty()

    fun setAllocation(id: Int, allocation: Map<Stat, Int>) {
        val all = allocations().toMutableMap()
        all[id] = allocation.filterValues { it > 0 }
        prefs.edit { putString(KEY_ALLOCATIONS, gson.toJson(all)) }
    }

    fun goldenIds(): Set<Int> = prefs.getStringSet(KEY_GOLDEN, null).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun isGolden(id: Int) = id in goldenIds()

    fun markGolden(id: Int) = prefs.edit { putStringSet(KEY_GOLDEN, goldenIds().map { it.toString() }.toSet() + "$id") }

    private companion object {
        const val PREFS_NAME = "upgrades"
        const val KEY_ALLOCATIONS = "allocations"
        const val KEY_GOLDEN = "golden"
        val MAP_TYPE = object : TypeToken<Map<Int, Map<Stat, Int>>>() {}.type
    }
}
