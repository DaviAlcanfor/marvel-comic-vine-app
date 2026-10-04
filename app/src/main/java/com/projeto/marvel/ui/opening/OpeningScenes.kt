package com.projeto.marvel.ui.opening

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import com.projeto.marvel.R
import com.projeto.marvel.data.Favorite
import com.projeto.marvel.databinding.ActivityMainBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.EraPanelDrawable
import com.projeto.marvel.ui.LogoTextView
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.eraFont
import com.projeto.marvel.ui.finishComicLoading
import com.projeto.marvel.ui.startComicLoading

// Abertura do app em três roteiros, todos com o seu herói (foto, nome e a onomatopeia dele) e no
// traço da época: capa de HQ que abre como livro, quadros que caem e montam uma página, ou a
// explosão clássica (ComicLoading.kt). Escolha no Perfil; o automático sorteia.

private const val COVER_DROP_SCALE = 1.12f
private const val COVER_IN_MILLIS = 520L
private const val COVER_OPEN_MILLIS = 620L
private const val COVER_OPEN_DEGREES = -105f
private const val CAMERA_DISTANCE = 12_000f
private const val WORD_DELAY_MILLIS = 420L
private const val WORD_POP_MILLIS = 300L
private const val WORD_TILT = -10f
private const val PANEL_STAGGER_MILLIS = 170L
private const val PANEL_IN_MILLIS = 480L
private const val PANEL_OUT_MILLIS = 380L
private const val PANEL_TILT = 4f
private const val PANEL_TENSION = 1.3f
private const val HERO_PANEL_WEIGHT = 1.6f
private const val FADE_MILLIS = 300L
private const val TITLE_SP = 40f
private const val CAPTION_SP = 15f

/** Liga a abertura [script] com o [hero]; devolve quem a encerra quando os dados chegaram. */
fun ActivityMainBinding.startOpening(hero: Favorite?, script: OpeningScript): () -> Unit {
    val words = heroSounds(hero?.name)
    return when (script.resolve()) {
        OpeningScript.COVER -> cover(hero, words)
        OpeningScript.PANELS -> panels(hero, words)
        else -> {
            val stop = startComicLoading(words)
            return { finishComicLoading(stop) }
        }
    }
}

/** Esconde a explosão clássica: os outros roteiros montam a cena por cima do mesmo fundo. */
private fun ActivityMainBinding.cleanStage(): FrameLayout {
    loading.visibility = View.VISIBLE
    listOf(loadingPow, loadingBam, loadingZap, loadingLogo, loadingBalloon).forEach { it.visibility = View.GONE }
    return loading
}

/** Capa de HQ com o herói; ao terminar, abre como livro (gira na lombada) e revela o app. */
private fun ActivityMainBinding.cover(hero: Favorite?, words: List<String>): () -> Unit {
    val stage = cleanStage()
    val context = stage.context
    val gutter = context.resources.getDimensionPixelSize(R.dimen.space_xl)
    val cover = FrameLayout(context).apply {
        background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
        clipToOutline = true
    }
    stage.addView(cover, frame(MATCH, MATCH).margins(gutter, gutter * 2, gutter, gutter * 2))
    cover.addView(heroImage(hero), FrameLayout.LayoutParams(MATCH, MATCH))
    cover.addView(
        LogoTextView(context).apply {
            text = hero?.name ?: context.getString(R.string.app_name)
            textSize = TITLE_SP
            maxLines = 2
            gravity = Gravity.CENTER
        },
        frame(MATCH, WRAP, Gravity.TOP).margins(gutter / 2, gutter / 2, gutter / 2, 0)
    )
    val word = sfx(words.first())
    cover.addView(word, frame(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).margins(0, 0, gutter / 2, gutter))
    cover.cameraDistance = CAMERA_DISTANCE * context.resources.displayMetrics.density
    cover.alpha = 0f
    cover.scaleX = COVER_DROP_SCALE
    cover.scaleY = COVER_DROP_SCALE
    cover.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(COVER_IN_MILLIS)
        .setInterpolator(DecelerateInterpolator())
    word.pop(WORD_DELAY_MILLIS)
    return {
        cover.pivotX = 0f
        cover.animate().rotationY(COVER_OPEN_DEGREES).setDuration(COVER_OPEN_MILLIS)
            .setInterpolator(AccelerateInterpolator())
        stage.animate().alpha(0f).setStartDelay(COVER_OPEN_MILLIS / 2).setDuration(FADE_MILLIS).withEndAction {
            stage.removeView(cover)
            stage.visibility = View.GONE
        }
    }
}

