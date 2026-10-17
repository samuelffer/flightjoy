package com.flightjoy.android.protocol

import com.google.gson.annotations.SerializedName
import kotlin.math.roundToInt

object ProtocolConstants {
    const val VERSION = 1
    const val DEFAULT_PORT = 49152
    const val AXIS_SCALE = 10000
    const val MAX_BUTTONS = 8

    const val TYPE_PAIR_REQ = "PAIR_REQ"
    const val TYPE_PAIR_ACK = "PAIR_ACK"
    const val TYPE_PAIR_ERR = "PAIR_ERR"
    const val TYPE_STATE = "STATE"
    const val TYPE_PING = "PING"
    const val TYPE_PONG = "PONG"
    const val TYPE_DISCONNECT = "DISCONNECT"
}

data class FlightJoyPacket(
    @SerializedName("v")
    var version: Int = ProtocolConstants.VERSION,

    @SerializedName("type")
    var type: String = "",

    @SerializedName("deviceId")
    var deviceId: String? = null,

    @SerializedName("deviceName")
    var deviceName: String? = null,

    @SerializedName("clientNonce")
    var clientNonce: String? = null,

    @SerializedName("sessionId")
    var sessionId: String? = null,

    @SerializedName("reason")
    var reason: String? = null,

    @SerializedName("seq")
    var sequence: Long = 0L,

    @SerializedName("ts")
    var timestampMs: Long = 0L,

    @SerializedName("clientTs")
    var clientTimestampMs: Long = 0L,

    @SerializedName("p")
    var pitchScaled: Int = 0,

    @SerializedName("r")
    var rollScaled: Int = 0,

    @SerializedName("y")
    var yawScaled: Int = 0,

    @SerializedName("t")
    var throttleScaled: Int = 0,

    @SerializedName("a")
    var auxScaled: Int = 0,

    @SerializedName("b")
    var buttonsMask: Int = 0,

    @SerializedName("mac")
    var mac: String? = null
) {
    companion object {
        fun scaleAxis(normalized: Float): Int {
            val clamped = normalized.coerceIn(-1.0f, 1.0f)
            return (clamped * ProtocolConstants.AXIS_SCALE).roundToInt()
        }
    }
}
