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
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

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
    private var sessionKey: String? = null

    private val lastServerPongMs = AtomicLong(0L)
    private val lastRttMs = AtomicLong(0L)
    private val packetsSentCount = AtomicLong(0L)

    val isConnectedOrPairing: Boolean
        get() = running.get()

    fun connect(config: FlightClientConfig) {
        disconnect(sendPacket = false)
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
        val activeSessionKey = sessionKey

        if (wasRunning && sendPacket && currentSocket != null && !currentSocket.isClosed &&
            !activeSessionId.isNullOrBlank() && !activeSessionKey.isNullOrBlank()
        ) {
            Thread {
                try {
                    val pkt = FlightJoyPacket(
                        version = ProtocolConstants.VERSION,
                        type = ProtocolConstants.TYPE_DISCONNECT,
                        sessionId = activeSessionId,
                        timestampMs = System.currentTimeMillis()
                    )
                    PairingCrypto.signDisconnect(pkt, activeSessionKey)
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
        sessionKey = null

        if (wasRunning) {
            logger.info("Rede", "Sessão UDP encerrada pelo usuário.")
            onStatusChanged(
                ClientStatusSnapshot(
                    state = ClientConnectionState.DISCONNECTED,
                    message = "Desconectado"
                )
            )
        }
    }

    private fun runSessionLifecycle(config: FlightClientConfig) {
        try {
            val targetAddress = InetAddress.getByName(config.serverIp.trim())
            val targetPort = config.serverPort.coerceIn(1024, 65535)
            val pin = config.pairingPin.trim()

            onStatusChanged(
                ClientStatusSnapshot(
                    state = ClientConnectionState.PAIRING,
                    message = "Pareando com ${config.serverIp}:$targetPort..."
                )
            )
            logger.info("Rede", "Iniciando pareamento com ${config.serverIp}:$targetPort...")

            val udp = DatagramSocket()
            udp.soTimeout = 800
            udp.connect(targetAddress, targetPort)
            socket = udp

            val clientNonce = PairingCrypto.generateNonceHex(16)
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

            val pairReq = FlightJoyPacket(
                version = ProtocolConstants.VERSION,
                type = ProtocolConstants.TYPE_PAIR_REQ,
                deviceId = deviceId,
                deviceName = deviceModel,
                clientNonce = clientNonce,
                timestampMs = System.currentTimeMillis()
            )
            PairingCrypto.signPairReq(pairReq, pin)
            val reqBytes = PacketCodec.encodeToBytes(pairReq)

            var ackPacket: FlightJoyPacket? = null
            var pairErrorReason: String? = null
            val recvBuf = ByteArray(2048)

            for (attempt in 1..3) {
                if (!running.get()) return
                udp.send(DatagramPacket(reqBytes, reqBytes.size))
                try {
                    val respDatagram = DatagramPacket(recvBuf, recvBuf.size)
                    udp.receive(respDatagram)
                    val decoded = PacketCodec.decodeFromBytes(respDatagram.data, respDatagram.length)
                    if (decoded != null) {
                        if (decoded.type == ProtocolConstants.TYPE_PAIR_ERR) {
                            pairErrorReason = decoded.reason ?: "PIN recusado pelo servidor"
                            break
                        }
                        if (decoded.type == ProtocolConstants.TYPE_PAIR_ACK &&
                            PairingCrypto.verifyPairAck(decoded, pin)
                        ) {
                            ackPacket = decoded
                            break
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    logger.warn("Pareamento", "Tentativa $attempt/3 sem resposta de ${config.serverIp}:$targetPort...")
                }
            }

            if (pairErrorReason != null) {
                logger.error("Pareamento", "Recusado pelo PC: $pairErrorReason")
                running.set(false)
                udp.close()
                onStatusChanged(
                    ClientStatusSnapshot(
                        state = ClientConnectionState.ERROR_OR_TIMEOUT,
                        message = "Erro: $pairErrorReason"
                    )
                )
                return
            }

            if (ackPacket == null || ackPacket.sessionId.isNullOrBlank()) {
                logger.error("Pareamento", "Sem resposta do PC (${config.serverIp}:$targetPort). Verifique IP, Firewall e se o servidor está rodando.")
                running.set(false)
                udp.close()
                onStatusChanged(
                    ClientStatusSnapshot(
                        state = ClientConnectionState.ERROR_OR_TIMEOUT,
                        message = "Sem resposta do PC (${config.serverIp})"
                    )
                )
                return
            }

            val establishedSessionId = ackPacket.sessionId!!
            val derivedKey = PairingCrypto.deriveSessionKey(pin, deviceId, clientNonce, establishedSessionId)
            sessionId = establishedSessionId
            sessionKey = derivedKey
            lastServerPongMs.set(System.currentTimeMillis())
            packetsSentCount.set(0L)

            logger.info("Pareamento", "Conectado com sucesso! Sessão: ${establishedSessionId.take(8)}...")
            onStatusChanged(
                ClientStatusSnapshot(
                    state = ClientConnectionState.CONNECTED,
                    message = "Conectado (${config.sendRateHz} Hz)",
                    rttMs = 0L,
                    packetsSent = 0L
                )
            )

            // Inicia thread receptora para PONGs e detecção de desconexão
            udp.soTimeout = 500
            receiveThread = Thread({
                runReceiveLoop(udp, derivedKey)
            }, "FlightJoy-UDP-Recv").apply {
                isDaemon = true
                start()
            }

            // Loop contínuo de transmissão de baixa latência
            val intervalMs = (1000L / config.sendRateHz.coerceIn(20, 120)).coerceAtLeast(8L)
            var seq = 0L
            var lastPingMs = System.currentTimeMillis()

            while (running.get() && !udp.isClosed) {
                val loopStart = System.currentTimeMillis()

                // Verifica watchdog de resposta do servidor (3.5s)
                if (loopStart - lastServerPongMs.get() > 3500L) {
                    logger.warn("Watchdog", "Servidor Windows parou de responder (Timeout > 3.5s).")
                    running.set(false)
                    onStatusChanged(
                        ClientStatusSnapshot(
                            state = ClientConnectionState.ERROR_OR_TIMEOUT,
                            message = "Conexão Perdida com o PC"
                        )
                    )
                    break
                }

                seq++
                val statePacket = FlightJoyPacket(
                    version = ProtocolConstants.VERSION,
                    type = ProtocolConstants.TYPE_STATE,
                    sessionId = establishedSessionId,
                    sequence = seq,
                    timestampMs = loopStart,
                    pitchScaled = FlightJoyPacket.scaleAxis(controlState.pitch),
                    rollScaled = FlightJoyPacket.scaleAxis(controlState.roll),
                    yawScaled = FlightJoyPacket.scaleAxis(controlState.yaw),
                    throttleScaled = FlightJoyPacket.scaleAxis(controlState.throttle),
                    auxScaled = FlightJoyPacket.scaleAxis(controlState.aux),
                    buttonsMask = controlState.getButtonsMask()
                )
                PairingCrypto.signState(statePacket, derivedKey)
                val bytes = PacketCodec.encodeToBytes(statePacket)
                udp.send(DatagramPacket(bytes, bytes.size))
                val totalSent = packetsSentCount.incrementAndGet()

                // Envia PING a cada 1000ms e atualiza status na UI
                if (loopStart - lastPingMs >= 1000L) {
                    lastPingMs = loopStart
                    val ping = FlightJoyPacket(
                        version = ProtocolConstants.VERSION,
                        type = ProtocolConstants.TYPE_PING,
                        sessionId = establishedSessionId,
                        sequence = seq,
                        timestampMs = loopStart
                    )
                    PairingCrypto.signPing(ping, derivedKey)
                    val pingBytes = PacketCodec.encodeToBytes(ping)
                    udp.send(DatagramPacket(pingBytes, pingBytes.size))

                    onStatusChanged(
                        ClientStatusSnapshot(
                            state = ClientConnectionState.CONNECTED,
                            message = "Conectado • ${lastRttMs.get()} ms",
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
                logger.error("Rede", "Falha na comunicação UDP: ${ex.message}")
                running.set(false)
                onStatusChanged(
                    ClientStatusSnapshot(
                        state = ClientConnectionState.ERROR_OR_TIMEOUT,
                        message = "Erro Rede: ${ex.localizedMessage ?: "Falha UDP"}"
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

    private fun runReceiveLoop(udp: DatagramSocket, activeKey: String) {
        val buf = ByteArray(1024)
        while (running.get() && !udp.isClosed) {
            try {
                val dp = DatagramPacket(buf, buf.size)
                udp.receive(dp)
                val pkt = PacketCodec.decodeFromBytes(dp.data, dp.length) ?: continue
                if (pkt.type == ProtocolConstants.TYPE_PONG &&
                    PairingCrypto.verifyPong(pkt, activeKey)
                ) {
                    val now = System.currentTimeMillis()
                    lastServerPongMs.set(now)
                    if (pkt.clientTimestampMs > 0L && now >= pkt.clientTimestampMs) {
                        lastRttMs.set(now - pkt.clientTimestampMs)
                    }
                } else if (pkt.type == ProtocolConstants.TYPE_DISCONNECT) {
                    logger.warn("Rede", "Servidor Windows encerrou a sessão.")
                    running.set(false)
                    onStatusChanged(
                        ClientStatusSnapshot(
                            state = ClientConnectionState.DISCONNECTED,
                            message = "Servidor Desconectou"
                        )
                    )
                    break
                }
            } catch (_: SocketTimeoutException) {
                // Normal durante o loop de recepção
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }
}
