package com.flightjoy.android.config

import com.flightjoy.android.protocol.ProtocolConstants

data class FlightClientConfig(
    var serverIp: String = "192.168.1.100",
    var serverPort: Int = ProtocolConstants.DEFAULT_PORT,
    var pairingPin: String = "482910",
    var sendRateHz: Int = 60,
    var landscapeYokeMode: Boolean = true,
    var maxDeflectionDeg: Float = 45.0f,
    var deadzone: Float = 0.03f,
    var sensitivity: Float = 1.0f,
    var exponential: Float = 0.20f,
    var invertPitch: Boolean = false,
    var invertRoll: Boolean = false,
    var invertYaw: Boolean = false,
    var enableYaw: Boolean = true
)