/** Página de três quadros que caem um a um (legenda, onomatopeia, herói); depois despencam. */
private fun ActivityMainBinding.panels(hero: Favorite?, words: List<String>): () -> Unit {
    val stage = cleanStage()
    val context = stage.context
    val gutter = context.resources.getDimensionPixelSize(R.dimen.space_md)
    val page = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(gutter, gutter * 2, gutter, gutter * 2)
    }
    stage.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
    fun panel(weight: Float) = FrameLayout(context).apply {
        background = EraPanelDrawable(context, EraPanelDrawable.Kind.PANEL)
        clipToOutline = true
        page.addView(this, LinearLayout.LayoutParams(MATCH, 0, weight).margins(0, gutter / 2, 0, gutter / 2))
    }
    val caption = TextView(context).apply {
        setText(R.string.opening_meanwhile)
        textSize = CAPTION_SP
        comicBox(BoxStyle.CAPTION, ContextCompat.getColor(context, R.color.caption_yellow))
    }
    panel(1f).addView(caption, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    val word = sfx(words.first())
    panel(1f).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.primary))
        addView(word, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }
    panel(HERO_PANEL_WEIGHT).apply {
        addView(heroImage(hero), FrameLayout.LayoutParams(MATCH, MATCH))
        addView(
            LogoTextView(context).apply {
                text = hero?.name ?: context.getString(R.string.app_name)
                textSize = TITLE_SP
                maxLines = 1
            },
            frame(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).margins(gutter, 0, 0, gutter)
        )
    }
    val drop = context.resources.displayMetrics.heightPixels.toFloat()
    val panels = (0 until page.childCount).map(page::getChildAt)
    panels.forEachIndexed { index, view ->
        view.translationY = -drop
        view.rotation = if (index % 2 == 0) -PANEL_TILT else PANEL_TILT
        view.animate().translationY(0f).rotation(0f).setStartDelay(index * PANEL_STAGGER_MILLIS)
            .setDuration(PANEL_IN_MILLIS).setInterpolator(OvershootInterpolator(PANEL_TENSION))
    }
    word.pop(PANEL_STAGGER_MILLIS + PANEL_IN_MILLIS)
    return {
        panels.forEachIndexed { index, view ->
            view.animate().translationY(drop).rotation(if (index % 2 == 0) PANEL_TILT else -PANEL_TILT)
                .setStartDelay(index * PANEL_STAGGER_MILLIS / 2).setDuration(PANEL_OUT_MILLIS)
                .setInterpolator(AccelerateInterpolator())
        }
        stage.animate().alpha(0f).setStartDelay(PANEL_OUT_MILLIS).setDuration(FADE_MILLIS).withEndAction {
            stage.removeView(page)
            stage.visibility = View.GONE
        }
    }
}

/** Foto do herói (do cache da Início, quando já veio); sem herói, a retícula da época. */
private fun ActivityMainBinding.heroImage(hero: Favorite?): View {
    val context = root.context
    return ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setBackgroundResource(R.drawable.bg_screen)
        hero?.imageUrl?.let { load(it) { crossfade(true) } }
    }
}

/** Onomatopeia na explosão amarela, na fonte de estouro da época. */
private fun ActivityMainBinding.sfx(text: String): TextView {
    val context = root.context
    return TextView(context).apply {
        this.text = text
        typeface = context.eraFont(R.attr.eraSfxFont)
        textSize = CAPTION_SP * 2
        comicBox(BoxStyle.BURST, ContextCompat.getColor(context, R.color.logo_yellow))
        rotation = WORD_TILT
        alpha = 0f
    }
}

private fun View.pop(delay: Long) {
    scaleX = 0f
    scaleY = 0f
    animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(WORD_POP_MILLIS)
        .setInterpolator(OvershootInterpolator())
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private fun frame(width: Int, height: Int, gravity: Int = Gravity.NO_GRAVITY) =
    FrameLayout.LayoutParams(width, height, gravity)

private fun <T : ViewGroup.MarginLayoutParams> T.margins(left: Int, top: Int, right: Int, bottom: Int) =
    apply { setMargins(left, top, right, bottom) }
