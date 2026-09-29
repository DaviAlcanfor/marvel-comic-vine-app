package com.projeto.marvel.ui.battle

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Troféu 3D da vitória em OpenGL ES 2.0 (mesma linha da arena, sem biblioteca 3D). A taça é um
// "torno": um perfil (raio × altura) girado em volta do eixo Y; as alças são meio toros. Luz
// difusa + brilho especular dourado, e retícula de HQ nas partes em sombra.

private const val SEGMENTS = 40
private const val HANDLE_SEGMENTS = 20
private const val HANDLE_SIDES = 12
private const val FLOATS_PER_VERTEX = 6
private const val BYTES_PER_FLOAT = 4
private const val BYTES_PER_SHORT = 2
private const val POSITION_FLOATS = 3
private const val MATRIX_FLOATS = 16
private const val SPIN_DEGREES_PER_SECOND = 90f
private const val TILT_DEGREES = 12f
private const val FIELD_OF_VIEW = 35f
private const val NEAR = 0.1f
private const val FAR = 20f
private const val CAMERA_Z = 4.6f
private const val CAMERA_Y = 0.25f
private const val MODEL_OFFSET_Y = -0.75f
private const val MILLIS_PER_SECOND = 1000f
private const val HANDLE_MAJOR = 0.26f
private const val HANDLE_MINOR = 0.05f
private const val HANDLE_X = 0.72f
private const val HANDLE_Y = 1.1f

/** Perfil da taça, de baixo para cima: (raio, altura). Base, haste, bojo e borda. */
@Suppress("MagicNumber") // tabela de geometria: cada par é um ponto do desenho da taça
private val PROFILE = listOf(
    0f to 0f, 0.62f to 0f, 0.62f to 0.1f, 0.4f to 0.14f, 0.14f to 0.2f, 0.12f to 0.55f,
    0.2f to 0.62f, 0.42f to 0.75f, 0.58f to 0.95f, 0.64f to 1.2f, 0.66f to 1.45f, 0.6f to 1.47f
)

class TrophyRenderer(private val gold: Int, private val clearColor: Int) : GLSurfaceView.Renderer {

    private var program = 0
    private val projection = FloatArray(MATRIX_FLOATS)
    private val view = FloatArray(MATRIX_FLOATS)
    private val model = FloatArray(MATRIX_FLOATS)
    private val viewModel = FloatArray(MATRIX_FLOATS)
    private val mvp = FloatArray(MATRIX_FLOATS)
    private lateinit var vertices: FloatBuffer
    private lateinit var indices: ShortBuffer
    private var indexCount = 0

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        val bg = rgb(clearColor)
        GLES20.glClearColor(bg[0], bg[1], bg[2], alpha(clearColor))
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = linkTrophyProgram()
        val mesh = Mesh()
        mesh.lathe()
        mesh.handle(side = 1f)
        mesh.handle(side = -1f)
        vertices = floatBuffer(mesh.vertices.toFloatArray())
        indices = shortBuffer(mesh.indices.toShortArray())
        indexCount = mesh.indices.size
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, FIELD_OF_VIEW, width.toFloat() / height.coerceAtLeast(1), NEAR, FAR)
        Matrix.setLookAtM(view, 0, 0f, CAMERA_Y, CAMERA_Z, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val angle = SystemClock.uptimeMillis() / MILLIS_PER_SECOND * SPIN_DEGREES_PER_SECOND
        Matrix.setIdentityM(model, 0)
        Matrix.rotateM(model, 0, TILT_DEGREES, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, angle, 0f, 1f, 0f)
        Matrix.translateM(model, 0, 0f, MODEL_OFFSET_Y, 0f)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModel"), 1, false, model, 0)
        val color = rgb(gold)
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "uColor"), color[0], color[1], color[2])

        val stride = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        val position = GLES20.glGetAttribLocation(program, "aPos")
        val normal = GLES20.glGetAttribLocation(program, "aNormal")
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, POSITION_FLOATS, GLES20.GL_FLOAT, false, stride, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(POSITION_FLOATS)
        GLES20.glVertexAttribPointer(normal, POSITION_FLOATS, GLES20.GL_FLOAT, false, stride, vertices)
        GLES20.glEnableVertexAttribArray(normal)
        indices.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indices)
    }
}

/** Malha indexada: posição + normal por vértice. */
private class Mesh {
    val vertices = mutableListOf<Float>()
    val indices = mutableListOf<Short>()
    private val vertexCount get() = vertices.size / FLOATS_PER_VERTEX

