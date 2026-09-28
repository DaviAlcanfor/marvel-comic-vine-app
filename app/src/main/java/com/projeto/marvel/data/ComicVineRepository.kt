package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.ComicVineService
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.Team
import com.projeto.marvel.data.remote.TeamDetail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.log10

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
     * Busca por nome, paginada ([PAGE_SIZE] por página; página menor que isso = fim).
     *
     * TODO: a Comic Vine ignora o filtro por `publisher` neste endpoint (testado), então a busca
     * pode trazer personagens de outras editoras. Sem busca, a Home usa [popularCharacters].
     */
    suspend fun searchCharacters(query: String, offset: Int = 0): Result<List<CharacterSummary>> =
        runCatching {
            val response = service.getCharacters(
                filter = "name:$query",
                limit = PAGE_SIZE,
                offset = offset,
                fieldList = LIST_FIELDS
            )
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
        }

    /**
     * Personagens populares da Marvel: os membros dos Vingadores (~255), buscados por id em
     * blocos de [PAGE_SIZE] em paralelo e ordenados por aparições. A lista padrão da API
     * (sem filtro) começa por personagens da DC e ignora `sort`/`publisher`, por isso o time.
     *
     * Guardada em memória: Home e seleção da Batalha usam a mesma lista (são 4 requisições).
     */
    suspend fun popularCharacters(): Result<List<CharacterSummary>> =
        runCatching {
            popularCache?.let { return@runCatching it }
            val members = service.getTeamDetail(AVENGERS_URL, fieldList = "characters").let { response ->
                check(response.error == null || response.error == "OK") { "Comic Vine: ${response.error}" }
                response.result?.members.orEmpty()
            }
            coroutineScope {
                members.map { it.id }.chunked(PAGE_SIZE).map { ids ->
                    async {
                        val response = service.getCharacters(
                            filter = "id:${ids.joinToString("|")}",
                            limit = PAGE_SIZE,
                            fieldList = LIST_FIELDS
                        )
                        check(response.error == null || response.error == "OK") {
                            "Comic Vine: ${response.error}"
                        }
                        response.results.orEmpty()
                    }
                }.awaitAll().flatten().sortedByDescending { it.issueAppearances ?: 0 }
            }.also { popularCache = it }
        }

    suspend fun getTeamDetail(apiDetailUrl: String): Result<TeamDetail> =
        runCatching {
            val response = service.getTeamDetail(apiDetailUrl)
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.result ?: error("Time não encontrado")
        }

    suspend fun getCharacterDetail(apiDetailUrl: String): Result<CharacterSummary> =
        runCatching {
            val response = service.getCharacterDetail(apiDetailUrl, DETAIL_FIELDS)
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
     * Carrega os dois lutadores em paralelo. Sem [opponentUrl], sorteia um adversário de
     * [OPPONENT_ROSTER]. Os poderes só vêm no endpoint de detalhe.
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

    /**
     * A Comic Vine ignora `sort` e o filtro por editora em `characters/` (testado: devolve
     * sempre a mesma lista, começando por personagens da DC). O filtro por nome funciona, então
     * o sorteio é de um nome conhecido e fica o resultado da Marvel com mais aparições.
     */
    private suspend fun randomOpponentUrl(except: String): String {
        // Mais de uma tentativa: o nome sorteado pode ser o do próprio jogador.
        for (name in OPPONENT_ROSTER.shuffled().take(MAX_OPPONENT_TRIES)) {
            val response = service.getCharacters(filter = "name:$name")
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.results.orEmpty()
                .filter { it.publisher?.name == MARVEL && it.apiDetailUrl != null && it.apiDetailUrl != except }
                .maxByOrNull { it.issueAppearances ?: 0 }
                ?.apiDetailUrl
                ?.let { return it }
        }
        error("Nenhum adversário encontrado")
    }

    companion object {
        const val PAGE_SIZE = 100 // máximo aceito pela Comic Vine
        private const val AVENGERS_URL = "https://comicvine.gamespot.com/api/team/4060-3806/"

        /** Só o que a lista/card usa; o detalhe busca o resto. */
        private const val LIST_FIELDS =
            "id,name,real_name,deck,image,publisher,api_detail_url,count_of_issue_appearances"

        /**
         * Sem isso o detalhe traz as listas de todas as aparições (milhares no Homem-Aranha):
         * megabytes por personagem, e a arena demorava ~10 s para abrir.
         */
        private const val DETAIL_FIELDS = "$LIST_FIELDS,description,powers,origin,teams"

        // ponytail: cache só em memória (some ao fechar o app); persistir se a API ficar lenta demais.
        @Volatile private var popularCache: List<CharacterSummary>? = null
        private const val MARVEL = "Marvel"
        private const val MAX_OPPONENT_TRIES = 3

        /** Heróis e vilões populares da Marvel para o adversário aleatório. */
        private val OPPONENT_ROSTER = listOf(
            "Spider-Man", "Iron Man", "Captain America", "Thor", "Hulk", "Black Widow",
            "Wolverine", "Storm", "Deadpool", "Doctor Strange", "Black Panther", "Captain Marvel",
            "Scarlet Witch", "Daredevil", "Thanos", "Loki", "Magneto", "Doctor Doom",
            "Green Goblin", "Venom", "Ultron", "Red Skull"
        )
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
