package com.projeto.marvel.ui.album

import android.animation.Animator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.projeto.marvel.R
import com.projeto.marvel.data.Rarity
import com.projeto.marvel.databinding.FragmentAlbumBinding
import com.projeto.marvel.databinding.ItemTradingCardBinding
import com.projeto.marvel.ui.BoxStyle
import com.projeto.marvel.ui.SpeedLinesDrawable
import com.projeto.marvel.ui.battle.startSpeedLines
import com.projeto.marvel.ui.comicBox
import com.projeto.marvel.ui.shake
import kotlin.random.Random

private const val DEAL_STAGGER_MILLIS = 120L
private const val RISE_MILLIS = 280L
private const val SPREAD_MILLIS = 480L
private const val DEAL_START_SCALE = 0.55f
private const val RISE_DP = 70f
private const val DEAL_SPIN = 12f
private const val SPREAD_TENSION = 0.9f
private const val PACK_MOUTH = 0.2f
private const val PACK_EXIT_MILLIS = 450L
private const val FLIP_LIFT = 1.1f
private const val FLIP_TENSION = 1.4f
private const val FLIP_GAP_MILLIS = 260L
private const val RARE_SUSPENSE_MILLIS = 380L
private const val LEGENDARY_SUSPENSE_MILLIS = 900L
private const val QUARTER_TURN = 90f
private const val CAMERA_DISTANCE = 8_000f
private const val FLIP_IN_MILLIS = 170L
private const val FLIP_OUT_MILLIS = 280L
private const val WORD_POP_MILLIS = 240L
private const val WORD_HOLD_MILLIS = 650L
private const val WORD_START_SCALE = 0.2f
private const val WORD_TILT = 8f
private const val LINES_ALPHA = 55
private const val FADE_MILLIS = 250L

/**
 * Depois do rasgo: RIIIP!, as cartas sobem de dentro do pacote (de costas), se espalham na grade,
 * o pacote desce e elas viram uma a uma. Quanto
 * mais rara, mais suspense antes de virar (treme, e na lendária/Divina sai LENDÁRIA! e a aura).
 * Repetida mostra ×N; a primeira cópia, NOVA!. Um toque na tela pula para o fim ([skip]).
 */
class PackReveal(private val binding: FragmentAlbumBinding, private val onOpen: (Sticker) -> Unit) {

    val cards = mutableListOf<ItemTradingCardBinding>()
    private val pending = mutableListOf<Runnable>()
    private val revealed = mutableSetOf<Int>()
    private var stickers: List<Sticker> = emptyList()
    private var lines: Animator? = null
    private var pack: View? = null
    private val context get() = binding.root.context

