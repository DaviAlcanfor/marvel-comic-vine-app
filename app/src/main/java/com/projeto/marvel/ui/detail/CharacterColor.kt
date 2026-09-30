package com.projeto.marvel.ui.detail

import kotlin.math.pow

// Cor de destaque do Detalhe tirada da foto do personagem (Palette). Contas em Kotlin puro, sem
// android.graphics, para rodar no teste de unidade.

private const val MIN_CONTRAST = 4.5
private const val LIGHTEN_STEP = 0.1f
private const val CHANNEL_MAX = 255
private const val LINEAR_THRESHOLD = 0.03928
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_DIVISOR = 1.055
private const val GAMMA = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val CONTRAST_OFFSET = 0.05
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val OPAQUE = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = OPAQUE

/** Luminância relativa (WCAG) de uma cor ARGB. */
fun luminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val c = (color shr shift and CHANNEL_MAX) / CHANNEL_MAX.toDouble()
        return if (c <= LINEAR_THRESHOLD) c / LINEAR_DIVISOR else ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
    }
    return RED_WEIGHT * channel(RED_SHIFT) + GREEN_WEIGHT * channel(GREEN_SHIFT) + BLUE_WEIGHT * channel(0)
}

fun contrast(a: Int, b: Int): Double {
    val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (light + CONTRAST_OFFSET) / (dark + CONTRAST_OFFSET)
}

/**
 * [color] misturado com branco (fundo escuro) ou preto (fundo claro) até ter contraste 4,5:1 com
 * [background] — a cor de um personagem escuro (Venom, Pantera Negra) sumiria no tema escuro, e a
 * de um claro (Homem de Gelo) no tema claro.
 */
fun readableOn(background: Int, color: Int): Int {
    val target = if (contrast(WHITE, background) >= contrast(BLACK, background)) WHITE else BLACK
    var amount = 0f
    var result = color or OPAQUE
    while (contrast(result, background) < MIN_CONTRAST && amount < 1f) {
        amount += LIGHTEN_STEP
        result = mixToward(color, target, amount)
    }
    return result
}

private fun mixToward(color: Int, target: Int, amount: Float): Int {
    fun mix(shift: Int): Int {
        val c = color shr shift and CHANNEL_MAX
        val t = target shr shift and CHANNEL_MAX
        return (c + (t - c) * amount).toInt().coerceIn(0, CHANNEL_MAX) shl shift
    }
    return OPAQUE or mix(RED_SHIFT) or mix(GREEN_SHIFT) or mix(0)
}
