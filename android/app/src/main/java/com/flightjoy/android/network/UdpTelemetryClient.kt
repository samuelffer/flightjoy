package com.flightjoy.android.network

import android.os.Build
import com.flightjoy.android.config.FlightClientConfig
import com.flightjoy.android.control.ControlState
import com.flightjoy.android.logging.AppLogger
import com.flightjoy.android.protocol.FlightJoyPacket
import com.flightjoy.android.protocol.PacketCodec
import com.flightjoy.android.protocol.ProtocolConstants
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class DiscoveredPc(
    val ipAddress: String,
    val pcName: String,
    val port: Int
)

class UdpTelemetryClient(
    private val deviceId: String,
    private val controlState: ControlState,
    private val logger: AppLogger,
    private val onStatusChanged: (ClientStatusSnapshot) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var socket: DatagramSocket? = null
    private var workerThread: Thread? = null
    private var receiveThread: Thread? = null

    @Volatile
    private var sessionId: String? = null

    @Volatile
    var activeConfig: FlightClientConfig = FlightClientConfig()

    private val lastServerPongMs = AtomicLong(0L)
    private val lastRttMs = AtomicLong(0L)
    private val packetsSentCount = AtomicLong(0L)

    val isConnectedOrPairing: Boolean
        get() = running.get()

    /**
     * Automatically finds a PC running FlightJoy on the local network via UDP Broadcast.
     */
    fun discoverPcOnLan(port: Int, onResult: (DiscoveredPc?) -> Unit) {
        Thread({
            var searchSocket: DatagramSocket? = null
            try {
                val targetPort = port.coerceIn(1024, 65535)
                val udp = DatagramSocket()
                udp.broadcast = true
                udp.soTimeout = 700
                searchSocket = udp

                val discoverPkt = FlightJoyPacket(
                    version = ProtocolConstants.VERSION,
                    type = ProtocolConstants.TYPE_DISCOVER,
                    deviceId = deviceId,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    timestampMs = System.currentTimeMillis()
                )
                val bytes = PacketCodec.encodeToBytes(discoverPkt)

                val broadcastTargets = mutableSetOf<InetAddress>()
                broadcastTargets.add(InetAddress.getByName("255.255.255.255"))

                try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    while (interfaces.hasMoreElements()) {
                        val ni = interfaces.nextElement()
                        if (!ni.isUp || ni.isLoopback) continue
                        for (ia in ni.interfaceAddresses) {
                            val bcast = ia.broadcast
                            if (bcast is Inet4Address) {
                                broadcastTargets.add(bcast)
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                val recvBuf = ByteArray(1024)
                var found: DiscoveredPc? = null

                for (attempt in 1..2) {
                    for (addr in broadcastTargets) {
                        try {
                            udp.send(DatagramPacket(bytes, bytes.size, addr, targetPort))
                        } catch (_: Exception) {
                        }
                    }

                    try {
                        val dp = DatagramPacket(recvBuf, recvBuf.size)
                        udp.receive(dp)
                        val decoded = PacketCodec.decodeFromBytes(dp.data, dp.length)
                        if (decoded != null && decoded.type == ProtocolConstants.TYPE_DISCOVER_REPLY) {
                            val ip = dp.address.hostAddress ?: continue
                            val pcName = decoded.pcName?.ifBlank { "Desktop PC" } ?: "Desktop PC"
                            val replyPort = if (decoded.port > 0) decoded.port else targetPort
                            found = DiscoveredPc(ip, pcName, replyPort)
                            break
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }

                if (found != null) {
                    logger.info("Discovery", "PC found: ${found.pcName} (${found.ipAddress}:${found.port})")
                } else {
                    logger.warn("Discovery", "No PC found on UDP port $targetPort.")
                }

                onResult(found)
            } catch (ex: Exception) {
                logger.error("Discovery", "Auto-discovery error: ${ex.message}")
                onResult(null)
            } finally {
                try {
                    searchSocket?.close()
                } catch (_: Exception) {
                }
            }
        }, "FlightJoy-Discovery").apply {
            isDaemon = true
            start()
        }
    }

    fun connect(config: FlightClientConfig) {
        disconnect(sendPacket = false)
        activeConfig = config
        running.set(true)

        workerThread = Thread({
            runSessionLifecycle(config)
        }, "FlightJoy-UDP-Worker").apply {
            isDaemon = true
            start()
        }
    }

    fun disconnect(sendPacket: Boolean = true) {
        val wasRunning = running.getAndSet(false)
        val currentSocket = socket
        val activeSessionId = sessionId

        if (wasRunning && sendPacket && currentSocket != null && !currentSocket.isClosed &&
            !activeSessionId.isNullOrBlank()
        ) {
            Thread {
                try {
                    val pkt = FlightJoyPacket(
                        version = ProtocolConstants.VERSION,
                        type = ProtocolConstants.TYPE_DISCONNECT,
                        sessionId = activeSessionId,
                        timestampMs = System.currentTimeMillis()
                    )
                    val bytes = PacketCodec.encodeToBytes(pkt)
                    val addr = currentSocket.inetAddress
                    val port = currentSocket.port
                    if (addr != null && port > 0) {
                        currentSocket.send(DatagramPacket(bytes, bytes.size, addr, port))
                    }
                } catch (_: Exception) {
                } finally {
                    try {
                        currentSocket.close()
                    } catch (_: Exception) {
                    }
                }
            }.start()
        } else {
            try {
                currentSocket?.close()
            } catch (_: Exception) {
            }
        }

        socket = null
        sessionId = null

        if (wasRunning) {
            logger.info("Network", "Disconnected.")
            onStatusChanged(
                ClientStatusSnapshot(
                    state = ClientConnectionState.DISCONNECTED,
                    message = "Disconnected"
                )
            )
        }
    }

    private fun runSessionLifecycle(config: FlightClientConfig) {
        try {
            val targetAddress = InetAddress.getByName(config.serverIp.trim())
            val targetPort = config.serverPort.coerceIn(1024, 65535)

            onStatusChanged(
                ClientStatusSnapshot(
                    state = ClientConnectionState.PAIRING,
                    message = "Connecting..."
                )
            )

            val udp = DatagramSocket()
            udp.soTimeout = 700
            udp.connect(targetAddress, targetPort)
            socket = udp

            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val helloReq = FlightJoyPacket(
                version = ProtocolConstants.VERSION,
                type = ProtocolConstants.TYPE_HELLO,
                deviceId = deviceId,
                deviceName = deviceModel,
                timestampMs = System.currentTimeMillis()
            )
            val reqBytes = PacketCodec.encodeToBytes(helloReq)

            var welcomePacket: FlightJoyPacket? = null
            val recvBuf = ByteArray(2048)

            for (attempt in 1..3) {
                if (!running.get()) return
                udp.send(DatagramPacket(reqBytes, reqBytes.size))
                try {
                    val respDatagram = DatagramPacket(recvBuf, recvBuf.size)
                    udp.receive(respDatagram)
                    val decoded = PacketCodec.decodeFromBytes(respDatagram.data, respDatagram.length)
                    if (decoded != null &&
                        decoded.type == ProtocolConstants.TYPE_WELCOME &&
                        !decoded.sessionId.isNullOrBlank()
                    ) {
                        welcomePacket = decoded
                        break
                    }
                } catch (_: SocketTimeoutException) {
                }
            }

            if (welcomePacket == null || welcomePacket.sessionId.isNullOrBlank()) {
                logger.error("Network", "No response from ${config.serverIp}:$targetPort.")
                running.set(false)
                udp.close()
                onStatusChanged(
                    ClientStatusSnapshot(
                        state = ClientConnectionState.ERROR_OR_TIMEOUT,
                        message = "No response (${config.serverIp})"
                    )
                )
                return
            }

            val establishedSessionId = welcomePacket.sessionId!!
            val pcDisplay = welcomePacket.pcName?.ifBlank { config.serverIp } ?: config.serverIp
            sessionId = establishedSessionId
            lastServerPongMs.set(System.currentTimeMillis())
            packetsSentCount.set(0L)

            logger.info("Network", "Connected to $pcDisplay!")
            onStatusChanged(
                ClientStatusSnapshot(
                    state = ClientConnectionState.CONNECTED,
                    message = "Connected • $pcDisplay",
                    rttMs = 0L,
                    packetsSent = 0L
                )
            )

            udp.soTimeout = 500
            receiveThread = Thread({
                runReceiveLoop(udp)
            }, "FlightJoy-UDP-Recv").apply {
                isDaemon = true
                start()
            }

            var seq = 0L
            var lastPingMs = System.currentTimeMillis()

            while (running.get() && !udp.isClosed) {
                val loopStart = System.currentTimeMillis()
                val cfg = activeConfig
                val intervalMs = (1000L / cfg.sendRateHz.coerceIn(20, 120)).coerceAtLeast(8L)

                if (loopStart - lastServerPongMs.get() > 3800L) {
                    logger.warn("Watchdog", "PC stopped responding.")
                    running.set(false)
                    onStatusChanged(
                        ClientStatusSnapshot(
                            state = ClientConnectionState.ERROR_OR_TIMEOUT,
                            message = "Disconnected (Timeout)"
                        )
                    )
                    break
                }

                seq++
                val rudderNorm = if (cfg.invertYaw) -controlState.combinedRudder else controlState.combinedRudder
                val statePacket = FlightJoyPacket(
                    version = ProtocolConstants.VERSION,
                    type = ProtocolConstants.TYPE_STATE,
                    sessionId = establishedSessionId,
                    sequence = seq,
                    timestampMs = loopStart,
                    pitchScaled = FlightJoyPacket.scaleAxis(controlState.pitch),
                    rollScaled = FlightJoyPacket.scaleAxis(controlState.roll),
                    yawScaled = FlightJoyPacket.scaleAxis(rudderNorm),
                    throttleScaled = FlightJoyPacket.scaleAxis(controlState.throttle),
                    auxScaled = FlightJoyPacket.scaleAxis(controlState.reverseThrottle),
                    brakeScaled = FlightJoyPacket.scaleAxis(controlState.brakeSlider),
                    buttonsMask = controlState.getButtonsMask(),
                    mapPitch = cfg.mapPitchAxis,
                    mapRoll = cfg.mapRollAxis,
                    mapRudder = cfg.mapRudderAxis,
                    mapThrottle = cfg.mapThrottleAxis,
                    mapReverse = cfg.mapReverseAxis,
                    mapBrake = cfg.mapBrakeAxis
                )
                val bytes = PacketCodec.encodeToBytes(statePacket)
                udp.send(DatagramPacket(bytes, bytes.size))
                val totalSent = packetsSentCount.incrementAndGet()

                if (loopStart - lastPingMs >= 1000L) {
                    lastPingMs = loopStart
                    val ping = FlightJoyPacket(
                        version = ProtocolConstants.VERSION,
                        type = ProtocolConstants.TYPE_PING,
                        sessionId = establishedSessionId,
                        sequence = seq,
                        timestampMs = loopStart
                    )
                    val pingBytes = PacketCodec.encodeToBytes(ping)
                    udp.send(DatagramPacket(pingBytes, pingBytes.size))

                    onStatusChanged(
                        ClientStatusSnapshot(
                            state = ClientConnectionState.CONNECTED,
                            message = "Connected ($pcDisplay) • ${lastRttMs.get()} ms",
                            rttMs = lastRttMs.get(),
                            packetsSent = totalSent
                        )
                    )
                }

                val elapsed = System.currentTimeMillis() - loopStart
                val sleepMs = intervalMs - elapsed
                if (sleepMs > 1L) {
                    Thread.sleep(sleepMs)
                }
            }
        } catch (ex: Exception) {
            if (running.get()) {
                logger.error("Network", "UDP failure: ${ex.message}")
                running.set(false)
                onStatusChanged(
                    ClientStatusSnapshot(
                        state = ClientConnectionState.ERROR_OR_TIMEOUT,
                        message = "Connection error"
                    )
                )
            }
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun runReceiveLoop(udp: DatagramSocket) {
        val buf = ByteArray(1024)
        while (running.get() && !udp.isClosed) {
            try {
                val dp = DatagramPacket(buf, buf.size)
                udp.receive(dp)
                val pkt = PacketCodec.decodeFromBytes(dp.data, dp.length) ?: continue
                if (pkt.type == ProtocolConstants.TYPE_PONG) {
                    val now = System.currentTimeMillis()
                    lastServerPongMs.set(now)
                    if (pkt.clientTimestampMs > 0L && now >= pkt.clientTimestampMs) {
                        lastRttMs.set(now - pkt.clientTimestampMs)
                    }
                } else if (pkt.type == ProtocolConstants.TYPE_DISCONNECT) {
                    running.set(false)
                    onStatusChanged(
                        ClientStatusSnapshot(
                            state = ClientConnectionState.DISCONNECTED,
                            message = "Disconnected by PC"
                        )
                    )
                    break
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }
}
