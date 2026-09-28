package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.Team
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.log10
import kotlin.random.Random

/**
 * Única porta de entrada para dados da Comic Vine. A ViewModel fala com ela, nunca com o Retrofit.
 */
class ComicVineRepository(
    private val service: ComicVineService = ApiClient.comicVine
) {

    /** [query] nula/vazia lista as issues mais recentes. */
    suspend fun searchIssues(query: String? = null, offset: Int = 0): Result<List<Issue>> =
        runCatching {
            val response = service.getIssues(
                filter = query?.takeIf { it.isNotBlank() }?.let { "name:$it" },
                offset = offset
            )
            // A Comic Vine responde 200 mesmo em erro de negócio; o status vem no corpo.
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }

    /**
     * [query] nula/vazia lista os personagens mais recentes.
     *
     * TODO: a Comic Vine não tem filtro nativo de `publisher` neste endpoint (ele existe
     * para outros recursos, mas exigiria descobrir o id numérico da editora Marvel e não
     * está documentado de forma estável). Por isso a lista pode trazer personagens de
     * outras editoras. Quando esse filtro for definido, aplicar aqui.
     */
    suspend fun searchCharacters(query: String? = null, offset: Int = 0): Result<List<CharacterSummary>> =
        runCatching {
            val response = service.getCharacters(
                filter = query?.takeIf { it.isNotBlank() }?.let { "name:$it" },
                offset = offset
            )
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }

    suspend fun getCharacterDetail(apiDetailUrl: String): Result<CharacterSummary> =
        runCatching {
            val response = service.getCharacterDetail(apiDetailUrl)
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.result ?: error("Personagem não encontrado")
        }

    /** TODO: mesma limitação de filtro por editora do [searchCharacters] se aplica aqui. */
    suspend fun listTeams(offset: Int = 0): Result<List<Team>> =
        runCatching {
            val response = service.getTeams(offset = offset)
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }

    /**
     * Carrega os dois lutadores em paralelo. Sem [opponentUrl], sorteia o adversário entre os
     * personagens mais populares (ordenados por aparições, para não cair em personagens
     * obscuros sem imagem nem poderes). Os poderes só vêm no endpoint de detalhe.
     */
    suspend fun getFighters(playerUrl: String, opponentUrl: String?): Result<Pair<Fighter, Fighter>> =
        runCatching {
            coroutineScope {
                val player = async { getCharacterDetail(playerUrl).getOrThrow().toFighter() }
                val opponent = async {
                    getCharacterDetail(opponentUrl ?: randomOpponentUrl(except = playerUrl)).getOrThrow().toFighter()
                }
                player.await() to opponent.await()
            }
        }

    private suspend fun randomOpponentUrl(except: String): String {
        val response = service.getCharacters(
            offset = Random.nextInt(OPPONENT_POOL_OFFSET),
            sort = "count_of_issue_appearances:desc"
        )
        check(response.error == null || response.error == "OK") {
            "Comic Vine: ${response.error}"
        }
        return response.results.orEmpty().mapNotNull { it.apiDetailUrl }.filter { it != except }.randomOrNull()
            ?: error("Nenhum adversário encontrado")
    }

    private companion object {
        // Offset máximo do sorteio: a página cai sempre entre os ~100 mais populares.
        const val OPPONENT_POOL_OFFSET = 80
    }
}

enum class Stat { ATTACK, DEFENSE, SPEED, INTELLIGENCE, FAME }

enum class MoveType { STRIKE, BLAST, POISON, HEAL, GUARD, DODGE }

/** [name] é o nome do poder na Comic Vine (em inglês), exibido como nome do golpe. */
data class Move(val name: String, val type: MoveType)

data class Fighter(
    val name: String,
    val imageUrl: String?,
    val stats: Map<Stat, Int>,
    val moves: List<Move>
)

private const val STAT_BASE = 10
private const val STAT_MAX = 99
private const val POINTS_PER_POWER = 20
private const val FAME_PER_DECADE = 25
private const val MAX_MOVES = 4

/** Trechos procurados (em minúsculas) no nome de cada poder da Comic Vine. */
private val STAT_KEYWORDS = mapOf(
    Stat.ATTACK to listOf("strength", "blast", "combat", "martial", "weapon", "magic", "fire", "claw", "marksman"),
    Stat.DEFENSE to listOf("invulnera", "durab", "healing", "regenerat", "stamina", "armor", "resist", "immortal"),
    Stat.SPEED to listOf("speed", "flight", "agility", "reflex", "teleport", "acrobat"),
    Stat.INTELLIGENCE to listOf("genius", "intellect", "telepath", "tactic", "psychic", "precog", "technolog")
)

/** Mesma ideia do [STAT_KEYWORDS]: a Comic Vine não classifica poderes por tipo. */
private val MOVE_KEYWORDS = mapOf(
    MoveType.POISON to listOf("poison", "toxi", "venom", "acid"),
    MoveType.HEAL to listOf("healing", "regenerat"),
    MoveType.BLAST to listOf("blast", "energy", "fire", "lightning", "magic", "projection", "beam"),
    MoveType.STRIKE to listOf("strength", "combat", "martial", "weapon", "claw", "marksman"),
    MoveType.GUARD to listOf("invulnera", "durab", "armor", "force field", "resist", "stamina"),
    MoveType.DODGE to listOf("speed", "agility", "reflex", "teleport", "acrobat", "flight")
)

private val BASIC_STRIKE = Move("Soco", MoveType.STRIKE)
private val DAMAGING = setOf(MoveType.STRIKE, MoveType.BLAST, MoveType.POISON)

/**
 * A Comic Vine não tem atributos numéricos de combate: eles são derivados dos nomes dos
 * poderes (cada poder que casa com [STAT_KEYWORDS] soma pontos no atributo) e das aparições
 * em revistas (FAMA). Personagem sem poderes cadastrados fica com [STAT_BASE].
 *
 * Golpes: um por tipo (o primeiro poder que casa com [MOVE_KEYWORDS]), até [MAX_MOVES].
 * Sem nenhum golpe de dano, entra um [BASIC_STRIKE] para a luta sempre poder terminar.
 */
internal fun CharacterSummary.toFighter(): Fighter {
    val powerNames = powers.orEmpty().map { it.name }
    val lowercase = powerNames.map { it.lowercase() }
    val stats = STAT_KEYWORDS.mapValues { (_, keywords) ->
        val hits = lowercase.count { power -> keywords.any { it in power } }
        (STAT_BASE + hits * POINTS_PER_POWER).coerceAtMost(STAT_MAX)
    }
    // Escala log: aparições vão de 0 a ~10.000, linear deixaria quase todos perto de zero.
    val fame = (log10((issueAppearances ?: 0) + 1.0) * FAME_PER_DECADE).toInt().coerceIn(STAT_BASE, STAT_MAX)

    val moves = powerNames.mapNotNull { power ->
        val type = MOVE_KEYWORDS.entries.firstOrNull { (_, keywords) -> keywords.any { it in power.lowercase() } }?.key
        type?.let { Move(power, it) }
    }.distinctBy { it.type }
    val withDamage = if (moves.none { it.type in DAMAGING }) listOf(BASIC_STRIKE) + moves else moves

    return Fighter(name, image?.mediumUrl, stats + (Stat.FAME to fame), withDamage.take(MAX_MOVES))
}
