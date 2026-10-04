package com.projeto.marvel.ui.quote

// Regras puras do "Quem disse?" (testadas em QuoteRulesTest).

const val QUOTE_MASK = "???"
const val QUOTE_PACK_EVERY = 5
private const val MIN_WORD = 3

/**
 * Esconde o personagem no resumo: cada palavra do nome e do nome real (3+ letras) vira [QUOTE_MASK],
 * sem diferenciar maiúsculas, inclusive com "'s" e hífens (Spider-Man → ???-???).
 */
fun maskNames(text: String, vararg names: String?): String {
    val words = names.filterNotNull()
        .flatMap { it.split(' ', '-', '.', '(', ')') }
        .map { it.trim() }
        .filter { it.length >= MIN_WORD }
        .distinct()
        .sortedByDescending { it.length }
    return words.fold(text) { acc, word ->
        acc.replace(Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE), QUOTE_MASK)
    }
}

/** Ganha pacote a cada [QUOTE_PACK_EVERY] acertos seguidos. */
fun earnsPack(streak: Int) = streak > 0 && streak % QUOTE_PACK_EVERY == 0
