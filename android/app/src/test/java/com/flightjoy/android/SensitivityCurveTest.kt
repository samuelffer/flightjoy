package com.flightjoy.android

import com.flightjoy.android.control.SensitivityCurve
import org.junit.Assert.assertEquals
import org.junit.Test

class SensitivityCurveTest {

    @Test
    fun apply_withinDeadzone_returnsZero() {
        val out = SensitivityCurve.apply(
            rawNormalized = 0.02f,
            deadzone = 0.04f,
            exponential = 0.2f,
            sensitivity = 1.0f,
            invert = false
        )
        assertEquals(0.0f, out, 0.0001f)
    }

    @Test
    fun apply_invertAndGain_scalesCorrectly() {
        val out = SensitivityCurve.apply(
            rawNormalized = 0.50f,
            deadzone = 0.0f,
            exponential = 0.0f,
            sensitivity = 1.5f,
            invert = true
        )
        assertEquals(-0.75f, out, 0.001f)
    }
}
