package com.projeto.marvel.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import com.projeto.marvel.R
import org.xmlpull.v1.XmlPullParser

/**
 * Peça de HQ desenhada no traço da época ([Era]) — usada direto no XML:
 * `<com.projeto.marvel.ui.EraPanelDrawable app:panelKind="button" />`.
 *
 * - Retrô: retângulo com nanquim grosso e sombra dura deslocada (pressionado, a face afunda).
 * - Anos 90: cantos chanfrados; quadro com moldura neon em degradê, botão magenta com filete
 *   amarelo, legenda inclinada (etiqueta).
 * - Moderno: cantos arredondados, traço fino, sem sombra.
 *
 * O preenchimento padrão vem do tipo ([Kind]); [setFill] troca (legendas coloridas por personagem).
 */
@Suppress("TooManyFunctions") // um Drawable de verdade: os overrides (estado, tema, contorno, padding) são obrigatórios
class EraPanelDrawable() : Drawable() {

    enum class Kind { PANEL, BUTTON, SECONDARY, CAPTION, INPUT, NAV, NAV_ITEM, CHAMFER_FILL }

    /** Feita em código (ex.: legenda do [comicBox]), já no tema de [context]. */
    constructor(context: android.content.Context, kind: Kind, @ColorInt fill: Int? = null) : this() {
        this.kind = kind
        density = context.resources.displayMetrics.density
        fill?.let { fillColor = it; fillSet = true }
        applyTheme(context.theme)
    }

