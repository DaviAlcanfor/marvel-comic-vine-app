package com.projeto.marvel.ui.detail

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.math.PI

/**
 * Inclinação do celular para o efeito 3D do Detalhe. Entrega [onTilt] com (frente/trás, lados)
 * em graus, relativos à posição em que a tela abriu, suavizados e limitados a ±[MAX_DEGREES].
 * Só escuta o sensor com a tela visível; desligado se o sistema pedir "remover animações".
 */
class TiltController(
    context: Context,
    private val onTilt: (pitch: Float, roll: Float) -> Unit
) : DefaultLifecycleObserver, SensorEventListener {

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotation = FloatArray(ROTATION_MATRIX)
    private val angles = FloatArray(ANGLES)
    private var base: FloatArray? = null
    private var pitch = 0f
    private var roll = 0f

    override fun onResume(owner: LifecycleOwner) {
        if (sensor == null || !ValueAnimator.areAnimatorsEnabled()) return
        base = null
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause(owner: LifecycleOwner) {
        sensors.unregisterListener(this)
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val start = base ?: angles.copyOf().also { base = it }
        val targetPitch = degrees(angles[1] - start[1])
        val targetRoll = degrees(angles[2] - start[2])
        // Filtro passa-baixa: sem ele o card treme com a mão.
        pitch += (targetPitch - pitch) * SMOOTHING
        roll += (targetRoll - roll) * SMOOTHING
        onTilt(pitch, roll)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun degrees(radians: Float) =
        (radians * HALF_TURN_DEGREES / PI.toFloat()).coerceIn(-MAX_DEGREES, MAX_DEGREES)

    private companion object {
        const val ROTATION_MATRIX = 9
        const val ANGLES = 3
        const val MAX_DEGREES = 15f
        const val SMOOTHING = 0.15f
        const val HALF_TURN_DEGREES = 180f
    }
}
