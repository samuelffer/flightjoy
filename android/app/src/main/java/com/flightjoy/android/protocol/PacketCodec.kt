package com.flightjoy.android.protocol

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.charset.StandardCharsets

object PacketCodec {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun encodeToBytes(packet: FlightJoyPacket): ByteArray {
        return gson.toJson(packet).toByteArray(StandardCharsets.UTF_8)
    }

    fun encodeToString(packet: FlightJoyPacket): String {
        return gson.toJson(packet)
    }

    fun decodeFromBytes(bytes: ByteArray, length: Int = bytes.size): FlightJoyPacket? {
        if (length <= 0) return null
        return try {
            val json = String(bytes, 0, length, StandardCharsets.UTF_8)
            gson.fromJson(json, FlightJoyPacket::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeFromString(json: String): FlightJoyPacket? {
        if (json.isBlank()) return null
        return try {
            gson.fromJson(json, FlightJoyPacket::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
