package com.flightjoy.android.config

import com.flightjoy.android.protocol.ProtocolConstants

enum class ButtonBehaviorMode(val code: Int) {
    SINGLE_CLICK(0), // Normal tap (short pulse)
    HOLD(1),         // Active while holding finger down
    TOGGLE(2);       // Latching: tap once to keep pressed ON, tap again to release OFF

    companion object {
        fun fromCode(code: Int): ButtonBehaviorMode =
            entries.firstOrNull { it.code == code } ?: SINGLE_CLICK
    }
}

data class PhoneButtonConfig(
    var slotIndex: Int,
    var label: String,
    var targetVJoyButton: Int,
    var behaviorMode: ButtonBehaviorMode = ButtonBehaviorMode.SINGLE_CLICK
)

data class FlightClientConfig(
    var serverIp: String = "192.168.1.100",
    var serverPort: Int = ProtocolConstants.DEFAULT_PORT,
    var sendRateHz: Int = 60,
    var landscapeYokeMode: Boolean = true,
    var maxDeflectionDeg: Float = 30.0f,
    var deadzone: Float = 0.0f,
    var sensitivity: Float = 1.0f,
    var exponential: Float = 0.25f,
    var invertPitch: Boolean = false,
    var invertRoll: Boolean = false,
    var invertYaw: Boolean = false,
    var enableYaw: Boolean = false,
    var rudderAutoCenter: Boolean = true,
    var showBrakeSlider: Boolean = false,
    var mapPitchAxis: String = "Y",
    var mapRollAxis: String = "X",
    var mapRudderAxis: String = "RZ",
    var mapThrottleAxis: String = "Z",
    var mapReverseAxis: String = "SL0",
    var mapBrakeAxis: String = "SL1",
    var buttons: MutableList<PhoneButtonConfig> = createDefaultButtons()
) {
    companion object {
        val AVAILABLE_VJOY_AXES = listOf("X", "Y", "Z", "RX", "RY", "RZ", "SL0", "SL1")

        fun createDefaultButtons(): MutableList<PhoneButtonConfig> = mutableListOf(
            PhoneButtonConfig(1, "BRAKE", 1, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(2, "PRK BRK", 2, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(3, "FLAPS -", 3, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(4, "FLAPS +", 4, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(5, "GEAR", 5, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(6, "SPOILER", 6, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(7, "AUTOPILOT", 7, ButtonBehaviorMode.SINGLE_CLICK),
            PhoneButtonConfig(8, "VIEW", 8, ButtonBehaviorMode.SINGLE_CLICK)
        )
    }
}
