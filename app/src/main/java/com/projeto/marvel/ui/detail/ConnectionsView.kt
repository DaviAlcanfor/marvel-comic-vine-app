package com.projeto.marvel.ui.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.annotation.ColorRes
import com.projeto.marvel.R
import com.projeto.marvel.ui.eraDimen
import com.projeto.marvel.ui.eraFont
import com.projeto.marvel.ui.eraOutline
import kotlin.math.abs

/** Tipo de ligação: define a cor da bolinha (e a legenda no título). */
enum class Link(@ColorRes val color: Int) {
    HERO(R.color.caption_yellow),
    TEAM(R.color.move_water),
    FRIEND(R.color.move_heal),
    ENEMY(R.color.move_strike),
    CREATOR(R.color.move_magic)
}

data class GraphNode(val label: String, val link: Link, val url: String?)

/**
 * Teia de conexões em Canvas: o personagem no centro, times/aliados/inimigos/criadores em volta,
 * ligados por traços de nanquim da época. Arrastar move um nó, pinça dá zoom, tocar abre.
 */
@SuppressLint("ViewConstructor")
class ConnectionsView(
    context: Context,
    private val nodes: List<GraphNode>,
    private val onOpen: (GraphNode) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val radius = NODE_DP * density
    private val layout = ForceLayout(nodes.size, REST_DP * density)
    private var zoom = 1f
    private var userZoomed = false
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.eraOutline()
        strokeWidth = context.eraDimen(R.attr.eraInkWidth).coerceAtLeast(density)
        style = Paint.Style.STROKE
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    // Contorno da cor do papel atrás do nome: lê por cima das linhas da teia.
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.surface)
        textSize = LABEL_SP * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = context.eraFont(R.attr.eraBodyFont)
        isFakeBoldText = true
        style = Paint.Style.STROKE
        strokeWidth = HALO_DP * density
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = LABEL_SP * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = context.eraFont(R.attr.eraBodyFont)
        isFakeBoldText = true
    }

    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                userZoomed = true
                moved = true
                invalidate()
                return true
            }
        }
    )

    // Dentro do diálogo a altura vem "até X": sem pedir uma, a View ocupa tudo e empurra os botões.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = (resources.displayMetrics.heightPixels * HEIGHT_SHARE).toInt()
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(wanted, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val settling = layout.step() > REST_ENERGY || layout.pinned >= 0
        if (!userZoomed) zoom = fitZoom()
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(zoom, zoom)
        for (i in 1 until layout.count) canvas.drawLine(layout.x[0], layout.y[0], layout.x[i], layout.y[i], ink)
        nodes.forEachIndexed { i, node ->
            val r = if (i == 0) radius * HERO_SCALE else radius
            fill.color = context.getColor(node.link.color)
            canvas.drawCircle(layout.x[i], layout.y[i], r, fill)
            canvas.drawCircle(layout.x[i], layout.y[i], r, ink)
            val label = node.label.take(MAX_LABEL)
            canvas.drawText(label, layout.x[i], layout.y[i] + r + text.textSize, halo)
            canvas.drawText(label, layout.x[i], layout.y[i] + r + text.textSize, text)
        }
        canvas.restore()
        if (settling) postInvalidateOnAnimation()
    }

    /** Zoom que cabe a teia inteira (com os nomes) na tela, até o tamanho natural. */
    private fun fitZoom(): Float {
        if (width == 0 || height == 0) return 1f
        val margin = radius + text.textSize * LABEL_ROOM
        val spanX = (0 until layout.count).maxOf { kotlin.math.abs(layout.x[it]) } + margin
        val spanY = (0 until layout.count).maxOf { kotlin.math.abs(layout.y[it]) } + margin + text.textSize
        return minOf(1f, width / 2f / spanX, height / 2f / spanY).coerceAtLeast(MIN_ZOOM)
    }

    @SuppressLint("ClickableViewAccessibility") // a teia é um extra visual; o Detalhe já lista tudo
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        val wx = (event.x - width / 2f) / zoom
        val wy = (event.y - height / 2f) / zoom
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                layout.pinned = layout.nodeAt(wx, wy, radius * TOUCH_SLOP).takeIf { it > 0 } ?: -1
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if (!scaler.isInProgress) drag(event, wx, wy)
            MotionEvent.ACTION_UP -> {
                if (!moved) layout.nodeAt(wx, wy, radius * TOUCH_SLOP).takeIf { it >= 0 }?.let { onOpen(nodes[it]) }
                layout.pinned = -1
            }
            MotionEvent.ACTION_CANCEL -> layout.pinned = -1
        }
        invalidate()
        return true
    }

    private fun drag(event: MotionEvent, wx: Float, wy: Float) {
        if (abs(event.x - downX) + abs(event.y - downY) > radius / 2) moved = true
        if (layout.pinned > 0) {
            layout.x[layout.pinned] = wx
            layout.y[layout.pinned] = wy
        }
    }

    private companion object {
        const val NODE_DP = 14f
        const val REST_DP = 110f
        const val HERO_SCALE = 1.6f
        const val LABEL_SP = 11f
        const val MAX_LABEL = 18
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 2.5f
        const val TOUCH_SLOP = 1.8f
        const val REST_ENERGY = 0.01f
        const val HALO_DP = 3f
        const val HEIGHT_SHARE = 0.45f
        const val LABEL_ROOM = 3f
    }
}
