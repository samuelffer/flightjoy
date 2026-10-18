package com.flightjoy.android.control

import kotlin.math.abs
import kotlin.math.sign

object SensitivityCurve {
    fun apply(
        rawNormalized: Float,
        deadzone: Float,
        exponential: Float,
        sensitivity: Float,
        invert: Boolean
    ): Float {
        val input = rawNormalized.coerceIn(-1.0f, 1.0f)
        val dz = deadzone.coerceIn(0.0f, 0.50f)
        val expo = exponential.coerceIn(0.0f, 1.0f)
        val sens = sensitivity.coerceIn(0.1f, 3.0f)

        val absVal = abs(input)
        if (absVal <= dz) {
            return 0.0f
        }

        val s = sign(input)
        val rescaled = (absVal - dz) / (1.0f - dz)
        val curved = (1.0f - expo) * rescaled + expo * (rescaled * rescaled * rescaled)
        var result = s * curved * sens
        if (invert) {
            result = -result
        }
        return result.coerceIn(-1.0f, 1.0f)
    }
}
