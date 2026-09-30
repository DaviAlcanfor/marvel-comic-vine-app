package com.projeto.marvel.ui.album

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.projeto.marvel.R
import com.projeto.marvel.data.Rarity
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Aura por raridade: brasas (comum), reflexos orbitando (rara), raios (lendária), arco-íris (Divina). */
enum class AuraStyle { COMMON, RARE, LEGENDARY, DIVINE }

fun auraStyle(rarity: Rarity, divine: Boolean) = when {
    divine -> AuraStyle.DIVINE
    rarity == Rarity.LEGENDARY -> AuraStyle.LEGENDARY
    rarity == Rarity.RARE -> AuraStyle.RARE
    else -> AuraStyle.COMMON
}

private const val CYCLE_MILLIS = 6_000L
private const val PARTICLES = 14
private const val GLINTS = 3
private const val TAIL = 6
private const val SPIKES = 18
private const val RIPPLES = 2
private const val SPARKLES = 8
private const val FULL_ALPHA = 255
private const val HUE_MAX = 360f
private const val SEED_FIELDS = 3

/**
 * Aura abstrata em volta de uma carta ou lutador, sem círculo fixo: partículas e raios que mudam
 * por raridade. Centro: o meio da própria View, ou o de [follow] (lutador na arena, que pula e
 * avança — a aura vai junto, lendo a posição dele a cada quadro). [compact]: só brilhos dentro dos
 * limites (Divina na grade do álbum). Só anima enquanto está visível.
 */
