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
                ContextCompat.getColor(context, R.color.text_primary),
                ContextCompat.getColor(context, R.color.move_water)
            )
        )
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
