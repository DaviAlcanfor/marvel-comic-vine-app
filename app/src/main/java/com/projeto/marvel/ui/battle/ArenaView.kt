package com.projeto.marvel.ui.battle

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.projeto.marvel.R

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

    private val renderer = ArenaRenderer(
        ArenaColors(
            background = ContextCompat.getColor(context, R.color.background),
            floor = ContextCompat.getColor(context, R.color.surface_variant),
            skirt = ContextCompat.getColor(context, R.color.primary_variant),
            post = ContextCompat.getColor(context, R.color.accent),
            ropes = intArrayOf(
                ContextCompat.getColor(context, R.color.primary),
                ContextCompat.getColor(context, R.color.white),
                ContextCompat.getColor(context, R.color.move_water)
            ),
            ink = ContextCompat.getColor(context, R.color.ink)
        ),
        inkWidth = context.resources.getDimension(R.dimen.ink_width) * INK_SCALE
    )

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
