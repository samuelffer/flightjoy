package com.flightjoy.android

import com.flightjoy.android.network.PairingCrypto
import com.flightjoy.android.protocol.FlightJoyPacket
import com.flightjoy.android.protocol.ProtocolConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCryptoTest {

    @Test
    fun pairAckVerification_succeedsWithCorrectPinAndFailsWithWrongPin() {
        val pin = "482910"
        val ack = FlightJoyPacket(
            version = 1,
            type = ProtocolConstants.TYPE_PAIR_ACK,
            deviceId = "pixel-8-id",
            clientNonce = "0011223344556677",
            sessionId = "ffeeddccbbaa9988",
            timestampMs = 1729180000000L
        )
        ack.mac = PairingCrypto.computeHmacSha256Hex(pin, PairingCrypto.buildPairAckCanonical(ack))

        assertTrue(PairingCrypto.verifyPairAck(ack, pin))
        assertFalse(PairingCrypto.verifyPairAck(ack, "000000"))
    }

    @Test
    fun stateSignature_isDeterministicAcrossCalls() {
        val sessionKey = PairingCrypto.deriveSessionKey("654321", "dev1", "nonceA", "sessB")
        val state = FlightJoyPacket(
            version = 1,
            type = ProtocolConstants.TYPE_STATE,
            sessionId = "sessB",
            sequence = 10L,
            timestampMs = 5000L,
            pitchScaled = 2500,
            rollScaled = -5000,
            yawScaled = 0,
            throttleScaled = 7500,
            auxScaled = -1000,
            buttonsMask = 5
        )

        PairingCrypto.signState(state, sessionKey)
        val mac1 = state.mac
        PairingCrypto.signState(state, sessionKey)
        val mac2 = state.mac

        assertEquals(64, mac1?.length)
        assertEquals(mac1, mac2)
    }
}
