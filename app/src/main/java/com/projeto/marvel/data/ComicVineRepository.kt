package com.projeto.marvel.data

import com.projeto.marvel.data.remote.ApiClient
import com.projeto.marvel.data.remote.CharacterSummary
import com.projeto.marvel.data.remote.Issue
import com.projeto.marvel.data.remote.ComicVineService
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
            charactersByIds(members.map { it.id }).getOrThrow().also { popularCache = it }
        }

    /**
     * Cards (com foto) de personagens por id, mais aparições primeiro. A referência a um
     * personagem (membro de time etc.) só traz id e nome: a foto vem de
     * `characters/?filter=id:1|2|3`, em blocos de [PAGE_SIZE] em paralelo.
     */
    suspend fun charactersByIds(ids: List<Int>): Result<List<CharacterSummary>> = runCatching {
        coroutineScope {
            ids.distinct().chunked(PAGE_SIZE).map { chunk ->
                async {
                    val response = service.getCharacters(
                        filter = "id:${chunk.joinToString("|")}",
                        limit = PAGE_SIZE,
                        fieldList = LIST_FIELDS
                    )
                    check(response.error == null || response.error == "OK") { "Comic Vine: ${response.error}" }
                    response.results.orEmpty()
                }
            }.awaitAll().flatten().sortedByDescending { it.issueAppearances ?: 0 }
        }
    }

    /**
     * Busca de HQs (issues) pelo nome da série, ex. "amazing spider-man". Com [series], busca as
     * séries (volumes) em si — vêm no mesmo formato, com `name` e `image`.
     */
    suspend fun searchComics(query: String, series: Boolean = false): Result<List<Issue>> =
        runCatching {
            val resources = if (series) "volume" else "issue"
            val response = service.search(query, resources = resources, fieldList = COMIC_FIELDS)
            check(response.error == null || response.error == "OK") { "Comic Vine: ${response.error}" }
            response.results.orEmpty()
        }

    /** Capa de uma issue (ex.: a primeira aparição do personagem), ou null se ela não tiver imagem. */
    suspend fun issueCover(issueId: Int): Result<String?> =
        runCatching {
            val response = service.getIssues(filter = "id:$issueId", limit = 1, fieldList = "image")
            // A Comic Vine responde 200 mesmo em erro de negócio; o status vem no corpo.
            check(response.error == null || response.error == "OK") { "Comic Vine: ${response.error}" }
            response.results?.firstOrNull()?.image?.mediumUrl
        }

    suspend fun getTeamDetail(apiDetailUrl: String): Result<TeamDetail> =
        runCatching {
            val response = service.getTeamDetail(apiDetailUrl)
            check(response.error == null || response.error == "OK") {
                "Comic Vine: ${response.error}"
            }
            response.result ?: error("Time não encontrado")
        }

    /** [full]: também biografia completa, aliados e inimigos (contexto do chat com o personagem). */
    suspend fun getCharacterDetail(apiDetailUrl: String, full: Boolean = false): Result<CharacterSummary> =
        runCatching {
            val response = service.getCharacterDetail(apiDetailUrl, if (full) FULL_FIELDS else DETAIL_FIELDS)
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
     * Carrega o jogador e seus adversários em paralelo (os poderes só vêm no endpoint de detalhe).
     * - [teamUrl]: trilha do time — os [GAUNTLET_SIZE] membros mais famosos, do menos ao mais
     *   famoso; o último é o chefe.
     * - vários [playerUrls] (3×3): um trio rival sorteado do mesmo tamanho, sem repetir ninguém.
     * - senão, um adversário só: [opponentUrl] ou um sorteado de [OPPONENT_ROSTER].
     */
    suspend fun getFighters(
        playerUrls: List<String>,
        opponentUrl: String? = null,
        teamUrl: String? = null
    ): Result<Pair<List<Fighter>, List<Fighter>>> =
        runCatching {
            val rivals = when {
                teamUrl != null -> gauntletUrls(teamUrl, except = playerUrls.first())
                opponentUrl != null -> listOf(opponentUrl)
                else -> mutableListOf<String>().apply {
                    repeat(playerUrls.size) { add(service.randomOpponentUrl(except = playerUrls + this)) }
                }
            }
            coroutineScope {
                val fighters = (playerUrls + rivals)
                    .map { url -> async { getCharacterDetail(url).getOrThrow().toFighter() } }
                    .awaitAll()
                val opponents = fighters.drop(playerUrls.size)
                val withBoss = if (teamUrl != null) {
                    opponents.dropLast(1) + opponents.last().copy(boss = true)
                } else {
                    opponents
                }
                fighters.take(playerUrls.size) to withBoss
            }
        }

    private suspend fun gauntletUrls(teamUrl: String, except: String): List<String> {
        val members = service.getTeamDetail(teamUrl, fieldList = "characters").let { response ->
            check(response.error == null || response.error == "OK") { "Comic Vine: ${response.error}" }
            response.result?.members.orEmpty()
        }
        return charactersByIds(members.map { it.id }).getOrThrow()
            .mapNotNull { it.apiDetailUrl }
            .filter { it != except }
            .take(GAUNTLET_SIZE)
            .reversed()
            .also { check(it.isNotEmpty()) { "Esse time não tem membros para enfrentar" } }
    }

    companion object {
        const val PAGE_SIZE = 100 // máximo aceito pela Comic Vine
        const val GAUNTLET_SIZE = 5
        private const val COMIC_FIELDS = "id,name,issue_number,cover_date,volume,image"
        private const val AVENGERS_URL = "https://comicvine.gamespot.com/api/team/4060-3806/"

        /** Só o que a lista/card usa; o detalhe busca o resto. */
        private const val LIST_FIELDS =
            "id,name,real_name,deck,image,publisher,api_detail_url,count_of_issue_appearances,origin,birth"

        /**
         * Sem isso o detalhe traz as listas de todas as aparições (milhares no Homem-Aranha):
         * megabytes por personagem, e a arena demorava ~10 s para abrir.
         */
        private const val DETAIL_FIELDS =
            "$LIST_FIELDS,description,powers,teams,first_appeared_in_issue,aliases,creators,movies,issues_died_in"
        private const val FULL_FIELDS = "$DETAIL_FIELDS,character_enemies,character_friends"

        // ponytail: cache só em memória (some ao fechar o app); persistir se a API ficar lenta demais.
        @Volatile private var popularCache: List<CharacterSummary>? = null
    }
}

