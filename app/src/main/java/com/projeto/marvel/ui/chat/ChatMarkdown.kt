package com.projeto.marvel.ui.chat

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan

private val BOLD = Regex("""\*\*(.+?)\*\*""")

/**
 * O Gemini responde em markdown; o balão é texto puro. Tira os `**` e devolve onde ficam os trechos
 * em negrito (regra pura, testada em ChatMarkdownTest).
 */
fun boldRuns(text: String): Pair<String, List<IntRange>> {
    val out = StringBuilder()
    val runs = mutableListOf<IntRange>()
    var last = 0
    for (match in BOLD.findAll(text)) {
        out.append(text, last, match.range.first)
        val start = out.length
        out.append(match.groupValues[1])
        runs += start until out.length
        last = match.range.last + 1
    }
    out.append(text, last, text.length)
    return out.toString() to runs
}

fun markdownBold(text: String): CharSequence {
    val (plain, runs) = boldRuns(text)
    if (runs.isEmpty()) return plain
    return SpannableString(plain).apply {
        runs.forEach { setSpan(StyleSpan(Typeface.BOLD), it.first, it.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }
}
