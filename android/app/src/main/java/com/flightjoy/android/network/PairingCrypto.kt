package com.flightjoy.android.network

import com.flightjoy.android.protocol.FlightJoyPacket
import com.flightjoy.android.protocol.ProtocolConstants
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PairingCrypto {
    private val secureRandom = SecureRandom()
    private val hexChars = "0123456789abcdef".toCharArray()

    fun generateNonceHex(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytesToHex(bytes)
    }

    fun computeHmacSha256Hex(secretKey: String, canonicalPayload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secretKey.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val digest = mac.doFinal(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(digest)
    }

    fun constantTimeEqualsHex(actualHex: String?, expectedHex: String): Boolean {
        if (actualHex.isNullOrEmpty() || actualHex.length != expectedHex.length) {
            return false
        }
        val a = actualHex.lowercase().toByteArray(StandardCharsets.US_ASCII)
        val b = expectedHex.lowercase().toByteArray(StandardCharsets.US_ASCII)
        return MessageDigest.isEqual(a, b)
    }

    fun deriveSessionKey(pin: String, deviceId: String, clientNonce: String, sessionId: String): String {
        val canonical = "SESSION|${ProtocolConstants.VERSION}|$deviceId|$clientNonce|$sessionId"
        return computeHmacSha256Hex(pin, canonical)
    }

    fun buildPairReqCanonical(p: FlightJoyPacket): String =
        "PAIR_REQ|${p.version}|${p.deviceId}|${p.deviceName}|${p.clientNonce}|${p.timestampMs}"

    fun buildPairAckCanonical(p: FlightJoyPacket): String =
        "PAIR_ACK|${p.version}|${p.deviceId}|${p.clientNonce}|${p.sessionId}|${p.timestampMs}"

    fun buildStateCanonical(p: FlightJoyPacket): String =
        "STATE|${p.version}|${p.sessionId}|${p.sequence}|${p.timestampMs}|${p.pitchScaled}|${p.rollScaled}|${p.yawScaled}|${p.throttleScaled}|${p.auxScaled}|${p.buttonsMask}"

    fun buildPingCanonical(p: FlightJoyPacket): String =
        "PING|${p.version}|${p.sessionId}|${p.sequence}|${p.timestampMs}"

    fun buildPongCanonical(p: FlightJoyPacket): String =
        "PONG|${p.version}|${p.sessionId}|${p.sequence}|${p.clientTimestampMs}"

    fun buildDisconnectCanonical(p: FlightJoyPacket): String =
        "DISCONNECT|${p.version}|${p.sessionId}|${p.timestampMs}"

    fun signPairReq(packet: FlightJoyPacket, pin: String) {
        packet.mac = computeHmacSha256Hex(pin, buildPairReqCanonical(packet))
    }

    fun verifyPairAck(packet: FlightJoyPacket, pin: String): Boolean {
        if (packet.type != ProtocolConstants.TYPE_PAIR_ACK || packet.sessionId.isNullOrBlank()) {
            return false
        }
        val expected = computeHmacSha256Hex(pin, buildPairAckCanonical(packet))
        return constantTimeEqualsHex(packet.mac, expected)
    }

    fun signState(packet: FlightJoyPacket, sessionKey: String) {
        packet.mac = computeHmacSha256Hex(sessionKey, buildStateCanonical(packet))
    }

    fun signPing(packet: FlightJoyPacket, sessionKey: String) {
        packet.mac = computeHmacSha256Hex(sessionKey, buildPingCanonical(packet))
    }

    fun verifyPong(packet: FlightJoyPacket, sessionKey: String): Boolean {
        if (packet.type != ProtocolConstants.TYPE_PONG) return false
        val expected = computeHmacSha256Hex(sessionKey, buildPongCanonical(packet))
        return constantTimeEqualsHex(packet.mac, expected)
    }

    fun signDisconnect(packet: FlightJoyPacket, sessionKey: String) {
        packet.mac = computeHmacSha256Hex(sessionKey, buildDisconnectCanonical(packet))
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = hexChars[v ushr 4]
            result[i * 2 + 1] = hexChars[v and 0x0F]
        }
        return String(result)
    }
}