    private var kind = Kind.PANEL
    private var era = Era.RETRO
    private var density = 1f
    private var fillColor = 0
    private var fillSet = false
    private var outlineColor = 0
    private var inkColor = 0
    private var shadowColor = 0
    private var accentColor = 0
    private var neon = intArrayOf()
    private var pressed = false
    private var checked = false
    private var gradient: IntArray? = null
    private var enabled = true

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.MITER
    }
    private val path = Path()
    private val box = RectF()

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Resources.Theme?) {
        super.inflate(r, parser, attrs, theme)
        val values = if (theme != null) {
            theme.obtainStyledAttributes(attrs, R.styleable.EraPanelDrawable, 0, 0)
        } else {
            r.obtainAttributes(attrs, R.styleable.EraPanelDrawable)
        }
        kind = Kind.entries[values.getInt(R.styleable.EraPanelDrawable_panelKind, 0)]
        if (values.hasValue(R.styleable.EraPanelDrawable_panelFill)) {
            fillColor = values.getColor(R.styleable.EraPanelDrawable_panelFill, 0)
            fillSet = true
        }
        values.recycle()
        density = r.displayMetrics.density
        theme?.let(::applyTheme)
    }

    override fun canApplyTheme() = true

    override fun applyTheme(t: Resources.Theme) {
        super.applyTheme(t)
        val value = TypedValue()
        if (t.resolveAttribute(R.attr.eraStyle, value, true)) era = Era.entries.getOrElse(value.data) { Era.RETRO }
        fun color(res: Int) = ResourcesCompat.getColor(t.resources, res, t)
        outlineColor = if (t.resolveAttribute(R.attr.eraOutline, value, true)) value.data else color(R.color.ink)
        inkColor = color(R.color.ink)
        shadowColor = inkColor
        accentColor = color(R.color.logo_yellow)
        neon = intArrayOf(color(R.color.nineties_outline), color(R.color.nineties_magenta), color(R.color.logo_yellow))
        if (!fillSet) fillColor = defaultFill(::color)
        invalidateSelf()
    }

    private fun defaultFill(color: (Int) -> Int): Int = color(
        when (kind) {
            Kind.PANEL, Kind.SECONDARY -> R.color.surface
            Kind.BUTTON -> R.color.primary
            Kind.CAPTION -> CAPTION_FILL.getValue(era)
            Kind.INPUT -> if (era == Era.NINETIES) R.color.nineties_input else R.color.surface
            Kind.NAV -> NAV_FILL.getValue(era)
            Kind.NAV_ITEM -> R.color.logo_yellow
            Kind.CHAMFER_FILL -> R.color.surface
        }
    ).let { if (kind == Kind.SECONDARY && era == Era.NINETIES) 0 else it }

    /** Preenchimento em degradê diagonal (metal do pacote nos Anos 90). */
    fun setGradient(colors: IntArray) {
        gradient = colors
        invalidateSelf()
    }

    /** Troca o preenchimento (ex.: legenda na cor do personagem). */
    fun setFill(@ColorInt color: Int) {
        fillColor = color
        fillSet = true
        invalidateSelf()
    }

    private val shadow
        get() = if (era == Era.RETRO && kind != Kind.NAV && kind != Kind.INPUT) RETRO_SHADOW_DP * density else 0f
    private val ink get() = when (era) {
        Era.RETRO -> RETRO_INK_DP * density
        Era.NINETIES -> NINETIES_INK_DP * density
        Era.MODERN -> MODERN_INK_DP * density
    }

    override fun draw(canvas: Canvas) {
        if (kind == Kind.NAV) return drawNav(canvas)
        if (kind == Kind.NAV_ITEM) return drawNavItem(canvas)
        val shift = if (pressed && era == Era.RETRO) shadow else 0f
        box.set(bounds.left + shift, bounds.top + shift, bounds.right - shadow + shift, bounds.bottom - shadow + shift)
        shape(box)
        if (shadow > 0f && !pressed) {
            canvas.save()
            canvas.translate(shadow, shadow)
            fill.shader = null
            fill.color = shadowColor
            canvas.drawPath(path, fill)
            canvas.restore()
        }
        paintFill()
        canvas.drawPath(path, fill)
        fill.shader = null
        paintStroke()
        if (stroke.strokeWidth > 0f) canvas.drawPath(path, stroke)
        stroke.shader = null
    }

    /** Contorno da peça em [rect], conforme a época e o tipo. */
    private fun shape(rect: RectF) {
        path.reset()
        when (era) {
            Era.RETRO -> path.addRect(rect, Path.Direction.CW)
            Era.NINETIES -> when (kind) {
                Kind.CAPTION -> path.parallelogram(rect)
                else -> path.chamfer(rect, CHAMFER_DP * density)
            }
            Era.MODERN -> {
                val radius = when (kind) {
                    Kind.CAPTION -> LABEL_RADIUS_DP
                    Kind.BUTTON, Kind.SECONDARY -> BUTTON_RADIUS_DP
                    else -> PANEL_RADIUS_DP
                } * density
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
        }
    }

    private fun paintFill() {
        fill.color = if (!enabled) disabledFill() else fillColor
        if (pressed && era != Era.RETRO) fill.color = ColorUtils.blendARGB(fill.color, Color.BLACK, PRESS_DARKEN)
        gradient?.let {
            fill.shader = LinearGradient(box.left, box.top, box.right, box.bottom, it, null, Shader.TileMode.CLAMP)
        }
        if (era == Era.NINETIES && kind == Kind.BUTTON && enabled) {
            val top = ColorUtils.blendARGB(fill.color, Color.WHITE, BUTTON_SHINE)
            fill.shader = LinearGradient(0f, box.top, 0f, box.bottom, top, fill.color, Shader.TileMode.CLAMP)
        }
    }

    private fun paintStroke() {
        stroke.strokeWidth = ink
        stroke.color = outlineColor
        when {
            era == Era.NINETIES && kind == Kind.PANEL -> {
                stroke.strokeWidth = NINETIES_FRAME_DP * density
                stroke.shader =
                    LinearGradient(box.left, box.top, box.right, box.bottom, neon, null, Shader.TileMode.CLAMP)
            }
            era == Era.NINETIES && (kind == Kind.BUTTON || kind == Kind.CAPTION) -> stroke.color = accentColor
            era == Era.MODERN && (kind == Kind.BUTTON || kind == Kind.CAPTION) -> stroke.strokeWidth = 0f
            kind == Kind.CHAMFER_FILL && era == Era.NINETIES -> stroke.strokeWidth = 0f
            era == Era.RETRO -> stroke.color = outlineColor
        }
    }

    private fun disabledFill() = ColorUtils.setAlphaComponent(fillColor, DISABLED_ALPHA)

    /** Retrô: placa amarela de nanquim, meio torta, atrás do item ativo da barra. */
    private fun drawNavItem(canvas: Canvas) {
        if (!checked || era != Era.RETRO) return
        val inset = NAV_ITEM_INSET_DP * density
        val shadow = NAV_ITEM_SHADOW_DP * density
        box.set(bounds.left + inset, bounds.top + inset, bounds.right - inset - shadow, bounds.bottom - inset - shadow)
        canvas.save()
        canvas.rotate(NAV_ITEM_TILT, box.centerX(), box.centerY())
        fill.shader = null
        fill.color = inkColor
        canvas.drawRect(box.left + shadow, box.top + shadow, box.right + shadow, box.bottom + shadow, fill)
        fill.color = fillColor
        canvas.drawRect(box, fill)
        stroke.shader = null
        stroke.color = inkColor
        stroke.strokeWidth = ink
        canvas.drawRect(box, stroke)
        canvas.restore()
    }

    private fun drawNav(canvas: Canvas) {
        fill.color = fillColor
        canvas.drawRect(bounds, fill)
        val line = when (era) {
            Era.RETRO -> RETRO_NAV_LINE_DP
            Era.NINETIES -> NINETIES_NAV_LINE_DP
            Era.MODERN -> MODERN_INK_DP
        } * density
        fill.color = if (era == Era.RETRO) inkColor else outlineColor
        canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.top + line, fill)
    }

    override fun getOutline(outline: Outline) {
        if (bounds.isEmpty) return
        box.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right - shadow, bounds.bottom - shadow)
        shape(box)
        // Retrô: o recorte inclui a sombra (ela fica fora da face, mas dentro da View).
        val square = era == Era.RETRO || kind == Kind.NAV || kind == Kind.NAV_ITEM
        if (square) outline.setRect(bounds) else outline.setPath(path)
    }

    /** Espaço para o traço e a sombra: o conteúdo (foto do quadro, texto do botão) não cobre a borda. */
    override fun getPadding(padding: Rect): Boolean {
        if (kind in NO_PADDING) return false
        val edge = if (kind == Kind.PANEL && era == Era.NINETIES) NINETIES_FRAME_DP * density else ink
        val inset = edge.toInt()
        padding.set(inset, inset, inset + shadow.toInt(), inset + shadow.toInt())
        return true
    }

    override fun isStateful() = kind == Kind.BUTTON || kind == Kind.SECONDARY || kind == Kind.NAV_ITEM

    override fun onStateChange(state: IntArray): Boolean {
        val nowPressed = android.R.attr.state_pressed in state
        val nowEnabled = android.R.attr.state_enabled in state
        val nowChecked = android.R.attr.state_checked in state || android.R.attr.state_selected in state
        if (nowPressed == pressed && nowEnabled == enabled && nowChecked == checked) return false
        pressed = nowPressed
        enabled = nowEnabled
        checked = nowChecked
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val RETRO_SHADOW_DP = 4f
        const val RETRO_INK_DP = 2.5f
        const val NINETIES_INK_DP = 1.5f
        const val NINETIES_FRAME_DP = 3f
        const val MODERN_INK_DP = 1f
        const val RETRO_NAV_LINE_DP = 3f
        const val NINETIES_NAV_LINE_DP = 2f
        const val CHAMFER_DP = 12f
        const val LABEL_RADIUS_DP = 3f
        const val BUTTON_RADIUS_DP = 6f
        const val PANEL_RADIUS_DP = 10f
        val NO_PADDING = setOf(Kind.NAV, Kind.CAPTION, Kind.NAV_ITEM, Kind.CHAMFER_FILL)
        const val NAV_ITEM_INSET_DP = 6f
        const val NAV_ITEM_SHADOW_DP = 3f
        const val NAV_ITEM_TILT = -3f
        const val DISABLED_ALPHA = 0x73
        const val PRESS_DARKEN = 0.2f
        const val BUTTON_SHINE = 0.25f
        val CAPTION_FILL = mapOf(
            Era.RETRO to R.color.caption_yellow,
            Era.NINETIES to R.color.nineties_magenta,
            Era.MODERN to R.color.primary
        )
        val NAV_FILL = mapOf(
            Era.RETRO to R.color.scrim,
            Era.NINETIES to R.color.nineties_nav,
            Era.MODERN to R.color.modern_nav
        )
    }
}

private const val SKEW = 0.25f

/** Retângulo com dois cantos opostos cortados (Anos 90). */
private fun Path.chamfer(rect: RectF, cut: Float) {
    moveTo(rect.left + cut, rect.top)
    lineTo(rect.right, rect.top)
    lineTo(rect.right, rect.bottom - cut)
    lineTo(rect.right - cut, rect.bottom)
    lineTo(rect.left, rect.bottom)
    lineTo(rect.left, rect.top + cut)
    close()
}

/** Etiqueta inclinada (Anos 90). */
private fun Path.parallelogram(rect: RectF) {
    val lean = rect.height() * SKEW
    moveTo(rect.left + lean, rect.top)
    lineTo(rect.right, rect.top)
    lineTo(rect.right - lean, rect.bottom)
    lineTo(rect.left, rect.bottom)
    close()
}
