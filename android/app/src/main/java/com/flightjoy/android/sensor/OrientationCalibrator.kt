package com.flightjoy.android.sensor

import com.flightjoy.android.config.FlightClientConfig
import com.flightjoy.android.control.SensitivityCurve

data class NormalizedOrientation(
    val pitch: Float,
    val roll: Float,
    val yaw: Float,
    val pitchDeg: Float,
    val rollDeg: Float,
    val yawDeg: Float
)

/**
 * Gerencia a matriz de referência de calibração (Zero/Tara) e transforma
 * a matriz de rotação atual em eixos normalizados [-1.0, +1.0].
 */
class OrientationCalibrator {
    private val lock = Any()
    private var referenceMatrix: FloatArray = OrientationMath.identityMatrix()
    private var lastCurrentMatrix: FloatArray = OrientationMath.identityMatrix()
    private var hasAutoCalibrated = false

    fun calibrateToCurrent() {
        synchronized(lock) {
            referenceMatrix = lastCurrentMatrix.clone()
            hasAutoCalibrated = true
        }
    }

    fun resetCalibration() {
        synchronized(lock) {
            referenceMatrix = OrientationMath.identityMatrix()
            hasAutoCalibrated = false
        }
    }

    fun processRotationMatrix(currentRotationMatrix: FloatArray, config: FlightClientConfig): NormalizedOrientation {
        val rRef: FloatArray
        synchronized(lock) {
            lastCurrentMatrix = currentRotationMatrix.clone()
            if (!hasAutoCalibrated) {
                referenceMatrix = currentRotationMatrix.clone()
                hasAutoCalibrated = true
            }
            rRef = referenceMatrix
        }

        val rRel = OrientationMath.multiplyTransposeLeft(rRef, currentRotationMatrix)
        val angles = OrientationMath.extractAnglesDegrees(rRel, config.landscapeYokeMode)
        val maxDeg = config.maxDeflectionDeg.coerceAtLeast(15.0f)

        // Pitch nativo invertido por padrão (puxar/inclinar para trás = cabrar, para frente = picar)
        val rawPitch = (-angles.pitchDeg / maxDeg).coerceIn(-1.0f, 1.0f)
        val rawRoll = (angles.rollDeg / maxDeg).coerceIn(-1.0f, 1.0f)
        val rawYaw = if (config.enableYaw) {
            (angles.yawDeg / maxDeg).coerceIn(-1.0f, 1.0f)
        } else {
            0.0f
        }

        val pitchOut = SensitivityCurve.apply(
            rawNormalized = rawPitch,
            deadzone = config.deadzone,
            exponential = config.exponential,
            sensitivity = config.sensitivity,
            invert = config.invertPitch
        )

        val rollOut = SensitivityCurve.apply(
            rawNormalized = rawRoll,
            deadzone = config.deadzone,
            exponential = config.exponential,
            sensitivity = config.sensitivity,
            invert = config.invertRoll
        )

        val yawOut = if (config.enableYaw) {
            SensitivityCurve.apply(
                rawNormalized = rawYaw,
                deadzone = config.deadzone,
                exponential = config.exponential,
                sensitivity = config.sensitivity,
                invert = config.invertYaw
            )
        } else {
            0.0f
        }

        return NormalizedOrientation(
            pitch = pitchOut,
            roll = rollOut,
            yaw = yawOut,
            pitchDeg = angles.pitchDeg,
            rollDeg = angles.rollDeg,
            yawDeg = angles.yawDeg
        )
    }
}
