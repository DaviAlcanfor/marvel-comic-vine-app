package com.projeto.marvel.ui.battle

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.projeto.marvel.R
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.era
import com.projeto.marvel.ui.eraDimen
import com.projeto.marvel.ui.eraOutline

/**
 * Fundo 3D da arena (ver [ArenaRenderer]). Observa o ciclo de vida da tela: só desenha enquanto
 * ela está visível (GL contínuo gastaria bateria à toa em segundo plano).
 */
// O contorno do ringue fica mais grosso que o dos cards: é visto de longe e em perspectiva.
private const val INK_SCALE = 1.5f

class ArenaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), DefaultLifecycleObserver {

    private val renderer = ArenaRenderer(arenaColors(context), context.eraDimen(R.attr.eraInkWidth) * INK_SCALE)

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Golpe forte: a câmera treme junto com a tela. */
    fun impact() = renderer.impact()

    override fun onResume(owner: LifecycleOwner) = onResume()

    override fun onPause(owner: LifecycleOwner) = onPause()
}

/**
 * Ringue no traço da época: cordas vermelho/branco/azul de gibi (Retrô; no escuro, nanquim claro),
 * neon magenta/ciano/amarelo (Anos 90) ou grafite com cordas vermelhas (Moderno).
 */
private fun arenaColors(context: Context): ArenaColors {
    fun color(res: Int) = ContextCompat.getColor(context, res)
    val ropes = when (context.era()) {
        Era.RETRO -> intArrayOf(color(R.color.primary), color(R.color.text_primary), color(R.color.move_water))
        Era.NINETIES ->
            intArrayOf(color(R.color.nineties_magenta), color(R.color.nineties_outline), color(R.color.logo_yellow))
        Era.MODERN -> intArrayOf(color(R.color.primary), color(R.color.primary_variant), color(R.color.text_secondary))
    }
    val post = when (context.era()) {
        Era.RETRO -> color(R.color.accent)
        Era.NINETIES -> color(R.color.nineties_outline)
        Era.MODERN -> color(R.color.surface)
    }
    return ArenaColors(
        background = color(R.color.background),
        floor = color(R.color.surface_variant),
        skirt = color(R.color.primary_variant),
        post = post,
        ropes = ropes,
        ink = context.eraOutline()
    )
}
