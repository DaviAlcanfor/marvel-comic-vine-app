package com.projeto.marvel.ui.opening

import kotlin.random.Random

/** Roteiro da abertura do app. [AUTO] sorteia um dos outros a cada abertura. */
enum class OpeningScript { AUTO, COVER, PANELS, BURST }

/** O roteiro que de fato roda: o escolhido no Perfil ou, no automático, um sorteado. */
fun OpeningScript.resolve(random: Random = Random.Default): OpeningScript =
    if (this == OpeningScript.AUTO) PLAYABLE.random(random) else this

private val PLAYABLE = listOf(OpeningScript.COVER, OpeningScript.PANELS, OpeningScript.BURST)

/** Onomatopeias clássicas de cada herói (a primeira é a "assinatura" dele). */
private val SIGNATURES = listOf(
    listOf("wolverine", "logan", "x-23", "laura kinney") to listOf("SNIKT!", "SHUNK!", "RAAAH!"),
    listOf("spider", "aranha", "venom", "miles morales") to listOf("THWIP!", "THWAK!", "WHAM!"),
    listOf("nightcrawler", "noturno") to listOf("BAMF!", "BAMF!", "POW!"),
    listOf("hulk") to listOf("SMASH!", "KRAKOOM!", "WHAM!"),
    listOf("thor") to listOf("KRA-KOOM!", "BZZAK!", "THOOM!"),
    listOf("iron man", "homem de ferro", "tony stark", "war machine") to listOf("ZZRAK!", "FWOOSH!", "KABLAM!"),
    listOf("human torch", "tocha") to listOf("FWOOSH!", "FLAME ON!", "WHUMP!"),
    listOf("black panther", "pantera") to listOf("SHRAK!", "THUD!", "WAKANDA!"),
    listOf("captain america", "capitão américa") to listOf("KLANG!", "WHAM!", "POW!"),
    listOf("storm", "tempestade") to listOf("KRAKA-THOOM!", "WHOOSH!", "ZZAP!"),
    listOf("doctor strange", "doutor estranho") to listOf("SHAZZZ!", "VWOOM!", "ZAP!"),
    listOf("deadpool") to listOf("BLAM!", "CHIMICHANGA!", "POW!"),
    listOf("magneto") to listOf("KRRANG!", "VMMM!", "BAM!"),
    listOf("cyclops", "ciclope") to listOf("ZAAAK!", "BLAM!", "POW!")
)

private val CLASSIC = listOf("POW!", "BAM!", "ZAP!")

/** Três onomatopeias para a abertura: as do herói (pelo nome) ou o POW/BAM/ZAP clássico. */
fun heroSounds(name: String?): List<String> {
    val lower = name?.lowercase() ?: return CLASSIC
    return SIGNATURES.firstOrNull { (keys, _) -> keys.any { it in lower } }?.second ?: CLASSIC
}
