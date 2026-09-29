package com.projeto.marvel.ui.album

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val RAYS = 12
private const val SPARKS = 16
private const val CYCLE_MILLIS = 6_000L
private const val GLOW_ALPHA = 150
private const val RAY_ALPHA = 55
private const val PULSE = 0.08f
private const val SPARK_DP = 3f
private const val FULL_TURN = 360f
private const val TWINKLE_SPEED = 6
private const val TWINKLE_INDEX = 3

/**
 * Aura atrás da carta em 3D: brilho que pulsa na cor da raridade, raios de luz girando devagar e
 * faíscas subindo e piscando. Só anima enquanto está visível.
 */
@Suppress("MagicNumber") // geometria do desenho (frações do raio, ondas do brilho)
class AuraView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    @ColorInt private var color = Color.WHITE
    private var phase = 0f
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ray = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rays = Path()

    // Cada faísca: ângulo, distância (fração do raio), velocidade e fase do brilho.
    private val sparks = List(SPARKS) { FloatArray(4) { Random.nextFloat() } }

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MILLIS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    /** Cor da aura (metal da raridade / dourada). */
    fun setAuraColor(@ColorInt color: Int) {
        this.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f
        val pulse = 1f + PULSE * sin(phase * 2 * PI * 3).toFloat()

        glow.shader = RadialGradient(
            cx, cy, radius * pulse,
            intArrayOf(ColorUtils.setAlphaComponent(color, GLOW_ALPHA), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * pulse, glow)

        // Raios: fatias finas girando (uma volta a cada ciclo).
        ray.color = ColorUtils.setAlphaComponent(color, RAY_ALPHA)
        canvas.save()
        canvas.rotate(phase * FULL_TURN, cx, cy)
        rays.reset()
        repeat(RAYS) { i ->
            val a = (2 * PI * i / RAYS).toFloat()
            val b = a + (PI / RAYS / 2).toFloat()
            rays.moveTo(cx, cy)
            rays.lineTo(cx + cos(a) * radius, cy + sin(a) * radius)
            rays.lineTo(cx + cos(b) * radius, cy + sin(b) * radius)
            rays.close()
        }
        canvas.drawPath(rays, ray)
        canvas.restore()

        // Faíscas: sobem em espiral e piscam.
        val size = SPARK_DP * resources.displayMetrics.density
        sparks.forEach { spark4 ->
            val (angle, distance, speed) = spark4
            val twinkle = spark4[TWINKLE_INDEX]
            val t = (phase * (1 + speed) + angle) % 1f
            val a = (angle + t) * 2 * PI
            val d = radius * (0.35f + 0.6f * ((distance + t) % 1f))
            val alpha = (sin((phase * TWINKLE_SPEED + twinkle) * 2 * PI) * 0.5 + 0.5).toFloat()
            spark.color = ColorUtils.setAlphaComponent(Color.WHITE, (alpha * 255).toInt())
            canvas.drawCircle(cx + (cos(a) * d).toFloat(), cy + (sin(a) * d).toFloat(), size * alpha + 1, spark)
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
