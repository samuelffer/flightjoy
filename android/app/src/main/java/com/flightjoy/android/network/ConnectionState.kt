package com.flightjoy.android.network

enum class ClientConnectionState {
    DISCONNECTED,
    PAIRING,
    CONNECTED,
    ERROR_OR_TIMEOUT
}

data class ClientStatusSnapshot(
    val state: ClientConnectionState,
    val message: String,
    val rttMs: Long = 0L,
    val packetsSent: Long = 0L
)