private const val MARVEL = "Marvel"
private const val MAX_OPPONENT_TRIES = 3

/** Heróis e vilões populares da Marvel para o adversário aleatório. */
private val OPPONENT_ROSTER = listOf(
    "Spider-Man", "Iron Man", "Captain America", "Thor", "Hulk", "Black Widow",
    "Wolverine", "Storm", "Deadpool", "Doctor Strange", "Black Panther", "Captain Marvel",
    "Scarlet Witch", "Daredevil", "Thanos", "Loki", "Magneto", "Doctor Doom",
    "Green Goblin", "Venom", "Ultron", "Red Skull"
)

/**
 * A Comic Vine ignora `sort` e o filtro por editora em `characters/` (testado: devolve
 * sempre a mesma lista, começando por personagens da DC). O filtro por nome funciona, então
 * o sorteio é de um nome conhecido e fica o resultado da Marvel com mais aparições.
 */
private suspend fun ComicVineService.randomOpponentUrl(except: Collection<String>): String {
    // Mais de uma tentativa: o nome sorteado pode ser o do próprio jogador.
    for (name in OPPONENT_ROSTER.shuffled().take(MAX_OPPONENT_TRIES)) {
        val response = getCharacters(filter = "name:$name")
        check(response.error == null || response.error == "OK") {
            "Comic Vine: ${response.error}"
        }
        response.results.orEmpty()
            .filter { it.publisher?.name == MARVEL && it.apiDetailUrl != null && it.apiDetailUrl !in except }
            .maxByOrNull { it.issueAppearances ?: 0 }
            ?.apiDetailUrl
            ?.let { return it }
    }
    error("Nenhum adversário encontrado")
}

enum class Stat { ATTACK, DEFENSE, SPEED, INTELLIGENCE, FAME }

/** Na ordem de prioridade para escolher os golpes: os temáticos antes dos genéricos. */
enum class MoveType { FREEZE, WATER, MAGIC, DRAIN, POISON, BLAST, STRIKE, HEAL, GUARD, DODGE }

/** [name] é o nome do poder na Comic Vine (em inglês), exibido como nome do golpe. */
/** [ultimate]: o golpe especial da barra de energia (ver `ultimateMove` nas regras da Batalha). */
data class Move(val name: String, val type: MoveType, val ultimate: Boolean = false)

/**
 * [boss]: último adversário da trilha de um time, com vida extra (ver `maxHp`).
 * [id] = do personagem (liga ao álbum); [level] = nível pelas figurinhas repetidas (ver `leveled`).
 */
data class Fighter(
    val id: Int = 0,
    val name: String,
    val imageUrl: String?,
    val stats: Map<Stat, Int>,
    val moves: List<Move>,
    val boss: Boolean = false,
    val level: Int = 1,
    /** Carta dourada no álbum: +5 em tudo e nome dourado na arena. */
    val golden: Boolean = false
)

private const val STAT_BASE = 10
private const val STAT_MAX = 99
private const val STAT_BUDGET = 100
private const val FAME_PER_DECADE = 25
private const val MAX_MOVES = 4

