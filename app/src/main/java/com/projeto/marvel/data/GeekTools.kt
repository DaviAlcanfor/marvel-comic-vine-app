package com.projeto.marvel.data

import androidx.core.text.HtmlCompat
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.gson.Gson
import com.projeto.marvel.data.remote.CharacterSummary
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

private const val MAX_TEXT = 1_500
private const val MAX_ITEMS = 12
private const val MAX_RESULT = 8_000

/**
 * Ferramentas do Geek: cada chamada da API do app vira uma função que o Gemini pode pedir
 * (function calling). O resultado volta em JSON enxuto — só o que responde perguntas, sem HTML
 * longo — para o modelo responder com dado real da Comic Vine em vez de inventar.
 */
class GeekTools(
    private val comics: ComicVineRepository = ComicVineRepository(),
    private val catalog: CatalogRepository = CatalogRepository(),
    private val timelines: TimelineRepository = TimelineRepository(),
    private val profile: () -> Map<String, Any?> = { emptyMap() }
) {
    private val gson = Gson()

    private val url = "url" to Schema.string("api_detail_url devolvido por uma busca")
    private val name = "nome" to Schema.string("nome em inglês, como na Comic Vine")

    val declarations = listOf(
        FunctionDeclaration("buscar_personagens", "Busca personagens pelo nome.", mapOf(name)),
        FunctionDeclaration("personagem", "Ficha de um personagem: bio, poderes, times, estreia.", mapOf(url)),
        FunctionDeclaration("personagens_populares", "Os personagens mais famosos (Vingadores).", emptyMap()),
        FunctionDeclaration("linha_do_tempo", "Estreia, times e mortes de um personagem, com datas.", mapOf(url)),
        FunctionDeclaration("atributos_batalha", "Atributos (0–99) e golpes na Batalha do app.", mapOf(url)),
        FunctionDeclaration(
            "times",
            "Lista de times.",
            mapOf("pagina" to Schema.integer("0, 1, 2…")),
            listOf("pagina")
        ),
        FunctionDeclaration("time", "Detalhe de um time: descrição e membros.", mapOf(url)),
        FunctionDeclaration(
            "buscar_hqs",
            "Busca HQs (edições) ou séries pelo nome.",
            mapOf("busca" to Schema.string("título"), "series" to Schema.boolean("true = séries")),
            listOf("series")
        ),
        FunctionDeclaration("buscar_criadores", "Busca roteiristas/desenhistas pelo nome.", mapOf(name)),
        FunctionDeclaration("criador", "Detalhe de um criador.", mapOf(url)),
        FunctionDeclaration("filmes", "Filmes da Marvel.", emptyMap()),
        FunctionDeclaration("filme", "Detalhe de um filme.", mapOf(url)),
        FunctionDeclaration("lugares", "Lugares famosos do universo Marvel.", emptyMap()),
        FunctionDeclaration("lugar", "Detalhe de um lugar.", mapOf(url)),
        FunctionDeclaration("meu_perfil", "Perfil do usuário no app: herói favorito, HQs, filmes, placar.", emptyMap())
    )

    /** Executa a ferramenta [tool]; erro vira {"erro": ...} para o modelo contar ao usuário. */
    suspend fun call(tool: String, args: Map<String, JsonElement>): String {
        val result = runCatching { run(tool, args) }.getOrElse { mapOf("erro" to (it.message ?: "falhou")) }
        return gson.toJson(result).take(MAX_RESULT)
    }

    @Suppress("CyclomaticComplexMethod") // um ramo por ferramenta: é a tabela de despacho
    private suspend fun run(tool: String, args: Map<String, JsonElement>): Any = when (tool) {
        "buscar_personagens" ->
            comics.searchCharacters(args.text("nome")).getOrThrow().take(MAX_ITEMS).map { it.card() }
        "personagem" -> comics.getCharacterDetail(args.text("url")).getOrThrow().sheet()
        "personagens_populares" -> comics.popularCharacters().getOrThrow().take(MAX_ITEMS * 2).map { it.card() }
        "linha_do_tempo" -> timelines.timeline(comics.getCharacterDetail(args.text("url")).getOrThrow()).getOrThrow()
        "atributos_batalha" -> comics.getCharacterDetail(args.text("url")).getOrThrow().toFighter().let {
            mapOf("atributos" to it.stats, "golpes" to it.moves.map { m -> "${m.name} (${m.type})" })
        }
        "times" -> comics.listTeams(offset = (args["pagina"]?.int() ?: 0) * MAX_ITEMS).getOrThrow()
            .map { mapOf("nome" to it.name, "membros" to it.memberCount, "url" to it.apiDetailUrl) }
        "time" -> comics.getTeamDetail(args.text("url")).getOrThrow().let {
            mapOf(
                "nome" to it.name,
                "resumo" to it.deck,
                "descricao" to plain(it.description),
                "membros" to it.members?.map { m -> m.name }
            )
        }
        "buscar_hqs" -> comics.searchComics(args.text("busca"), series = args["series"]?.bool() == true).getOrThrow()
            .take(MAX_ITEMS).map { mapOf("titulo" to it.title, "data" to it.coverDate) }
        "buscar_criadores" -> catalog.creators(args.text("nome")).getOrThrow().take(MAX_ITEMS)
        "criador" -> catalog.creator(args.text("url")).getOrThrow()
        "filmes" -> catalog.marvelMovies().getOrThrow()
        "filme" -> catalog.movie(args.text("url")).getOrThrow()
        "lugares" -> catalog.locations().getOrThrow()
        "lugar" -> catalog.location(args.text("url")).getOrThrow()
        "meu_perfil" -> profile()
        else -> error("ferramenta desconhecida: $tool")
    }
}

private fun Map<String, JsonElement>.text(key: String) =
    (get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: error("faltou o parâmetro $key")

private fun JsonElement.int() = (this as? JsonPrimitive)?.intOrNull

private fun JsonElement.bool() = (this as? JsonPrimitive)?.booleanOrNull

private fun plain(html: String?) =
    html?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim().take(MAX_TEXT) }

private fun CharacterSummary.card() = mapOf(
    "nome" to name,
    "nome_real" to realName,
    "resumo" to deck,
    "aparicoes" to issueAppearances,
    "editora" to publisher?.name,
    "url" to apiDetailUrl
)

private fun CharacterSummary.sheet() = card() + mapOf(
    "origem" to origin?.name,
    "estreia" to (birth ?: firstIssue?.let { listOfNotNull(it.name, it.issueNumber).joinToString(" #") }),
    "poderes" to powers?.map { it.name },
    "times" to teams?.mapNotNull { it.name }?.take(MAX_ITEMS * 2),
    "apelidos" to aliases?.lines()?.filter { it.isNotBlank() },
    "criadores" to creators?.mapNotNull { it.name },
    "filmes" to movies?.mapNotNull { it.name },
    "morreu_em" to issuesDiedIn?.mapNotNull { it.name },
    "biografia" to plain(description)
)
