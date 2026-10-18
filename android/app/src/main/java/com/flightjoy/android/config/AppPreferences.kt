package com.flightjoy.android.config

import android.content.Context
import com.flightjoy.android.protocol.ProtocolConstants
import java.util.UUID

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("flightjoy_prefs", Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "and-" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString("device_id", generated).apply()
        return generated
    }

    fun loadConfig(): FlightClientConfig {
        val defaultButtons = FlightClientConfig.createDefaultButtons()
        val loadedButtons = mutableListOf<PhoneButtonConfig>()

        for (def in defaultButtons) {
            val i = def.slotIndex
            val rawLabel = prefs.getString("btn_${i}_label_en", null) ?: def.label
            val targetVJoy = prefs.getInt("btn_${i}_vjoy", def.targetVJoyButton).coerceIn(1, 32)
            val isHold = prefs.getBoolean("btn_${i}_hold", def.isHoldMode)
            loadedButtons.add(PhoneButtonConfig(i, rawLabel, targetVJoy, isHold))
        }

        return FlightClientConfig(
            serverIp = prefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100",
            serverPort = prefs.getInt("server_port", ProtocolConstants.DEFAULT_PORT),
            sendRateHz = prefs.getInt("send_rate_hz", 60).coerceIn(20, 120),
            landscapeYokeMode = prefs.getBoolean("landscape_mode", true),
            maxDeflectionDeg = prefs.getFloat("max_deflection_deg", 45.0f).coerceIn(15.0f, 85.0f),
            deadzone = prefs.getFloat("deadzone", 0.03f).coerceIn(0.0f, 0.35f),
            sensitivity = prefs.getFloat("sensitivity", 1.0f).coerceIn(0.2f, 2.5f),
            exponential = prefs.getFloat("exponential", 0.25f).coerceIn(0.0f, 0.90f),
            invertPitch = prefs.getBoolean("invert_pitch", false),
            invertRoll = prefs.getBoolean("invert_roll", false),
            invertYaw = prefs.getBoolean("invert_yaw", false),
            enableYaw = prefs.getBoolean("enable_yaw", false),
            rudderAutoCenter = prefs.getBoolean("rudder_auto_center", true),
            mapPitchAxis = prefs.getString("map_pitch", "Y") ?: "Y",
            mapRollAxis = prefs.getString("map_roll", "X") ?: "X",
            mapRudderAxis = prefs.getString("map_rudder", "RZ") ?: "RZ",
            mapThrottleAxis = prefs.getString("map_throttle", "Z") ?: "Z",
            mapReverseAxis = prefs.getString("map_reverse", "SL0") ?: "SL0",
            mapBrakeAxis = prefs.getString("map_brake", "SL1") ?: "SL1",
            buttons = loadedButtons
        )
    }

    fun saveConfig(config: FlightClientConfig) {
        val editor = prefs.edit()
            .putString("server_ip", config.serverIp.trim())
            .putInt("server_port", config.serverPort.coerceIn(1024, 65535))
            .putInt("send_rate_hz", config.sendRateHz.coerceIn(20, 120))
            .putBoolean("landscape_mode", config.landscapeYokeMode)
            .putFloat("max_deflection_deg", config.maxDeflectionDeg.coerceIn(15.0f, 85.0f))
            .putFloat("deadzone", config.deadzone.coerceIn(0.0f, 0.35f))
            .putFloat("sensitivity", config.sensitivity.coerceIn(0.2f, 2.5f))
            .putFloat("exponential", config.exponential.coerceIn(0.0f, 0.90f))
            .putBoolean("invert_pitch", config.invertPitch)
            .putBoolean("invert_roll", config.invertRoll)
            .putBoolean("invert_yaw", config.invertYaw)
            .putBoolean("enable_yaw", config.enableYaw)
            .putBoolean("rudder_auto_center", config.rudderAutoCenter)
            .putString("map_pitch", config.mapPitchAxis)
            .putString("map_roll", config.mapRollAxis)
            .putString("map_rudder", config.mapRudderAxis)
            .putString("map_throttle", config.mapThrottleAxis)
            .putString("map_reverse", config.mapReverseAxis)
            .putString("map_brake", config.mapBrakeAxis)

        for (btn in config.buttons) {
            val i = btn.slotIndex
            editor.putString("btn_${i}_label_en", btn.label.trim().ifEmpty { "B$i" })
            editor.putInt("btn_${i}_vjoy", btn.targetVJoyButton.coerceIn(1, 32))
            editor.putBoolean("btn_${i}_hold", btn.isHoldMode)
        }

        editor.apply()
    }
}