/** Trechos procurados (em minúsculas) no nome de cada poder da Comic Vine (lista em `powers/`). */
private val STAT_KEYWORDS = mapOf(
    Stat.ATTACK to listOf(
        "strength", "blast", "combat", "martial", "weapon", "claw", "marksman", "sword",
        "energy-enhanced", "heat vision", "sonic", "death touch", "vibration"
    ),
    Stat.DEFENSE to listOf(
        "invulnera", "durab", "healing", "regenerat", "stamina", "armor", "resist", "immortal",
        "adaptive", "force field", "energy shield", "density", "absorption", "power suit"
    ),
    Stat.SPEED to listOf(
        "speed", "flight", "agility", "reflex", "teleport", "acrobat", "wall clinger", "webslinger",
        "danger sense", "escape artist", "stealth", "levitation", "phasing"
    ),
    Stat.INTELLIGENCE to listOf(
        "genius", "intellect", "telepath", "tactic", "psychic", "precog", "technolog", "gadgets",
        "magic", "leadership", "cosmic", "psionic", "telekinesis", "illusion", "hypnosis"
    )
)

/** Mesma ideia do [STAT_KEYWORDS]: a Comic Vine não classifica poderes por tipo. Ordem = prioridade. */
private val MOVE_KEYWORDS = linkedMapOf(
    MoveType.FREEZE to listOf("ice control", "ice breath", "cryo", "freez"), // "ice" sozinho casaria "Voice"
    MoveType.WATER to listOf("water", "hydro", "sub-mariner"),
    MoveType.MAGIC to listOf("magic", "sorcer", "mystic", "divine", "necromancy", "hellfire", "reality", "illusion"),
    MoveType.DRAIN to listOf("lifeforce", "soul absorption", "vampir", "drain"),
    MoveType.POISON to listOf("poison", "toxi", "venom", "acid", "chemical secretion"),
    MoveType.BLAST to listOf(
        "blast", "energy", "fire", "flame", "lightning", "electric", "projection", "beam", "heat vision"
    ),
    MoveType.STRIKE to listOf("strength", "combat", "martial", "weapon", "claw", "marksman", "sword"),
    MoveType.HEAL to listOf("healing", "regenerat"),
    MoveType.GUARD to listOf("invulnera", "durab", "armor", "force field", "energy shield", "resist", "stamina"),
    MoveType.DODGE to listOf("speed", "agility", "reflex", "teleport", "acrobat", "flight", "phasing")
)

/** Tipo de golpe de um poder da Comic Vine, ou null se nenhuma palavra-chave casar. */
fun moveTypeOf(power: String): MoveType? {
    val lowercase = power.lowercase()
    return MOVE_KEYWORDS.entries.firstOrNull { (_, keywords) -> keywords.any { it in lowercase } }?.key
}

private val BASIC_STRIKE = Move("Soco", MoveType.STRIKE)
private val DAMAGING = setOf(
    MoveType.FREEZE, MoveType.WATER, MoveType.MAGIC, MoveType.DRAIN, MoveType.POISON, MoveType.BLAST, MoveType.STRIKE
)

/**
 * A Comic Vine não tem atributos numéricos de combate: eles são derivados dos nomes dos
 * poderes e das aparições em revistas (FAMA). Todo personagem reparte o mesmo [STAT_BUDGET]
 * entre os atributos de combate, com peso (acertos em [STAT_KEYWORDS] + 1)²: o quadrado
 * acentua o ponto forte (Hulk bate e aguenta, Homem-Aranha é rápido) sem mudar o total, então
 * quem tem mais poderes cadastrados não fica mais forte, só mais especializado.
 *
 * Golpes: um por tipo, os temáticos primeiro (ordem de [MoveType]), até [MAX_MOVES].
 * Sem nenhum golpe de dano, entra um [BASIC_STRIKE] para a luta sempre poder terminar.
 */
internal fun CharacterSummary.toFighter(): Fighter {
    val powerNames = powers.orEmpty().map { it.name }
    val lowercase = powerNames.map { it.lowercase() }
    val weights = STAT_KEYWORDS.mapValues { (_, keywords) ->
        val hits = lowercase.count { power -> keywords.any { it in power } }
        (hits + 1) * (hits + 1)
    }
    val total = weights.values.sum()
    val stats = weights.mapValues { (_, weight) -> (STAT_BASE + STAT_BUDGET * weight / total).coerceAtMost(STAT_MAX) }
    // Escala log: aparições vão de 0 a ~10.000, linear deixaria quase todos perto de zero.
    val fame = (log10((issueAppearances ?: 0) + 1.0) * FAME_PER_DECADE).toInt().coerceIn(STAT_BASE, STAT_MAX)

    val moves = powerNames.mapNotNull { power -> moveTypeOf(power)?.let { Move(power, it) } }
        .distinctBy { it.type }
        .sortedBy { it.type.ordinal }
    val withDamage = if (moves.none { it.type in DAMAGING }) listOf(BASIC_STRIKE) + moves else moves

    return Fighter(id, name, image?.mediumUrl, stats + (Stat.FAME to fame), withDamage.take(MAX_MOVES))
}
