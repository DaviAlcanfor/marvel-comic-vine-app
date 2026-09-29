package com.projeto.marvel.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate
import kotlin.random.Random

/** Raridade da figurinha pela fama (aparições em HQs). */
enum class Rarity { COMMON, RARE, LEGENDARY }

/**
 * Tipos de pacote. [weights] = chance relativa de cada raridade; [guaranteed] = a 1ª figurinha é
 * no mínimo desta raridade. Básico: grátis todo dia. Prata: vitória. Ouro: trilha completa ou 3×3.
 */
@Suppress("MagicNumber") // os pesos são a própria tabela de sorteio
enum class PackType(val weights: Map<Rarity, Int>, val guaranteed: Rarity) {
    BASIC(mapOf(Rarity.COMMON to 60, Rarity.RARE to 30, Rarity.LEGENDARY to 10), Rarity.COMMON),
    SILVER(mapOf(Rarity.COMMON to 45, Rarity.RARE to 40, Rarity.LEGENDARY to 15), Rarity.RARE),
    GOLD(mapOf(Rarity.COMMON to 25, Rarity.RARE to 45, Rarity.LEGENDARY to 30), Rarity.LEGENDARY)
}

private const val RARE_APPEARANCES = 500
private const val LEGENDARY_APPEARANCES = 2_000
const val PACK_SIZE = 4

fun rarity(appearances: Int?): Rarity {
    val count = appearances ?: 0
    return when {
        count >= LEGENDARY_APPEARANCES -> Rarity.LEGENDARY
        count >= RARE_APPEARANCES -> Rarity.RARE
        else -> Rarity.COMMON
    }
}

/**
 * Pacote de [size] figurinhas do tipo [type]: a 1ª respeita a garantia (se o pool tiver alguém
 * dela para cima); cada uma sorteia a raridade pelo peso (só entre as que existem no pool) e
 * depois um personagem dela. Repetidas podem sair, como num álbum de verdade.
 */
fun <T> openPack(
    pool: List<T>,
    rarityOf: (T) -> Rarity,
    random: Random,
    type: PackType = PackType.BASIC,
    size: Int = PACK_SIZE
): List<T> {
    val byRarity = pool.groupBy(rarityOf)
    val available = Rarity.entries.filter { !byRarity[it].isNullOrEmpty() }
    if (available.isEmpty()) return emptyList()
    val premium = available.filter { it >= type.guaranteed }.ifEmpty { available }
    return List(size) { index ->
        val rarity = weighted(if (index == 0) premium else available, type, random)
        byRarity.getValue(rarity).random(random)
    }
}

private fun weighted(rarities: List<Rarity>, type: PackType, random: Random): Rarity {
    var roll = random.nextInt(rarities.sumOf { type.weights.getValue(it) })
    return rarities.first { rarity ->
        roll -= type.weights.getValue(rarity)
        roll < 0
    }
}

const val FREE_PACKS_PER_DAY = 2

/**
 * Básicos grátis que ainda dá para abrir hoje: [FREE_PACKS_PER_DAY] por dia; [usedOn] é o dia em
 * que [used] foram abertos (outro dia = contador zerado).
 */
fun freePacksLeft(usedOn: LocalDate?, used: Int, today: LocalDate) =
    if (usedOn == today) (FREE_PACKS_PER_DAY - used).coerceAtLeast(0) else FREE_PACKS_PER_DAY

/**
 * Álbum no aparelho: quantas de cada personagem (id → quantidade), o dia do último pacote grátis
 * e os pacotes ganhos ainda não abertos, por tipo.
 */
class StickerStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun counts(): Map<Int, Int> =
        prefs.getString(KEY_COUNTS, null)?.let { gson.fromJson<Map<Int, Int>>(it, MAP_TYPE) }.orEmpty()

    fun add(ids: List<Int>) {
        val counts = counts().toMutableMap()
        ids.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        prefs.edit { putString(KEY_COUNTS, gson.toJson(counts)) }
    }

    /** Gasta [amount] figurinhas de [id] (evoluir para dourada). */
    fun remove(id: Int, amount: Int) {
        val counts = counts().toMutableMap()
        counts[id] = ((counts[id] ?: 0) - amount).coerceAtLeast(0)
        prefs.edit { putString(KEY_COUNTS, gson.toJson(counts)) }
    }

    fun addPack(type: PackType) = prefs.edit { putInt(key(type), extra(type) + 1) }

    /** Quantos de cada tipo dá para abrir agora (o Básico conta os grátis do dia). */
    fun packs(today: LocalDate): Map<PackType, Int> = PackType.entries.associateWith { type ->
        extra(type) + if (type == PackType.BASIC) freeLeft(today) else 0
    }

    /** Gasta um pacote do tipo (no Básico, os grátis do dia primeiro). False se não havia nenhum. */
    fun usePack(type: PackType, today: LocalDate): Boolean {
        when {
            type == PackType.BASIC && freeLeft(today) > 0 -> prefs.edit {
                putInt(KEY_FREE_USED, if (lastFree() == today) prefs.getInt(KEY_FREE_USED, 0) + 1 else 1)
                putLong(KEY_LAST_FREE, today.toEpochDay())
            }
            extra(type) > 0 -> prefs.edit { putInt(key(type), extra(type) - 1) }
            else -> return false
        }
        return true
    }

    private fun extra(type: PackType) = prefs.getInt(key(type), 0)

    // Quem já abriu o grátis antes desta regra tem last_free sem contador: conta como 1 usado.
    private fun freeLeft(today: LocalDate) = freePacksLeft(lastFree(), prefs.getInt(KEY_FREE_USED, 1), today)

    // Antes dos tipos, os pacotes de vitória ficavam em "extra_packs": viram os Prata.
    private fun key(type: PackType) = if (type == PackType.SILVER) KEY_LEGACY_EXTRA else "extra_${type.name}"

    private fun lastFree() = prefs.getLong(KEY_LAST_FREE, NEVER).takeIf { it != NEVER }?.let(LocalDate::ofEpochDay)

    private companion object {
        const val PREFS_NAME = "stickers"
        const val KEY_COUNTS = "counts"
        const val KEY_LEGACY_EXTRA = "extra_packs"
        const val KEY_LAST_FREE = "last_free"
        const val KEY_FREE_USED = "free_used"
        const val NEVER = Long.MIN_VALUE
        val MAP_TYPE = object : TypeToken<Map<Int, Int>>() {}.type
    }
}
