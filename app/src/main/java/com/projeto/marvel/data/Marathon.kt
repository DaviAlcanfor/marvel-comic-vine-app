package com.projeto.marvel.data

/** Filme da maratona: [movieId] é o id da Comic Vine; [minutes] a duração real. */
data class MarathonMovie(val movieId: Int, val title: String, val minutes: Int)

/** Onde você está na maratona: vistos, total, minutos que faltam e o próximo da ordem. */
data class MarathonProgress(val watched: Int, val total: Int, val minutesLeft: Int, val next: MarathonMovie?)

fun marathonProgress(watchedIds: Set<Int>, movies: List<MarathonMovie> = MCU_MARATHON): MarathonProgress {
    val left = movies.filterNot { it.movieId in watchedIds }
    return MarathonProgress(movies.size - left.size, movies.size, left.sumOf { it.minutes }, left.firstOrNull())
}

/**
 * Maratona do MCU na ordem da história (a da linha do tempo do Disney+). Lista curada: a Comic Vine
 * não tem ordem cronológica e o `runtime` dela vem em formatos misturados ("2 hrs. 27", "2h 5") e às
 * vezes errado, então a duração fica aqui também.
 */
@Suppress("MagicNumber") // ids da Comic Vine e durações em minutos: é a própria tabela
val MCU_MARATHON = listOf(
    MarathonMovie(927, "Capitão América: O Primeiro Vingador", 124),
    MarathonMovie(2328, "Capitã Marvel", 123),
    MarathonMovie(17, "Homem de Ferro", 126),
    MarathonMovie(733, "Homem de Ferro 2", 124),
    MarathonMovie(45, "O Incrível Hulk", 112),
    MarathonMovie(949, "Thor", 115),
    MarathonMovie(936, "Os Vingadores", 143),
    MarathonMovie(1514, "Thor: O Mundo Sombrio", 112),
    MarathonMovie(1512, "Homem de Ferro 3", 130),
    MarathonMovie(1542, "Capitão América: O Soldado Invernal", 136),
    MarathonMovie(1541, "Guardiões da Galáxia", 121),
    MarathonMovie(2229, "Guardiões da Galáxia Vol. 2", 136),
    MarathonMovie(1563, "Vingadores: Era de Ultron", 141),
    MarathonMovie(1552, "Homem-Formiga", 117),
    MarathonMovie(2143, "Capitão América: Guerra Civil", 147),
    MarathonMovie(2446, "Viúva Negra", 134),
    MarathonMovie(2292, "Pantera Negra", 134),
    MarathonMovie(2239, "Homem-Aranha: De Volta ao Lar", 133),
    MarathonMovie(2183, "Doutor Estranho", 115),
    MarathonMovie(2278, "Thor: Ragnarok", 130),
    MarathonMovie(2304, "Homem-Formiga e a Vespa", 118),
    MarathonMovie(2297, "Vingadores: Guerra Infinita", 149),
    MarathonMovie(2360, "Vingadores: Ultimato", 181),
    MarathonMovie(2550, "Shang-Chi e a Lenda dos Dez Anéis", 132),
    MarathonMovie(2560, "Eternos", 156),
    MarathonMovie(2367, "Homem-Aranha: Longe de Casa", 129),
    MarathonMovie(2619, "Homem-Aranha: Sem Volta para Casa", 148),
    MarathonMovie(2679, "Doutor Estranho no Multiverso da Loucura", 126),
    MarathonMovie(2742, "Thor: Amor e Trovão", 119),
    MarathonMovie(2766, "Pantera Negra: Wakanda para Sempre", 161),
    MarathonMovie(2833, "Homem-Formiga e a Vespa: Quantumania", 125),
    MarathonMovie(2832, "Guardiões da Galáxia Vol. 3", 150),
    MarathonMovie(2983, "As Marvels", 105),
    MarathonMovie(3100, "Deadpool & Wolverine", 128),
    MarathonMovie(3179, "Capitão América: Admirável Mundo Novo", 118),
    MarathonMovie(256, "Thunderbolts*", 127),
    MarathonMovie(3204, "Quarteto Fantástico: Primeiros Passos", 115)
)
