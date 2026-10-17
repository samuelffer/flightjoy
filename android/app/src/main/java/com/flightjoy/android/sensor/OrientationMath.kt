package com.flightjoy.android.sensor

import kotlin.math.asin
import kotlin.math.atan2

data class EulerAnglesDeg(
    val pitchDeg: Float,
    val rollDeg: Float,
    val yawDeg: Float
)

/**
 * Operações matemáticas puras sobre matrizes de rotação 3x3 (row-major, 9 elementos)
 * compatíveis com SensorManager.getRotationMatrixFromVector e testáveis na JVM sem Android.
 */
object OrientationMath {

    fun identityMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )

    /**
     * Calcula R_rel = (R_ref^T) * R_cur.
     * Quando R_cur == R_ref, o resultado é a matriz identidade I.
     */
    fun multiplyTransposeLeft(rRef: FloatArray, rCur: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (row in 0..2) {
            for (col in 0..2) {
                var sum = 0.0f
                for (k in 0..2) {
                    // (R_ref^T)[row, k] == R_ref[k, row]
                    sum += rRef[k * 3 + row] * rCur[k * 3 + col]
                }
                out[row * 3 + col] = sum
            }
        }
        return out
    }

    /**
     * Extrai os ângulos relativos (Pitch, Roll, Yaw em graus) a partir de R_rel (3x3).
     * @param landscapeMode Se true, mapeia os eixos ergonômicos de empunhadura em paisagem (Manche).
     */
    fun extractAnglesDegrees(rRel: FloatArray, landscapeMode: Boolean = true): EulerAnglesDeg {
        val azimuthRad = atan2(rRel[1].toDouble(), rRel[4].toDouble()).toFloat()
        val devicePitchRad = asin((-rRel[7]).coerceIn(-1.0f, 1.0f).toDouble()).toFloat()
        val deviceRollRad = atan2((-rRel[6]).toDouble(), rRel[8].toDouble()).toFloat()

        val radToDeg = (180.0 / Math.PI).toFloat()
        val yawDeg = azimuthRad * radToDeg

        return if (landscapeMode) {
            // Em paisagem (manche): inclinar para frente/trás ocorre no eixo Y do aparelho (deviceRoll),
            // e girar o manche para esquerda/direita ocorre no eixo X do aparelho (devicePitch).
            EulerAnglesDeg(
                pitchDeg = deviceRollRad * radToDeg,
                rollDeg = -devicePitchRad * radToDeg,
                yawDeg = yawDeg
            )
        } else {
            EulerAnglesDeg(
                pitchDeg = devicePitchRad * radToDeg,
                rollDeg = deviceRollRad * radToDeg,
                yawDeg = yawDeg
            )
        }
    }
}
