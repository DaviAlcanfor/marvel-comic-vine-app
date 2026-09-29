package com.projeto.marvel.ui.battle

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Ringue octogonal em 3D (OpenGL ES 2.0 do próprio Android: nada de biblioteca 3D, que traria o
// Compose junto). Piso com retícula de HQ, saia vermelha, postes e cordas; câmera girando devagar
// em volta e tremendo nos impactos. Os lutadores continuam 2D por cima (não há modelos 3D deles).

private const val SIDES = 8
private const val RING_RADIUS = 3f
private const val SKIRT_DEPTH = 0.6f
private const val POST_HEIGHT = 1.3f
private const val POST_WIDTH = 0.14f
private const val ROPE_THICKNESS = 0.05f
private const val ROPE_LOW = 0.45f
private const val ROPE_MID = 0.8f
private const val ROPE_HIGH = 1.15f
private val ROPE_HEIGHTS = floatArrayOf(ROPE_LOW, ROPE_MID, ROPE_HIGH)
private const val CAMERA_DISTANCE = 6.2f
private const val CAMERA_HEIGHT = 3.4f
private const val CAMERA_TARGET_Y = 0.3f
private const val ORBIT_RADIANS_PER_SECOND = 0.12f
private const val FIELD_OF_VIEW = 45f
private const val NEAR = 0.1f
private const val FAR = 50f
private const val SHAKE_MILLIS = 350L
private const val SHAKE_AMPLITUDE = 0.25f
private const val FLOATS_PER_VERTEX = 6
private const val POSITION_FLOATS = 3
private const val MATRIX_FLOATS = 16
private const val BYTES_PER_FLOAT = 4
private const val CENTER_LIGHTEN = 1.35f
private const val CHANNEL_MAX = 255f
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF

/** Cores do tema (ARGB) passadas pela View; o renderer não conhece resources. */
class ArenaColors(val background: Int, val floor: Int, val skirt: Int, val post: Int, val ropes: IntArray)

class ArenaRenderer(private val colors: ArenaColors) : GLSurfaceView.Renderer {

    private var program = 0
    private val mvp = FloatArray(MATRIX_FLOATS)
    private val projection = FloatArray(MATRIX_FLOATS)
    private val view = FloatArray(MATRIX_FLOATS)
    private lateinit var floor: FloatBuffer
    private lateinit var solids: FloatBuffer
    private var floorVertices = 0
    private var solidVertices = 0

    @Volatile private var shakeUntil = 0L

    /** Chamado da thread de UI: a câmera treme por [SHAKE_MILLIS]. */
    fun impact() {
        shakeUntil = SystemClock.uptimeMillis() + SHAKE_MILLIS
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        val bg = rgb(colors.background)
        GLES20.glClearColor(bg[0], bg[1], bg[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = linkProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        val floorData = floorFan()
        floor = buffer(floorData)
        floorVertices = floorData.size / FLOATS_PER_VERTEX
        val solidData = skirt() + posts() + ropes()
        solids = buffer(solidData)
        solidVertices = solidData.size / FLOATS_PER_VERTEX
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, FIELD_OF_VIEW, width.toFloat() / height.coerceAtLeast(1), NEAR, FAR)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val now = SystemClock.uptimeMillis()
        val angle = now / MILLIS_PER_SECOND * ORBIT_RADIANS_PER_SECOND
        val shake = if (now < shakeUntil) SHAKE_AMPLITUDE * (shakeUntil - now) / SHAKE_MILLIS else 0f
        Matrix.setLookAtM(
            view, 0,
            CAMERA_DISTANCE * cos(angle) + jitter(shake), CAMERA_HEIGHT + jitter(shake), CAMERA_DISTANCE * sin(angle),
            0f, CAMERA_TARGET_Y, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        val halftone = GLES20.glGetUniformLocation(program, "uHalftone")
        GLES20.glUniform1f(halftone, 1f)
        draw(floor, GLES20.GL_TRIANGLE_FAN, floorVertices)
        GLES20.glUniform1f(halftone, 0f)
        draw(solids, GLES20.GL_TRIANGLES, solidVertices)
    }

    private fun draw(data: FloatBuffer, mode: Int, count: Int) {
        val position = GLES20.glGetAttribLocation(program, "aPos")
        val color = GLES20.glGetAttribLocation(program, "aColor")
        val stride = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        data.position(0)
        GLES20.glVertexAttribPointer(position, POSITION_FLOATS, GLES20.GL_FLOAT, false, stride, data)
        GLES20.glEnableVertexAttribArray(position)
        data.position(POSITION_FLOATS)
        GLES20.glVertexAttribPointer(color, POSITION_FLOATS, GLES20.GL_FLOAT, false, stride, data)
        GLES20.glEnableVertexAttribArray(color)
        GLES20.glDrawArrays(mode, 0, count)
    }

    /** Piso: leque de triângulos, centro mais claro (holofote) e borda na cor do piso. */
    private fun floorFan(): FloatArray {
        val edge = rgb(colors.floor)
        val center = edge.map { (it * CENTER_LIGHTEN).coerceAtMost(1f) }.toFloatArray()
        val rim = (0..SIDES).flatMap { i -> corner(i, RING_RADIUS, 0f).toList() + edge.toList() }
        return (listOf(0f, 0f, 0f) + center.toList() + rim).toFloatArray()
    }

    /** Saia do ringue: um retângulo vertical por lado, do piso para baixo. */
    private fun skirt(): FloatArray = (0 until SIDES).flatMap { i ->
        quad(corner(i, RING_RADIUS, 0f), corner(i + 1, RING_RADIUS, 0f), -SKIRT_DEPTH, rgb(colors.skirt))
    }.toFloatArray()

    /** Postes nos cantos: duas faces cruzadas, para terem volume visto de qualquer ângulo. */
    private fun posts(): FloatArray = (0 until SIDES).flatMap { i ->
        val base = corner(i, RING_RADIUS, 0f)
        val color = rgb(colors.post)
        val half = POST_WIDTH / 2
        val (x, z) = base[0] to base[2]
        quad(floatArrayOf(x - half, 0f, z), floatArrayOf(x + half, 0f, z), POST_HEIGHT, color) +
            quad(floatArrayOf(x, 0f, z - half), floatArrayOf(x, 0f, z + half), POST_HEIGHT, color)
    }.toFloatArray()

    /** Três cordas (cores alternadas) ligando os postes. */
    private fun ropes(): FloatArray = ROPE_HEIGHTS.withIndex().flatMap { (level, height) ->
        val color = rgb(colors.ropes[level % colors.ropes.size])
        (0 until SIDES).flatMap { i ->
            quad(corner(i, RING_RADIUS, height), corner(i + 1, RING_RADIUS, height), ROPE_THICKNESS, color)
        }
    }.toFloatArray()
}

private const val MILLIS_PER_SECOND = 1000f

private fun jitter(amount: Float) = if (amount == 0f) 0f else (Random.nextFloat() * 2 - 1) * amount

private fun corner(index: Int, radius: Float, y: Float): FloatArray {
    val angle = (index % SIDES) * 2 * Math.PI / SIDES
    return floatArrayOf((radius * cos(angle)).toFloat(), y, (radius * sin(angle)).toFloat())
}

/** Retângulo vertical de [a] a [b] com altura [height] (negativa = para baixo), em 2 triângulos. */
private fun quad(a: FloatArray, b: FloatArray, height: Float, color: FloatArray): List<Float> {
    val aTop = floatArrayOf(a[0], a[1] + height, a[2])
    val bTop = floatArrayOf(b[0], b[1] + height, b[2])
    return listOf(a, b, bTop, a, bTop, aTop).flatMap { it.toList() + color.toList() }
}

private fun rgb(color: Int) = floatArrayOf(
    (color shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
    (color shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
    (color and CHANNEL_MASK) / CHANNEL_MAX
)

private fun buffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data)
        position(0)
    }

private fun linkProgram(vertexSource: String, fragmentSource: String): Int {
    fun compile(type: Int, source: String) = GLES20.glCreateShader(type).also {
        GLES20.glShaderSource(it, source)
        GLES20.glCompileShader(it)
    }
    return GLES20.glCreateProgram().also {
        GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vertexSource))
        GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource))
        GLES20.glLinkProgram(it)
    }
}

private const val VERTEX_SHADER = """
uniform mat4 uMvp;
attribute vec3 aPos;
attribute vec3 aColor;
varying vec3 vColor;
void main() {
    vColor = aColor;
    gl_Position = uMvp * vec4(aPos, 1.0);
}
"""

// Retícula de HQ no piso: bolinhas mais escuras numa grade de 10 px da tela.
private const val FRAGMENT_SHADER = """
precision mediump float;
varying vec3 vColor;
uniform float uHalftone;
void main() {
    vec2 cell = fract(gl_FragCoord.xy / 10.0) - 0.5;
    float dot = step(length(cell), 0.22);
    gl_FragColor = vec4(mix(vColor, vColor * 0.7, dot * uHalftone), 1.0);
}
"""