    fun start(opened: List<Sticker>, inflater: LayoutInflater, pack: View) {
        stop()
        this.pack = pack
        stickers = opened
        revealed.clear()
        cards.clear()
        binding.openedCards.visibility = View.VISIBLE
        binding.openingHint.setText(R.string.album_skip_hint)
        binding.openingHint.visibility = View.VISIBLE
        binding.keepButton.visibility = View.GONE
        binding.openingAura.alpha = 0f
        startLines()
        binding.openingWord.popWord(R.string.pack_word_tear, R.color.accent)
        listOf(binding.cardsTop, binding.cardsBottom).forEach { it.removeAllViews() }
        opened.forEachIndexed { index, sticker ->
            val row = if (index < opened.size / 2) binding.cardsTop else binding.cardsBottom
            val card = ItemTradingCardBinding.inflate(inflater, row, false)
            row.addView(card.root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            cards += card
            card.bind(sticker, revealed = false)
            card.root.alpha = 0f
        }
        // Depois do layout: cada carta sabe onde fica e sai da "boca" do pacote até lá.
        binding.openedCards.post { cards.forEachIndexed { index, card -> card.root.dealFrom(pack, index) } }
        val dealt = opened.size * DEAL_STAGGER_MILLIS + RISE_MILLIS + SPREAD_MILLIS
        pack.animate().translationY(pack.height.toFloat()).alpha(0f).setStartDelay(opened.size * DEAL_STAGGER_MILLIS)
            .setDuration(PACK_EXIT_MILLIS).setInterpolator(AccelerateInterpolator())
            .withEndAction { pack.visibility = View.GONE }
        // Cada carta vira depois da anterior + o suspense da raridade dela.
        var at = dealt
        opened.forEachIndexed { index, sticker ->
            val suspense = sticker.suspense()
            schedule(at) { flip(index, suspense) }
            at += suspense + FLIP_IN_MILLIS + FLIP_OUT_MILLIS + FLIP_GAP_MILLIS
        }
        schedule(at) { finish() }
    }

    /** Toque durante a revelação: todas viram na hora. */
    fun skip() {
        if (pending.isEmpty()) return
        cancelPending()
        pack?.animate()?.cancel()
        pack?.visibility = View.GONE
        cards.forEachIndexed { index, card ->
            card.root.animate().cancel()
            card.root.translationX = 0f
            card.root.translationY = 0f
            card.root.rotation = 0f
            card.root.rotationY = 0f
            card.root.scaleX = 1f
            card.root.scaleY = 1f
            card.root.alpha = 1f
            if (index !in revealed) show(index)
        }
        finish()
    }

    fun stop() {
        cancelPending()
        lines?.cancel()
        lines = null
        binding.openingLines.alpha = 0f
    }

    private fun startLines() {
        val tint = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.accent), LINES_ALPHA)
        lines = binding.openingLines.startSpeedLines(SpeedLinesDrawable(tint))
        binding.openingLines.animate().alpha(1f).setDuration(FADE_MILLIS)
    }

    private fun schedule(delay: Long, action: () -> Unit) {
        val runnable = Runnable { action() }
        pending += runnable
        binding.root.postDelayed(runnable, delay)
    }

    private fun cancelPending() {
        pending.forEach(binding.root::removeCallbacks)
        pending.clear()
    }

    private fun flip(index: Int, suspense: Long) {
        val view = cards[index].root
        if (suspense > 0) view.shake()
        val sticker = stickers[index]
        if (sticker.golden || sticker.rarity == Rarity.LEGENDARY) {
            binding.openingAura.setRarity(sticker.rarity, sticker.golden)
            binding.openingAura.animate().alpha(1f).setDuration(suspense)
        }
        view.cameraDistance = CAMERA_DISTANCE * view.resources.displayMetrics.density
        // Levanta um pouco ao virar (profundidade) e assenta com mola.
        view.animate().rotationY(QUARTER_TURN).scaleX(FLIP_LIFT).scaleY(FLIP_LIFT).setStartDelay(suspense)
            .setDuration(FLIP_IN_MILLIS).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                show(index)
                view.rotationY = -QUARTER_TURN
                view.animate().rotationY(0f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(FLIP_OUT_MILLIS)
                    .setInterpolator(OvershootInterpolator(FLIP_TENSION))
            }
    }

    /** Frente da carta + selo NOVA!/×N; lendária e Divina ganham onomatopeia e vibração. */
    private fun show(index: Int) {
        revealed += index
        val card = cards[index]
        val sticker = stickers[index]
        card.bind(sticker, revealed = true, large = true)
        val copy = sticker.count - stickers.drop(index + 1).count { it.character.id == sticker.character.id }
        card.count.visibility = View.VISIBLE
        card.count.text = if (copy <= 1) {
            context.getString(R.string.album_new)
        } else {
            context.getString(R.string.album_count, copy)
        }
        card.root.setOnClickListener { onOpen(sticker) }
        val word = binding.openingWord
        when {
            sticker.golden -> word.popWord(R.string.pack_word_divine, R.color.rarity_divine)
            sticker.rarity == Rarity.LEGENDARY -> word.popWord(R.string.pack_word_legendary, R.color.rarity_legendary)
            else -> return
        }
        card.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    private fun finish() {
        pending.clear()
        binding.openingHint.visibility = View.GONE
        binding.keepButton.visibility = View.VISIBLE
        binding.keepButton.alpha = 0f
        binding.keepButton.animate().alpha(1f).setStartDelay(0).setDuration(FADE_MILLIS)
    }
}

private fun Sticker.suspense() = when {
    golden || rarity == Rarity.LEGENDARY -> LEGENDARY_SUSPENSE_MILLIS
    rarity == Rarity.RARE -> RARE_SUSPENSE_MILLIS
    else -> 0L
}

/**
 * Sai de dentro do [pack]: começa escondida na boca dele (pequena), sobe para fora e depois voa
 * até o lugar dela na grade, com mola.
 */
private fun View.dealFrom(pack: View, index: Int) {
    val here = IntArray(2).also(::getLocationOnScreen)
    val from = IntArray(2).also(pack::getLocationOnScreen)
    val dx = from[0] + pack.width / 2f - (here[0] + width / 2f)
    val dy = from[1] + pack.height * PACK_MOUTH - (here[1] + height / 2f)
    val rise = RISE_DP * resources.displayMetrics.density
    translationX = dx
    translationY = dy + rise
    scaleX = DEAL_START_SCALE
    scaleY = DEAL_START_SCALE
    rotation = (Random.nextFloat() * 2 - 1) * DEAL_SPIN
    animate().alpha(1f).translationY(dy - rise).setStartDelay(index * DEAL_STAGGER_MILLIS).setDuration(RISE_MILLIS)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(0)
                .setDuration(SPREAD_MILLIS).setInterpolator(OvershootInterpolator(SPREAD_TENSION))
        }
}

/** Onomatopeia estoura no meio da tela, segura e some. */
private fun TextView.popWord(@StringRes text: Int, @ColorRes color: Int) {
    setText(text)
    comicBox(BoxStyle.BURST, ContextCompat.getColor(context, color))
    animate().cancel()
    visibility = View.VISIBLE
    alpha = 0f
    scaleX = WORD_START_SCALE
    scaleY = WORD_START_SCALE
    rotation = if (Random.nextBoolean()) WORD_TILT else -WORD_TILT
    animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(WORD_POP_MILLIS)
        .setInterpolator(OvershootInterpolator())
        .withEndAction {
            animate().alpha(0f).setStartDelay(WORD_HOLD_MILLIS).setDuration(WORD_POP_MILLIS)
                .withEndAction { visibility = View.GONE }
        }
}