    /** [p] = posição (x, y, z); [n] = normal, normalizada aqui. */
    private fun vertex(p: FloatArray, n: FloatArray) {
        val length = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]).takeIf { it > 0f } ?: 1f
        vertices += listOf(p[0], p[1], p[2], n[0] / length, n[1] / length, n[2] / length)
    }

    /** Grade (linhas × colunas) já emitida: liga em pares de triângulos. */
    private fun grid(start: Int, rows: Int, columns: Int) {
        for (row in 0 until rows - 1) {
            for (column in 0 until columns - 1) {
                val a = start + row * columns + column
                val b = a + columns
                indices += listOf(a, b, a + 1, a + 1, b, b + 1).map { it.toShort() }
            }
        }
    }

    /** Taça: [PROFILE] girado em volta de Y. A normal sai da tangente do perfil. */
    fun lathe() {
        val start = vertexCount
        PROFILE.forEachIndexed { index, (radius, height) ->
            val previous = PROFILE[(index - 1).coerceAtLeast(0)]
            val next = PROFILE[(index + 1).coerceAtMost(PROFILE.lastIndex)]
            val dr = next.first - previous.first
            val dy = next.second - previous.second
            for (segment in 0..SEGMENTS) {
                val theta = segment * 2 * Math.PI.toFloat() / SEGMENTS
                val c = cos(theta)
                val s = sin(theta)
                vertex(floatArrayOf(radius * c, height, radius * s), floatArrayOf(dy * c, -dr, dy * s))
            }
        }
        grid(start, PROFILE.size, SEGMENTS + 1)
    }

    /** Alça: meio toro de fora da taça, no plano XY, do lado [side] (1 = direita, -1 = esquerda). */
    fun handle(side: Float) {
        val start = vertexCount
        for (i in 0..HANDLE_SEGMENTS) {
            val u = (-Math.PI / 2 + i * Math.PI / HANDLE_SEGMENTS).toFloat()
            for (j in 0..HANDLE_SIDES) {
                val v = j * 2 * Math.PI.toFloat() / HANDLE_SIDES
                val ring = HANDLE_MAJOR + HANDLE_MINOR * cos(v)
                val x = side * (HANDLE_X + ring * cos(u))
                val y = HANDLE_Y + ring * sin(u)
                val z = HANDLE_MINOR * sin(v)
                vertex(floatArrayOf(x, y, z), floatArrayOf(side * cos(v) * cos(u), cos(v) * sin(u), sin(v)))
            }
        }
        grid(start, HANDLE_SEGMENTS + 1, HANDLE_SIDES + 1)
    }
}

private const val CHANNEL_MAX = 255f
private const val CHANNEL_MASK = 0xFF
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

private fun rgb(color: Int) = floatArrayOf(
    (color shr RED_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
    (color shr GREEN_SHIFT and CHANNEL_MASK) / CHANNEL_MAX,
    (color and CHANNEL_MASK) / CHANNEL_MAX
)

private fun alpha(color: Int) = (color ushr ALPHA_SHIFT and CHANNEL_MASK) / CHANNEL_MAX

private fun floatBuffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data)
        position(0)
    }

private fun shortBuffer(data: ShortArray): ShortBuffer =
    ByteBuffer.allocateDirect(data.size * BYTES_PER_SHORT).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
        put(data)
        position(0)
    }

private fun linkTrophyProgram(): Int {
    fun compile(type: Int, source: String) = GLES20.glCreateShader(type).also {
        GLES20.glShaderSource(it, source)
        GLES20.glCompileShader(it)
    }
    return GLES20.glCreateProgram().also {
        GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, TROPHY_VERTEX))
        GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, TROPHY_FRAGMENT))
        GLES20.glLinkProgram(it)
    }
}

private const val TROPHY_VERTEX = """
uniform mat4 uMvp;
uniform mat4 uModel;
attribute vec3 aPos;
attribute vec3 aNormal;
varying vec3 vNormal;
void main() {
    vNormal = normalize(mat3(uModel) * aNormal);
    gl_Position = uMvp * vec4(aPos, 1.0);
}
"""

// Difusa + especular (brilho de metal) + retícula de HQ onde a luz é fraca.
private const val TROPHY_FRAGMENT = """
precision mediump float;
varying vec3 vNormal;
uniform vec3 uColor;
void main() {
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    vec3 light = normalize(vec3(0.6, 0.8, 0.9));
    float diffuse = max(dot(n, light), 0.0);
    vec3 halfway = normalize(light + vec3(0.0, 0.0, 1.0));
    float specular = pow(max(dot(n, halfway), 0.0), 24.0);
    vec3 color = uColor * (0.35 + 0.75 * diffuse) + vec3(1.0, 0.97, 0.85) * specular * 0.8;
    vec2 cell = fract(gl_FragCoord.xy / 7.0) - 0.5;
    float dots = step(length(cell), 0.25) * step(diffuse, 0.45);
    gl_FragColor = vec4(mix(color, color * 0.55, dots), 1.0);
}
"""