@Suppress("MagicNumber") // geometria do desenho (frações do raio, velocidades, ondas)
class AuraView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var style = AuraStyle.COMMON
    private var color = Color.WHITE
    private var target: View? = null
    var compact = false
    private var phase = 0f
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val hsv = floatArrayOf(0f, 0.65f, 1f)

    // Cada partícula: ângulo, velocidade e fase (0..1), sorteados uma vez.
    private val seeds = List(PARTICLES) { FloatArray(SEED_FIELDS) { Random.nextFloat() } }

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MILLIS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    fun setRarity(rarity: Rarity, divine: Boolean) {
        style = auraStyle(rarity, divine)
        val res = when (rarity) {
            Rarity.COMMON -> R.color.card_common_light
            Rarity.RARE -> R.color.card_rare_light
            Rarity.LEGENDARY -> R.color.card_legendary_light
        }
        color = ContextCompat.getColor(context, res)
        invalidate()
    }

    /** Centraliza a aura em [view] (irmã no mesmo pai), acompanhando posição, escala e opacidade. */
    fun follow(view: View) {
        target = view
    }

    override fun onDraw(canvas: Canvas) {
        val followed = target
        val cx: Float
        val cy: Float
        val r: Float
        if (followed != null) {
            if (!followed.isShown) return
            cx = followed.x - x + followed.width / 2f
            cy = followed.y - y + followed.height / 2f
            r = followed.width * followed.scaleX / 2f
            canvas.saveLayerAlpha(null, (followed.alpha * FULL_ALPHA).toInt())
        } else {
            cx = width / 2f
            cy = height / 2f
            r = minOf(width, height) / if (compact) 2f else 3.2f
            canvas.save()
        }
        when {
            compact -> sparkles(canvas, cx, cy, r, inside = true)
            style == AuraStyle.COMMON -> embers(canvas, cx, cy, r)
            style == AuraStyle.RARE -> {
                embers(canvas, cx, cy, r)
                glints(canvas, cx, cy, r)
            }
            style == AuraStyle.LEGENDARY -> {
                spikes(canvas, cx, cy, r, rainbow = false)
                embers(canvas, cx, cy, r)
            }
            else -> {
                ripples(canvas, cx, cy, r)
                spikes(canvas, cx, cy, r, rainbow = true)
                sparkles(canvas, cx, cy, r, inside = false)
            }
        }
        canvas.restore()
    }

    /** Brasas: sobem devagar em volta, acendendo e apagando. */
    private fun embers(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        seeds.forEach { (angle, speed, offset) ->
            val t = (phase * (1 + speed * 2) + offset) % 1f
            val a = angle * 2 * PI + sin((phase + offset) * 2 * PI) * 0.15
            val d = r * (1.05f + 0.35f * t)
            val alpha = sin(t * PI).toFloat()
            fill.color = ColorUtils.setAlphaComponent(color, (alpha * 220).toInt())
            val px = cx + (cos(a) * d).toFloat()
            val py = cy + (sin(a) * d).toFloat() - t * r * 0.5f
            canvas.drawCircle(px, py, (1.5f + 2.5f * speed) * density * alpha + 0.5f, fill)
        }
    }

    /** Reflexos: estrelinhas de 4 pontas em órbitas elípticas, com rastro. */
    private fun glints(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        repeat(GLINTS) { i ->
            repeat(TAIL) { step ->
                val a = (phase * (1 + i * 0.35f) - step * 0.012f) * 2 * PI + i * 2 * PI / GLINTS
                val px = cx + (cos(a) * r * 1.3f).toFloat()
                val py = cy + (sin(a) * r * 0.95f).toFloat()
                val fade = 1f - step / TAIL.toFloat()
                fill.color = ColorUtils.setAlphaComponent(if (step == 0) Color.WHITE else color, (fade * 230).toInt())
                if (step == 0) {
                    canvas.drawPath(path.star(px, py, 7 * density), fill)
                } else {
                    canvas.drawCircle(px, py, 2.5f * density * fade, fill)
                }
            }
        }
    }

    /** Raios de HQ: pontas finas em volta, girando devagar e pulsando cada uma no seu tempo. */
    private fun spikes(canvas: Canvas, cx: Float, cy: Float, r: Float, rainbow: Boolean) {
        val inner = r * 1.08f
        repeat(SPIKES) { i ->
            val a = i * 2 * PI / SPIKES + phase * PI / 2
            val half = PI / SPIKES / 3
            val pulse = abs(sin((phase * 3 + seeds[i % PARTICLES][2]) * 2 * PI)).toFloat()
            val outer = inner + r * (0.18f + 0.32f * pulse)
            fill.color = if (rainbow) {
                hsv.hue((i.toFloat() / SPIKES + phase) % 1f, 200)
            } else {
                ColorUtils.setAlphaComponent(color, 190)
            }
            path.reset()
            path.moveTo(cx + (cos(a - half) * inner).toFloat(), cy + (sin(a - half) * inner).toFloat())
            path.lineTo(cx + (cos(a) * outer).toFloat(), cy + (sin(a) * outer).toFloat())
            path.lineTo(cx + (cos(a + half) * inner).toFloat(), cy + (sin(a + half) * inner).toFloat())
            path.close()
            canvas.drawPath(path, fill)
        }
    }

    /** Ondas que se abrem e somem, trocando de cor (Divina). */
    private fun ripples(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        stroke.strokeWidth = 3 * density
        repeat(RIPPLES) { i ->
            val t = (phase * 2 + i.toFloat() / RIPPLES) % 1f
            stroke.color = hsv.hue((phase + t) % 1f, ((1 - t) * 200).toInt())
            canvas.drawCircle(cx, cy, r * (1.05f + 0.6f * t), stroke)
        }
    }

    /** Brilhos de 4 pontas piscando: em volta ([inside] = false) ou por cima da carta (grade). */
    private fun sparkles(canvas: Canvas, cx: Float, cy: Float, r: Float, inside: Boolean) {
        repeat(SPARKLES) { i ->
            val (angle, distance, offset) = seeds[i]
            val twinkle = sin((phase * 4 + offset) * 2 * PI).toFloat()
            if (twinkle <= 0f) return@repeat
            val a = angle * 2 * PI
            val d = if (inside) r * distance * 0.9f else r * (1.1f + 0.4f * distance)
            fill.color = hsv.hue((offset + phase) % 1f, (twinkle * FULL_ALPHA).toInt())
            val px = cx + (cos(a) * d).toFloat()
            val py = cy + (sin(a) * d).toFloat()
            canvas.drawPath(path.star(px, py, 8 * density * twinkle), fill)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) clock.start() else clock.cancel()
    }

    override fun onDetachedFromWindow() {
        clock.cancel()
        super.onDetachedFromWindow()
    }
}

/** Estrela de 4 pontas (brilho) centrada em [x], [y]; reaproveita o Path. */
private fun Path.star(x: Float, y: Float, size: Float): Path {
    val waist = size / STAR_WAIST
    reset()
    moveTo(x, y - size)
    lineTo(x + waist, y - waist)
    lineTo(x + size, y)
    lineTo(x + waist, y + waist)
    lineTo(x, y + size)
    lineTo(x - waist, y + waist)
    lineTo(x - size, y)
    lineTo(x - waist, y - waist)
    close()
    return this
}

private const val STAR_WAIST = 4

/** Cor do arco-íris na posição [fraction] (0..1), reaproveitando o array HSV. */
private fun FloatArray.hue(fraction: Float, alpha: Int): Int {
    this[0] = fraction * HUE_MAX
    return ColorUtils.setAlphaComponent(Color.HSVToColor(this), alpha.coerceIn(0, FULL_ALPHA))
}
