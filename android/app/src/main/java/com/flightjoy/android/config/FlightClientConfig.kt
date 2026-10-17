package com.flightjoy.android.config

import com.flightjoy.android.protocol.ProtocolConstants

data class PhoneButtonConfig(
    var slotIndex: Int,
    var label: String,
    var targetVJoyButton: Int,
    var isToggleMode: Boolean = false
)

data class FlightClientConfig(
    var serverIp: String = "192.168.1.100",
    var serverPort: Int = ProtocolConstants.DEFAULT_PORT,
    var sendRateHz: Int = 60,
    var landscapeYokeMode: Boolean = true,
    var maxDeflectionDeg: Float = 45.0f,
    var deadzone: Float = 0.03f,
    var sensitivity: Float = 1.0f,
    var exponential: Float = 0.25f,
    var invertPitch: Boolean = false,
    var invertRoll: Boolean = false,
    var invertYaw: Boolean = false,
    var enableYaw: Boolean = false, // Como há o slider dedicado de Rudder, o Yaw do giroscópio é opcional
    var mapPitchAxis: String = "Y",
    var mapRollAxis: String = "X",
    var mapRudderAxis: String = "RZ",
    var mapThrottleAxis: String = "Z",
    var buttons: MutableList<PhoneButtonConfig> = createDefaultButtons()
) {
    companion object {
        val AVAILABLE_VJOY_AXES = listOf("X", "Y", "Z", "RX", "RY", "RZ", "SL0", "SL1")

        fun createDefaultButtons(): MutableList<PhoneButtonConfig> = mutableListOf(
            PhoneButtonConfig(1, "GATILHO", 1, false),
            PhoneButtonConfig(2, "FREIOS", 2, false),
            PhoneButtonConfig(3, "FLAPS -", 3, false),
            PhoneButtonConfig(4, "FLAPS +", 4, false),
            PhoneButtonConfig(5, "TREM POUSO", 5, true),
            PhoneButtonConfig(6, "SPOILER", 6, true),
            PhoneButtonConfig(7, "PILOTO AUTO", 7, true),
            PhoneButtonConfig(8, "CÂMERA", 8, false)
        )
    }
}
