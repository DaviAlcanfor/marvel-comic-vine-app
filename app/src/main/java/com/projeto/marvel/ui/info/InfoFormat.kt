package com.projeto.marvel.ui.info

import java.text.NumberFormat
import java.util.Locale

// A Comic Vine devolve datas e valores como texto livre ("1922-12-28 00:00:00", "890,871,626\t").

private const val DATE_LENGTH = 10
private const val DATE_PARTS = 3
private const val MILLION = 1_000_000.0
private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** "1922-12-28 00:00:00" → "28/12/1922"; texto em outro formato volta como veio. */
fun formatDate(raw: String?): String? {
    val date = raw?.trim()?.take(DATE_LENGTH) ?: return null
    val parts = date.split("-")
    return if (parts.size == DATE_PARTS && parts.all { part -> part.all(Char::isDigit) }) {
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } else {
        raw.trim().ifEmpty { null }
    }
}

/** "890,871,626\t" → "US$ 890,9 mi"; sem dígitos (ou zero) → null. */
fun formatMoney(raw: String?): String? {
    val value = raw?.filter(Char::isDigit)?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val millions = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 1 }.format(value / MILLION)
    return "US$ $millions mi"
}
