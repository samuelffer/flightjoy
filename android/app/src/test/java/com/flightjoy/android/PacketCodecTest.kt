package com.flightjoy.android

import com.flightjoy.android.protocol.FlightJoyPacket
import com.flightjoy.android.protocol.PacketCodec
import com.flightjoy.android.protocol.ProtocolConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PacketCodecTest {

    @Test
    fun encodeAndDecode_preservesFieldsExactly() {
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
            auxScaled = FlightJoyPacket.scaleAxis(-1.0f),
            buttonsMask = 0b00010011,
            mac = "abcd1234"
        )

        val bytes = PacketCodec.encodeToBytes(original)
        val decoded = PacketCodec.decodeFromBytes(bytes, bytes.size)

        assertNotNull(decoded)
        assertEquals("sess123", decoded?.sessionId)
        assertEquals(99L, decoded?.sequence)
        assertEquals(2500, decoded?.pitchScaled)
        assertEquals(-5000, decoded?.rollScaled)
        assertEquals(10000, decoded?.throttleScaled)
        assertEquals(-10000, decoded?.auxScaled)
        assertEquals(0b00010011, decoded?.buttonsMask)
    }
}
