package com.projeto.marvel.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.ContextThemeWrapper
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatDelegate
import com.projeto.marvel.R
import com.projeto.marvel.data.ThemeStore
import com.projeto.marvel.data.overlayFor
import com.projeto.marvel.ui.Era
import com.projeto.marvel.ui.era
import com.projeto.marvel.ui.eraDimen
import com.projeto.marvel.ui.eraFont
import com.projeto.marvel.ui.eraOutline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Abre o seletor do sistema já com o papel de parede do herói do dia. */
fun liveWallpaperIntent(context: Context): Intent =
    Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
        android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
        ComponentName(context, HeroWallpaperService::class.java)
    )

/**
 * Papel de parede animado: o herói do dia (o mesmo da Início) num quadro de HQ, com linhas de ação
 * girando devagar e retícula, nas cores e no traço da época escolhida no Perfil.
 */
class HeroWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HeroEngine()

    /** O serviço não tem a Activity: monta o tema da época (modo noturno + sobreposição) na mão. */
    private fun eraContext(): Context {
        val mode = ThemeStore(this).get()
        val systemNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val night = when (mode.nightMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> systemNight
        }
        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(createConfigurationContext(config), R.style.Theme_Marvel).apply {
            mode.overlayFor(night)?.let { theme.applyStyle(it, true) }
        }
    }

    private inner class HeroEngine : Engine() {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val handler = Handler(Looper.getMainLooper())
        private val frame = Runnable { draw() }
        private var visible = false
        private var tick = 0
        private var hero: Pair<String, Bitmap?>? = null
        private lateinit var themed: Context
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            themed = eraContext()
            scope.launch {
                hero = loadHeroOfTheDay(applicationContext)
                    ?.let { (character, image) -> character.name.orEmpty() to image }
                draw()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                themed = eraContext()
                draw()
            } else {
                handler.removeCallbacks(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            scope.cancel()
            super.onDestroy()
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                paintScene(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            tick++
            handler.removeCallbacks(frame)
            if (visible) handler.postDelayed(frame, FRAME_MS)
        }

        private fun paintScene(canvas: Canvas) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            canvas.drawColor(themed.getColor(R.color.background))
            speedLines(canvas, w, h)
            if (themed.era() != Era.MODERN) halftone(canvas, w, h)
            heroPanel(canvas, w, h)
        }

        private fun speedLines(canvas: Canvas, w: Float, h: Float) {
            paint.style = Paint.Style.FILL
            paint.color = themed.getColor(R.color.speed_lines)
            val turn = tick * SPIN
            val reach = w + h
            for (i in 0 until RAYS step 2) {
                val a1 = turn + 2 * PI * i / RAYS
                val a2 = turn + 2 * PI * (i + 1) / RAYS
                val path = android.graphics.Path().apply {
                    moveTo(w / 2, h / 2)
                    lineTo(w / 2 + (reach * cos(a1)).toFloat(), h / 2 + (reach * sin(a1)).toFloat())
                    lineTo(w / 2 + (reach * cos(a2)).toFloat(), h / 2 + (reach * sin(a2)).toFloat())
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }

        private fun halftone(canvas: Canvas, w: Float, h: Float) {
            paint.color = themed.getColor(R.color.halftone)
            val step = DOT_STEP * themed.resources.displayMetrics.density
            val drift = (tick % DOT_CYCLE) * step / DOT_CYCLE
            var y = -step + drift
            while (y < h) {
                var x = -step + drift
                while (x < w) {
                    canvas.drawCircle(x, y, step * DOT_SHARE * (y / h), paint)
                    x += step
                }
                y += step
            }
        }

        private fun heroPanel(canvas: Canvas, w: Float, h: Float) {
            val (name, image) = hero ?: return
            val side = min(w, h) * PANEL_SHARE
            val bob = (sin(tick * BOB_SPEED) * side * BOB_SHARE).toFloat()
            val box = RectF((w - side) / 2, (h - side) / 2 + bob, (w + side) / 2, (h + side) / 2 + bob)
            val ink = themed.eraDimen(R.attr.eraInkWidth).coerceAtLeast(2f) * 2
            paint.style = Paint.Style.FILL
            paint.color = themed.eraOutline()
            canvas.drawRect(RectF(box).apply { offset(ink * 2, ink * 2) }, paint)
            image?.let { bitmap ->
                val scale = side / min(bitmap.width, bitmap.height)
                paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(
                        Matrix().apply {
                            setScale(scale, scale)
                            postTranslate(box.left - (bitmap.width * scale - side) / 2, box.top)
                        }
                    )
                }
                canvas.drawRect(box, paint)
                paint.shader = null
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = ink
            canvas.drawRect(box, paint)
            caption(canvas, name.uppercase(), box, ink)
        }

        private fun caption(canvas: Canvas, text: String, box: RectF, ink: Float) {
            paint.style = Paint.Style.FILL
            paint.textSize = box.width() * CAPTION_SHARE
            paint.typeface = themed.eraFont(R.attr.eraTitleFont)
            paint.textAlign = Paint.Align.CENTER
            val pad = paint.textSize / 2
            val label = RectF(box.left + pad, box.bottom - pad, box.right - pad, box.bottom + paint.textSize + pad)
            paint.color = themed.getColor(R.color.caption_yellow)
            canvas.drawRect(label, paint)
            paint.style = Paint.Style.STROKE
            paint.color = themed.eraOutline()
            canvas.drawRect(label, paint.apply { strokeWidth = ink })
            paint.style = Paint.Style.FILL
            paint.color = themed.getColor(R.color.ink)
            canvas.drawText(text, label.centerX(), label.bottom - pad - paint.descent() / 2, paint)
        }
    }

    private companion object {
        const val FRAME_MS = 50L
        const val RAYS = 28
        const val SPIN = 0.002
        const val DOT_STEP = 14f
        const val DOT_SHARE = 0.35f
        const val DOT_CYCLE = 120
        const val PANEL_SHARE = 0.7f
        const val BOB_SPEED = 0.05
        const val BOB_SHARE = 0.015
        const val CAPTION_SHARE = 0.08f
    }
}
