package com.flightjoy.android.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.flightjoy.android.config.FlightClientConfig
import com.flightjoy.android.control.ControlState
import com.flightjoy.android.logging.AppLogger

class SensorController(
    context: Context,
    private val controlState: ControlState,
    private val logger: AppLogger,
    private val onOrientationUpdated: (NormalizedOrientation) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val calibrator = OrientationCalibrator()
    private val rotationMatrix = FloatArray(9)

    @Volatile
    var currentConfig: FlightClientConfig = FlightClientConfig()

    @Volatile
    var isGyroLocked: Boolean = false
        set(value) {
            field = value
            if (value) {
                controlState.pitch = 0.0f
                controlState.roll = 0.0f
                controlState.yaw = 0.0f
                onOrientationUpdated(NormalizedOrientation(0f, 0f, 0f, 0f, 0f, 0f))
            }
        }

    val isSensorAvailable: Boolean
        get() = rotationSensor != null

    val sensorDescription: String
        get() = rotationSensor?.name ?: "Rotation Sensor Unavailable"

    fun start() {
        val sensor = rotationSensor
        if (sensor == null) {
            logger.warn("Sensor", "No TYPE_GAME_ROTATION_VECTOR or TYPE_ROTATION_VECTOR sensor found on device.")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        logger.info("Sensor", "Sensor started: ${sensor.name} (delay=SENSOR_DELAY_GAME).")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun calibrateCenter() {
        calibrator.calibrateToCurrent()
        logger.info("Calibration", "Center orientation calibrated to current device posture.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR
        ) {
            return
        }

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val processed = calibrator.processRotationMatrix(rotationMatrix, currentConfig)

        if (isGyroLocked) {
            controlState.pitch = 0.0f
            controlState.roll = 0.0f
            controlState.yaw = 0.0f
            return
        }

        controlState.pitch = processed.pitch
        controlState.roll = processed.roll
        controlState.yaw = processed.yaw

        onOrientationUpdated(processed)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
