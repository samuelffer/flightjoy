package com.flightjoy.android

import com.flightjoy.android.protocol.FlightJoyPacket
import com.flightjoy.android.protocol.PacketCodec
import com.flightjoy.android.protocol.ProtocolConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PacketCodecTest {

    @Test
    fun encodeAndDecode_preservesFieldsAndAxisMappings() {
        val original = FlightJoyPacket(
            version = ProtocolConstants.VERSION,
            type = ProtocolConstants.TYPE_STATE,
            sessionId = "sess123",
            sequence = 99L,
            timestampMs = 1729180000999L,
            pitchScaled = FlightJoyPacket.scaleAxis(0.25f),
            rollScaled = FlightJoyPacket.scaleAxis(-0.50f),
            yawScaled = FlightJoyPacket.scaleAxis(0.0f),
            throttleScaled = FlightJoyPacket.scaleAxis(1.0f),
            buttonsMask = (1 shl 0) or (1 shl 15),
            mapPitch = "Y",
            mapRoll = "X",
            mapRudder = "RZ",
            mapThrottle = "Z"
        )

        val bytes = PacketCodec.encodeToBytes(original)
        val decoded = PacketCodec.decodeFromBytes(bytes, bytes.size)

        assertNotNull(decoded)
        assertEquals("sess123", decoded?.sessionId)
        assertEquals(99L, decoded?.sequence)
        assertEquals(2500, decoded?.pitchScaled)
        assertEquals(-5000, decoded?.rollScaled)
        assertEquals(10000, decoded?.throttleScaled)
        assertEquals((1 shl 0) or (1 shl 15), decoded?.buttonsMask)
        assertEquals("Y", decoded?.mapPitch)
        assertEquals("X", decoded?.mapRoll)
        assertEquals("RZ", decoded?.mapRudder)
        assertEquals("Z", decoded?.mapThrottle)
    }

    @Test
    fun encodeAndDecode_discoverReply_preservesPcNameAndPort() {
        val reply = FlightJoyPacket(
            version = ProtocolConstants.VERSION,
            type = ProtocolConstants.TYPE_DISCOVER_REPLY,
            pcName = "DESKTOP-FLIGHTPC",
            port = 49152
        )

        val bytes = PacketCodec.encodeToBytes(reply)
        val decoded = PacketCodec.decodeFromBytes(bytes, bytes.size)

        assertNotNull(decoded)
        assertEquals(ProtocolConstants.TYPE_DISCOVER_REPLY, decoded?.type)
        assertEquals("DESKTOP-FLIGHTPC", decoded?.pcName)
        assertEquals(49152, decoded?.port)
    }
}
