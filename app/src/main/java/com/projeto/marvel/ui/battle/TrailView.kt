package com.projeto.marvel.ui.battle

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import com.projeto.marvel.R

/** Um adversário na trilha. [state]: já vencido, a luta atual ou ainda bloqueado. */
data class TrailNode(val name: String, val imageUrl: String?, val boss: Boolean, val state: State) {
    enum class State { DONE, CURRENT, LOCKED }
}

/**
 * Trilha de adversários estilo mapa de fases: avatares em zigue-zague ligados por uma linha
 * curva, do primeiro (embaixo) ao chefe (no topo, maior). Trecho vencido em linha cheia dourada,
 * o que falta tracejado. O atual pulsa.
 */
class TrailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val density = resources.displayMetrics.density
    private val nodeSize = (NODE_DP * density).toInt()
    private val bossSize = (BOSS_DP * density).toInt()
    private val rowHeight = (ROW_DP * density).toInt()
    private val labelHeight = (LABEL_DP * density).toInt()

    private val donePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINE_DP * density
        color = ContextCompat.getColor(context, R.color.accent)
        strokeCap = Paint.Cap.ROUND
    }
    private val todoPaint = Paint(donePaint).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        pathEffect = DashPathEffect(floatArrayOf(DASH_DP * density, DASH_DP * density), 0f)
    }

    private var nodes: List<TrailNode> = emptyList()

    // Reaproveitado a cada desenho (criar objetos em onDraw pesa na rolagem).
    private val path = Path()
    private val pulses = mutableListOf<ValueAnimator>()

    init {
        setWillNotDraw(false)
    }

    fun setNodes(nodes: List<TrailNode>) {
        this.nodes = nodes
        pulses.forEach { it.cancel() }
        pulses.clear()
        removeAllViews()
        nodes.forEach { node -> addView(avatar(node)); addView(label(node)) }
        requestLayout()
        invalidate()
    }

    private fun avatar(node: TrailNode) = ImageView(context).apply {
        val current = node.state == TrailNode.State.CURRENT
        setBackgroundResource(if (current) R.drawable.bg_trail_current else R.drawable.bg_circle)
        clipToOutline = true
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val padding = if (node.state == TrailNode.State.CURRENT) (RING_DP * density).toInt() else 0
        setPadding(padding, padding, padding, padding)
        load(node.imageUrl) { crossfade(true) }
        if (node.state == TrailNode.State.LOCKED) {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            alpha = LOCKED_ALPHA
        }
        if (node.state == TrailNode.State.CURRENT) {
            pulses += ObjectAnimator.ofFloat(this, View.SCALE_X, 1f, PULSE_SCALE, 1f).apply {
                duration = PULSE_MILLIS
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { scaleY = scaleX }
                start()
            }
        }
    }

    private fun label(node: TrailNode) = TextView(context).apply {
        text = when {
            node.state == TrailNode.State.DONE -> context.getString(R.string.trail_done, node.name)
            node.boss -> context.getString(R.string.battle_boss_name, node.name)
            else -> node.name
        }
        gravity = Gravity.CENTER
        maxLines = 1
        textSize = LABEL_SP
        setTextColor(
            ContextCompat.getColor(
                context,
                when (node.state) {
                    TrailNode.State.CURRENT -> R.color.accent
                    TrailNode.State.DONE -> R.color.text_primary
                    TrailNode.State.LOCKED -> R.color.text_secondary
                }
            )
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = nodes.size * rowHeight + bossSize
        for (i in 0 until childCount) {
            val node = nodes[i / 2]
            val size = if (node.boss) bossSize else nodeSize
            val child = getChildAt(i)
            if (i % 2 == 0) {
                val exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
                child.measure(exact, exact)
            } else {
                child.measure(
                    MeasureSpec.makeMeasureSpec(width / 2, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(labelHeight, MeasureSpec.EXACTLY)
                )
            }
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        nodes.indices.forEach { index ->
            val (cx, cy) = center(index)
            val avatar = getChildAt(index * 2)
            val label = getChildAt(index * 2 + 1)
            val half = avatar.measuredWidth / 2
            avatar.layout(cx - half, cy - half, cx + half, cy + half)
            val labelHalf = label.measuredWidth / 2
            val labelTop = cy + half
            label.layout(cx - labelHalf, labelTop, cx + labelHalf, labelTop + label.measuredHeight)
        }
    }

    /** Primeiro adversário embaixo, chefe no topo; x em zigue-zague (esquerda, centro, direita…). */
    private fun center(index: Int): Pair<Int, Int> {
        val column = ZIGZAG[index % ZIGZAG.size]
        val x = (width * column).toInt()
        val y = height - bossSize / 2 - index * rowHeight - labelHeight
        return x to y
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (index in 0 until nodes.size - 1) {
            val (x1, y1) = center(index)
            val (x2, y2) = center(index + 1)
            val midY = (y1 + y2) / 2f
            path.reset()
            path.moveTo(x1.toFloat(), y1.toFloat())
            path.cubicTo(x1.toFloat(), midY, x2.toFloat(), midY, x2.toFloat(), y2.toFloat())
            // O trecho até o nó atual já foi percorrido.
            val walked = nodes[index + 1].state != TrailNode.State.LOCKED
            canvas.drawPath(path, if (walked) donePaint else todoPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulses.forEach { it.cancel() }
    }

    private companion object {
        const val NODE_DP = 64
        const val BOSS_DP = 88
        const val ROW_DP = 112
        const val LABEL_DP = 22
        const val LINE_DP = 4f
        const val DASH_DP = 8f
        const val RING_DP = 4
        const val LABEL_SP = 13f
        const val LOCKED_ALPHA = 0.45f
        const val PULSE_SCALE = 1.1f
        const val PULSE_MILLIS = 900L
        val ZIGZAG = floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f)
    }
}
