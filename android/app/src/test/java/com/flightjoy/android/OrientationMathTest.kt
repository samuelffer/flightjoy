package com.flightjoy.android

import com.flightjoy.android.config.FlightClientConfig
import com.flightjoy.android.sensor.OrientationCalibrator
import com.flightjoy.android.sensor.OrientationMath
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationMathTest {

    @Test
    fun multiplyTransposeLeft_sameMatrix_producesIdentityAndZeroAngles() {
        // Matriz de rotação ortonormal arbitrária (45 graus em torno de X)
        val c = 0.70710677f
        val s = 0.70710677f
        val tilted = floatArrayOf(
            1f, 0f, 0f,
            0f, c, -s,
            0f, s, c
        )

        val rRel = OrientationMath.multiplyTransposeLeft(tilted, tilted)
        val angles = OrientationMath.extractAnglesDegrees(rRel, landscapeMode = true)

        assertEquals(0.0f, angles.pitchDeg, 0.01f)
        assertEquals(0.0f, angles.rollDeg, 0.01f)
        assertEquals(0.0f, angles.yawDeg, 0.01f)
    }

    @Test
    fun orientationCalibrator_autoCalibratesFirstReadingToZero() {
        val calibrator = OrientationCalibrator()
        val config = FlightClientConfig(deadzone = 0.02f)

        val c = 0.8660254f
        val s = 0.5f
        val initialMatrix = floatArrayOf(
            c, 0f, s,
            0f, 1f, 0f,
            -s, 0f, c
        )

        val firstOut = calibrator.processRotationMatrix(initialMatrix, config)
        assertEquals(0.0f, firstOut.pitch, 0.001f)
        assertEquals(0.0f, firstOut.roll, 0.001f)
        assertEquals(0.0f, firstOut.yaw, 0.001f)
    }
}
