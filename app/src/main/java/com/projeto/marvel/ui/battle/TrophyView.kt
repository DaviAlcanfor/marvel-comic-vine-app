package com.projeto.marvel.ui.battle

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.util.AttributeSet
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.projeto.marvel.R

/**
 * Troféu 3D girando (ver [TrophyRenderer]) com fundo transparente dentro do painel de vitória.
 *
 * É TextureView, não GLSurfaceView: o SurfaceView é desenhado fora da árvore de Views (uma camada
 * à parte), então não respeitava a transparência nem o alfa do painel e aparecia como um quadrado.
 * A TextureView compõe como uma View comum; o preço é cuidar do EGL e do laço de desenho aqui.
 */
class TrophyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private val renderer = TrophyRenderer(gold = ContextCompat.getColor(context, R.color.accent), clearColor = 0)
    private var texture: SurfaceTexture? = null
    private var size = 0 to 0
    private var thread: RenderThread? = null
    private var running = false

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    /** Começa a girar (quando o painel aparece). */
    fun start() {
        running = true
        startThread()
    }

    /** Para de desenhar (painel escondido / tela saindo). */
    fun stop() {
        running = false
        thread?.quit()
        thread = null
    }

    private fun startThread() {
        val surface = texture ?: return
        if (!running || thread != null) return
        thread = RenderThread(surface, size.first, size.second, renderer).also { it.start() }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        texture = surface
        size = width to height
        startThread()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        size = width to height
        thread?.resize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        thread?.quit()
        thread = null
        texture = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    /** Laço de desenho: contexto EGL próprio sobre a SurfaceTexture, ~60 quadros por segundo. */
    private class RenderThread(
        private val surface: SurfaceTexture,
        @Volatile private var width: Int,
        @Volatile private var height: Int,
        private val renderer: TrophyRenderer
    ) : Thread("trophy-gl") {

        @Volatile private var alive = true
        @Volatile private var resized = true

        fun quit() {
            alive = false
        }

        fun resize(width: Int, height: Int) {
            this.width = width
            this.height = height
            resized = true
        }

        override fun run() {
            val egl = Egl(surface)
            renderer.onSurfaceCreated(null, null)
            while (alive) {
                if (resized) {
                    resized = false
                    renderer.onSurfaceChanged(null, width, height)
                }
                renderer.onDrawFrame(null)
                egl.swap()
                sleep(FRAME_MILLIS)
            }
            egl.release()
        }
    }

    /** O mínimo de EGL14: display, config RGBA8888 + profundidade, contexto ES 2 e janela. */
    private class Egl(surface: SurfaceTexture) {
        private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: EGLContext
        private val window: EGLSurface

        init {
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            EGL14.eglChooseConfig(display, CONFIG_ATTRIBUTES, 0, configs, 0, 1, count, 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, CONTEXT_ATTRIBUTES, 0)
            window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, window, window, context)
        }

        fun swap() {
            EGL14.eglSwapBuffers(display, window)
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, window)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val CHANNEL_BITS = 8
        const val DEPTH_BITS = 16
        const val ES2_VERSION = 2

        val CONFIG_ATTRIBUTES = intArrayOf(
            EGL14.EGL_RED_SIZE, CHANNEL_BITS,
            EGL14.EGL_GREEN_SIZE, CHANNEL_BITS,
            EGL14.EGL_BLUE_SIZE, CHANNEL_BITS,
            EGL14.EGL_ALPHA_SIZE, CHANNEL_BITS,
            EGL14.EGL_DEPTH_SIZE, DEPTH_BITS,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val CONTEXT_ATTRIBUTES = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, ES2_VERSION, EGL14.EGL_NONE)
    }
}
