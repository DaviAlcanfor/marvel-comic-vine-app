package com.projeto.marvel.ui.album

import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil.load
import androidx.core.view.updateLayoutParams
import com.projeto.marvel.R
import com.projeto.marvel.data.PackType
import com.projeto.marvel.databinding.ViewPackBinding
import kotlin.math.sin

// Pacote de figurinhas "3D": papel metalizado na cor do tipo, balanço em Y com perspectiva,
// brilho passando e a abertura (a faixa de cima descola com o dedo e voa).

@StringRes
fun PackType.label() = when (this) {
    PackType.BASIC -> R.string.pack_basic
    PackType.SILVER -> R.string.pack_silver
    PackType.GOLD -> R.string.pack_gold
}

@StringRes
fun PackType.info() = when (this) {
    PackType.BASIC -> R.string.pack_basic_info
    PackType.SILVER -> R.string.pack_silver_info
    PackType.GOLD -> R.string.pack_gold_info
}

/** Metal do pacote (início, brilho do meio, fim): pacote e etiqueta de prêmio das missões. */
fun PackType.colors(): List<Int> = when (this) {
    PackType.BASIC -> listOf(R.color.pack_basic_start, R.color.pack_basic_mid, R.color.pack_basic_end)
    PackType.SILVER -> listOf(R.color.pack_silver_start, R.color.pack_silver_mid, R.color.pack_silver_end)
    PackType.GOLD -> listOf(R.color.pack_gold_start, R.color.pack_gold_mid, R.color.pack_gold_end)
}

private const val TOP_FRACTION = 0.16f
private const val CAMERA_DISTANCE = 10_000f
private const val SHINE_MILLIS = 1_600L
private const val PEEL_DEGREES = 32f
private const val PEEL_LIFT = 0.6f
private const val FLY_DEGREES = 70f
private const val FLY_MILLIS = 420L
private const val PLATE_ALPHA = 220

/**
 * Pinta o pacote do tipo com o desenho de [art] (um herói da raridade do tipo); [large] = o da
 * abertura (letras maiores).
 */
fun ViewPackBinding.style(type: PackType, large: Boolean, art: String? = null) {
    if (packArt.tag != art) {
        packArt.tag = art
        packArt.load(art) { crossfade(true) }
    }
    val context = root.context
    val (start, mid, end) = type.colors().map { ContextCompat.getColor(context, it) }
    val ink = ContextCompat.getColor(context, R.color.ink)
    val radius = context.resources.getDimension(R.dimen.radius_small)
    val stroke = context.resources.getDimensionPixelSize(R.dimen.ink_width)
    packBody.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, mid, end)).apply {
        cornerRadius = radius
        setStroke(stroke, ink)
    }
    packTop.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(end, mid, end)).apply {
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        setStroke(stroke, ink)
    }
    // Plaquinha do nome na cor do tipo, meio transparente: o desenho aparece por trás.
    packPlate.setBackgroundColor(ColorUtils.setAlphaComponent(mid, PLATE_ALPHA))
    val paper = ContextCompat.getColor(context, R.color.comic_paper)
    packRip.background = RipDrawable(paper, ink, context.resources.displayMetrics.density)
    root.post {
        val top = (root.height * TOP_FRACTION).toInt()
        packTop.updateLayoutParams { height = top }
        packRip.translationY = top - packRip.height / 2f
    }
    packName.setText(type.label())
    packInfo.setText(type.info())
    val scale = if (large) LARGE_SCALE else 1f
    packName.textSize = NAME_SP * scale
    packInfo.textSize = INFO_SP * scale
    packBrand.textSize = BRAND_SP * scale
    root.cameraDistance = CAMERA_DISTANCE * context.resources.displayMetrics.density
}

// Miniatura (64dp de largura); o da abertura é [LARGE_SCALE] vezes maior.
private const val NAME_SP = 13f
private const val INFO_SP = 7f
private const val BRAND_SP = 8f
private const val LARGE_SCALE = 2.4f

/** Brilho passando de vez em quando na miniatura (sem girar). */
fun ViewPackBinding.gleam() {
    packShine.animate().cancel()
    packShine.translationX = -packShine.width.toFloat()
    packShine.animate().translationX(root.width.toFloat()).setDuration(SHINE_MILLIS)
        .setInterpolator(LinearInterpolator())
}

/**
 * Faixa descolando com o dedo: [progress] 0..1 do rasgo, [direction] +1 puxando para a direita
 * (a ponta esquerda solta e sobe, a direita segue presa) ou -1. A borda rasgada aparece até onde
 * o rasgo chegou.
 */
fun ViewPackBinding.peel(progress: Float, direction: Int) {
    val width = packTop.width.toFloat()
    packTop.pivotX = if (direction > 0) width else 0f
    packTop.pivotY = packTop.height.toFloat()
    packTop.rotation = -direction * progress * PEEL_DEGREES
    packTop.translationY = -progress * packTop.height * PEEL_LIFT
    packRip.visibility = if (progress > 0f) View.VISIBLE else View.INVISIBLE
    val torn = (progress * root.width).toInt()
    packRip.clipBounds = if (direction > 0) {
        Rect(0, 0, torn, packRip.height)
    } else {
        Rect(root.width - torn, 0, root.width, packRip.height)
    }
}

/** Rasgo completo: a faixa sai voando para o lado do dedo, girando. */
fun ViewPackBinding.flyStrip(direction: Int) {
    val width = root.width.toFloat()
    packTop.animate().translationX(direction * width).translationY(-width / 2).rotation(-direction * FLY_DEGREES)
        .alpha(0f).setStartDelay(0).setDuration(FLY_MILLIS).setInterpolator(DecelerateInterpolator())
}

/** Volta ao estado fechado (para abrir outro). */
fun ViewPackBinding.reset() {
    packRip.visibility = View.INVISIBLE
    packRip.clipBounds = null
    listOf<View>(root, packTop, packBody).forEach {
        it.animate().cancel()
        it.translationX = 0f
        it.translationY = 0f
        it.rotation = 0f
        it.scaleX = 1f
        it.scaleY = 1f
        it.alpha = 1f
    }
}

@ColorRes
fun PackType.accent() = colors()[1]
