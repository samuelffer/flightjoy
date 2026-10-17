using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using FlightJoy.Core.Config;
using FlightJoy.Core.Control;
using FlightJoy.Core.Logging;
using FlightJoy.Core.Protocol;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Network;

/// <summary>
/// Servidor UDP de baixa latência responsável pelo pareamento HMAC-SHA256,
/// recepção contínua de controles, detecção de desconexão via Watchdog e envio ao vJoy.
/// </summary>
public sealed class UdpControlServer : IDisposable
{
    private readonly FlightLogger _logger;
    private readonly ControlPipeline _pipeline;
    private readonly IVJoyFeeder _feeder;
    private readonly ConnectionWatchdog _watchdog;
    private readonly object _sessionLock = new();

    private AppConfig _config;
    private UdpClient? _udpClient;
    private CancellationTokenSource? _cts;
    private Task? _receiveLoopTask;
    private Task? _watchdogLoopTask;

    private string? _activeSessionId;
    private string? _activeSessionKey;
    private IPEndPoint? _pairedEndpoint;
    private ConnectedDeviceInfo? _connectedDevice;
    private long _lastSequenceNumber;

    private long _totalPacketsReceived;
    private long _outOfOrderOrDropped;
    private long _invalidAuthPackets;
    private int _packetsInCurrentWindow;
    private long _lastRateCalcTickMs;
    private double _currentPacketsPerSecond;

    public ConnectionState State { get; private set; } = ConnectionState.Stopped;
    public ConnectedDeviceInfo? ConnectedDevice => _connectedDevice;

    public event Action<ConnectionState, ConnectedDeviceInfo?>? ConnectionStateChanged;
    public event Action<ProcessedJoystickState>? JoystickStateUpdated;
    public event Action<TelemetryStats>? TelemetryUpdated;

    public UdpControlServer(
        AppConfig config,
        ControlPipeline pipeline,
        IVJoyFeeder feeder,
        FlightLogger logger)
    {
        _config = config ?? throw new ArgumentNullException(nameof(config));
        _pipeline = pipeline ?? throw new ArgumentNullException(nameof(pipeline));
        _feeder = feeder ?? throw new ArgumentNullException(nameof(feeder));
        _logger = logger ?? throw new ArgumentNullException(nameof(logger));
        _watchdog = new ConnectionWatchdog(_config.WatchdogTimeoutMs);
        _watchdog.TimeoutDetected += OnWatchdogTimeout;
    }

    public void UpdateConfig(AppConfig config)
    {
        ArgumentNullException.ThrowIfNull(config);
        lock (_sessionLock)
        {
            _config = config;
            _watchdog.TimeoutMs = config.WatchdogTimeoutMs;
        }
    }

    public void Start()
    {
        Stop();

        _cts = new CancellationTokenSource();
        _udpClient = new UdpClient(_config.UdpPort);
        _udpClient.Client.ReceiveBufferSize = 65536;

        // Evita SocketException (WSAECONNRESET 10054) no Windows quando o cliente remoto fecha a porta UDP
        if (OperatingSystem.IsWindows())
        {
            const int SIO_UDP_CONNRESET = -1744830452;
            try
            {
                _udpClient.Client.IOControl((IOControlCode)SIO_UDP_CONNRESET, [0, 0, 0, 0], null);
            }
            catch
            {
                // Ignora se não suportado
            }
        }

        _lastRateCalcTickMs = Environment.TickCount64;
        SetState(ConnectionState.Listening, null);
        _logger.Info("Rede", $"Servidor UDP iniciado na porta {_config.UdpPort}. Aguardando pareamento (PIN: {_config.PairingPin}).");

        CancellationToken token = _cts.Token;
        _receiveLoopTask = Task.Run(() => ReceiveLoopAsync(token), token);
        _watchdogLoopTask = Task.Run(() => WatchdogLoopAsync(token), token);
    }

    public void Stop()
    {
        if (_cts != null)
        {
            try
            {
                _cts.Cancel();
            }
            catch
            {
            }
        }

        try
        {
            _udpClient?.Close();
            _udpClient?.Dispose();
        }
        catch
        {
        }

        _udpClient = null;
        _watchdog.StopSession();

        lock (_sessionLock)
        {
            _activeSessionId = null;
            _activeSessionKey = null;
            _pairedEndpoint = null;
            _connectedDevice = null;
            _lastSequenceNumber = 0;
        }

        if (State != ConnectionState.Stopped)
        {
            SetState(ConnectionState.Stopped, null);
            _logger.Info("Rede", "Servidor UDP parado.");
        }
    }

    private async Task ReceiveLoopAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _udpClient != null)
        {
            try
            {
                UdpReceiveResult result = await _udpClient.ReceiveAsync(ct).ConfigureAwait(false);
                FlightJoyPacket? response = ProcessDatagram(result.Buffer, result.RemoteEndPoint, Environment.TickCount64);
                if (response != null && _udpClient != null)
                {
                    byte[] responseBytes = PacketSerializer.SerializeToUtf8Bytes(response);
                    await _udpClient.SendAsync(responseBytes, responseBytes.Length, result.RemoteEndPoint).ConfigureAwait(false);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }
            catch (SocketException ex)
            {
                if (!ct.IsCancellationRequested)
                {
                    _logger.Warn("Rede", $"Aviso de socket UDP: {ex.SocketErrorCode} ({ex.Message})");
                }
            }
            catch (Exception ex)
            {
                _logger.Error("Rede", $"Erro ao processar pacote UDP: {ex.Message}");
            }
        }
    }

    private async Task WatchdogLoopAsync(CancellationToken ct)
    {
        using PeriodicTimer timer = new(TimeSpan.FromMilliseconds(100));
        while (!ct.IsCancellationRequested && await timer.WaitForNextTickAsync(ct).ConfigureAwait(false))
        {
            long nowTick = Environment.TickCount64;
            _watchdog.EvaluateTimeout(nowTick);

            long elapsedWindow = nowTick - _lastRateCalcTickMs;
            if (elapsedWindow >= 500)
            {
                int count = Interlocked.Exchange(ref _packetsInCurrentWindow, 0);
                _currentPacketsPerSecond = (count * 1000.0) / elapsedWindow;
                _lastRateCalcTickMs = nowTick;
            }

            TelemetryUpdated?.Invoke(GetTelemetrySnapshot(nowTick));
        }
    }

    /// <summary>
    /// Processa um datagrama UDP recebido e retorna opcionalmente um pacote de resposta (PAIR_ACK, PAIR_ERR, PONG).
    /// Método público para permitir testes integrados determinísticos sem abrir portas de rede reais.
    /// </summary>
    public FlightJoyPacket? ProcessDatagram(ReadOnlySpan<byte> datagram, IPEndPoint remoteEndPoint, long nowTickMs)
    {
        FlightJoyPacket? packet = PacketSerializer.Deserialize(datagram);
        if (packet == null || packet.Version != ProtocolConstants.ProtocolVersion)
            return null;

        switch (packet.Type)
        {
            case ProtocolConstants.TypePairReq:
                return HandlePairRequest(packet, remoteEndPoint, nowTickMs);

            case ProtocolConstants.TypeState:
                return HandleStatePacket(packet, remoteEndPoint, nowTickMs);

            case ProtocolConstants.TypePing:
                return HandlePingPacket(packet, nowTickMs);

            case ProtocolConstants.TypeDisconnect:
                HandleDisconnectPacket(packet, remoteEndPoint);
                return null;

            default:
                return null;
        }
    }

    private FlightJoyPacket HandlePairRequest(FlightJoyPacket packet, IPEndPoint remoteEndPoint, long nowTickMs)
    {
        string pin;
        lock (_sessionLock)
        {
            pin = _config.PairingPin;
        }

        if (!PairingAuthenticator.VerifyPairReq(packet, pin))
        {
            Interlocked.Increment(ref _invalidAuthPackets);
            _logger.Security("Pareamento", $"Tentativa de pareamento recusada de {remoteEndPoint} (PIN incorreto ou assinatura inválida).");
            return new FlightJoyPacket
            {
                Version = ProtocolConstants.ProtocolVersion,
                Type = ProtocolConstants.TypePairErr,
                Reason = "PIN de pareamento inválido",
                TimestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
        }

        string sessionId = PairingAuthenticator.GenerateNonceHex(16);
        string sessionKey = PairingAuthenticator.DeriveSessionKey(
            pin,
            packet.DeviceId!,
            packet.ClientNonce!,
            sessionId);

        ConnectedDeviceInfo deviceInfo = new(
            DeviceId: packet.DeviceId!,
            DeviceName: string.IsNullOrWhiteSpace(packet.DeviceName) ? "Android FlightJoy" : packet.DeviceName!,
            RemoteEndpoint: remoteEndPoint.ToString(),
            SessionId: sessionId,
            ConnectedAtUtc: DateTimeOffset.UtcNow);

        lock (_sessionLock)
        {
            _activeSessionId = sessionId;
            _activeSessionKey = sessionKey;
            _pairedEndpoint = remoteEndPoint;
            _connectedDevice = deviceInfo;
            _lastSequenceNumber = 0;
        }

        _watchdog.StartSession(nowTickMs);
        SetState(ConnectionState.PairedAndConnected, deviceInfo);
        _logger.Info("Pareamento", $"Dispositivo '{deviceInfo.DeviceName}' ({remoteEndPoint}) pareado com sucesso! Sessão: {sessionId[..8]}...");

        FlightJoyPacket ack = new()
        {
            Version = ProtocolConstants.ProtocolVersion,
            Type = ProtocolConstants.TypePairAck,
            DeviceId = packet.DeviceId,
            ClientNonce = packet.ClientNonce,
            SessionId = sessionId,
            TimestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
        PairingAuthenticator.SignPairAck(ack, pin);
        return ack;
    }

    private FlightJoyPacket? HandleStatePacket(FlightJoyPacket packet, IPEndPoint remoteEndPoint, long nowTickMs)
    {
        string? activeSessionId;
        string? activeSessionKey;
        lock (_sessionLock)
        {
            activeSessionId = _activeSessionId;
            activeSessionKey = _activeSessionKey;
        }

        if (string.IsNullOrEmpty(activeSessionId) ||
            string.IsNullOrEmpty(activeSessionKey) ||
            !string.Equals(packet.SessionId, activeSessionId, StringComparison.Ordinal) ||
            !PairingAuthenticator.VerifyState(packet, activeSessionKey))
        {
            Interlocked.Increment(ref _invalidAuthPackets);
            return null;
        }

        lock (_sessionLock)
        {
            if (packet.Sequence <= _lastSequenceNumber)
            {
                Interlocked.Increment(ref _outOfOrderOrDropped);
                return null;
            }

            if (_lastSequenceNumber > 0 && packet.Sequence > _lastSequenceNumber + 1)
            {
                long dropped = packet.Sequence - _lastSequenceNumber - 1;
                Interlocked.Add(ref _outOfOrderOrDropped, dropped);
            }

            _lastSequenceNumber = packet.Sequence;
            _pairedEndpoint = remoteEndPoint;
        }

        Interlocked.Increment(ref _totalPacketsReceived);
        Interlocked.Increment(ref _packetsInCurrentWindow);
        _watchdog.RecordActivity(nowTickMs);

        if (State != ConnectionState.PairedAndConnected)
        {
            SetState(ConnectionState.PairedAndConnected, _connectedDevice);
        }

        ProcessedJoystickState processed = _pipeline.ApplyPacket(packet, _feeder);
        JoystickStateUpdated?.Invoke(processed);

        // Responde com PONG a cada 30 pacotes (~500ms em 60Hz) para telemetria contínua de RTT no Android
        if (packet.Sequence % 30 == 0)
        {
            FlightJoyPacket pong = new()
            {
                Version = ProtocolConstants.ProtocolVersion,
                Type = ProtocolConstants.TypePong,
                SessionId = activeSessionId,
                Sequence = packet.Sequence,
                ClientTimestampMs = packet.TimestampMs,
                TimestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            };
            PairingAuthenticator.SignPong(pong, activeSessionKey);
            return pong;
        }

        return null;
    }

    private FlightJoyPacket? HandlePingPacket(FlightJoyPacket packet, long nowTickMs)
    {
        string? activeSessionId;
        string? activeSessionKey;
        lock (_sessionLock)
        {
            activeSessionId = _activeSessionId;
            activeSessionKey = _activeSessionKey;
        }

        if (string.IsNullOrEmpty(activeSessionId) ||
            string.IsNullOrEmpty(activeSessionKey) ||
            !string.Equals(packet.SessionId, activeSessionId, StringComparison.Ordinal) ||
            !PairingAuthenticator.VerifyPing(packet, activeSessionKey))
        {
            Interlocked.Increment(ref _invalidAuthPackets);
            return null;
        }

        _watchdog.RecordActivity(nowTickMs);

        FlightJoyPacket pong = new()
        {
            Version = ProtocolConstants.ProtocolVersion,
            Type = ProtocolConstants.TypePong,
            SessionId = activeSessionId,
            Sequence = packet.Sequence,
            ClientTimestampMs = packet.TimestampMs,
            TimestampMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
        PairingAuthenticator.SignPong(pong, activeSessionKey);
        return pong;
    }

    private void HandleDisconnectPacket(FlightJoyPacket packet, IPEndPoint remoteEndPoint)
    {
        string? activeSessionId;
        string? activeSessionKey;
        bool resetOnDisconnect;
        lock (_sessionLock)
        {
            activeSessionId = _activeSessionId;
            activeSessionKey = _activeSessionKey;
            resetOnDisconnect = _config.ResetAxesOnDisconnect;
        }

        if (string.IsNullOrEmpty(activeSessionId) ||
            string.IsNullOrEmpty(activeSessionKey) ||
            !string.Equals(packet.SessionId, activeSessionId, StringComparison.Ordinal) ||
            !PairingAuthenticator.VerifyDisconnect(packet, activeSessionKey))
        {
            return;
        }

        _watchdog.StopSession();
        lock (_sessionLock)
        {
            _activeSessionId = null;
            _activeSessionKey = null;
            _connectedDevice = null;
            _lastSequenceNumber = 0;
        }

        if (resetOnDisconnect)
        {
            _feeder.ResetToNeutral();
        }

        SetState(ConnectionState.Listening, null);
        _logger.Info("Rede", $"Dispositivo em {remoteEndPoint} encerrou a conexão normalmente.");
    }

    private void OnWatchdogTimeout()
    {
        bool resetOnDisconnect;
        ConnectedDeviceInfo? previousDevice;
        lock (_sessionLock)
        {
            resetOnDisconnect = _config.ResetAxesOnDisconnect;
            previousDevice = _connectedDevice;
        }

        if (resetOnDisconnect)
        {
            _feeder.ResetToNeutral();
        }

        SetState(ConnectionState.DisconnectedTimeout, previousDevice);
        _logger.Warn("Watchdog", $"Perda de conexão detectada com '{previousDevice?.DeviceName ?? "Android"}' após {_watchdog.TimeoutMs} ms sem pacotes. Controles neutralizados (Fail-Safe).");
    }

    private void SetState(ConnectionState newState, ConnectedDeviceInfo? deviceInfo)
    {
        State = newState;
        ConnectionStateChanged?.Invoke(newState, deviceInfo);
    }

    public TelemetryStats GetTelemetrySnapshot(long nowTickMs)
    {
        return new TelemetryStats
        {
            TotalPacketsReceived = Interlocked.Read(ref _totalPacketsReceived),
            OutOfOrderOrDroppedPackets = Interlocked.Read(ref _outOfOrderOrDropped),
            InvalidAuthPackets = Interlocked.Read(ref _invalidAuthPackets),
            LastSequenceNumber = Interlocked.Read(ref _lastSequenceNumber),
            CurrentPacketsPerSecond = Math.Round(_currentPacketsPerSecond, 1),
            LastPacketAgeMs = _watchdog.GetElapsedSinceLastActivityMs(nowTickMs)
        };
    }

    public static IReadOnlyList<string> GetLocalLanIpAddresses()
    {
        var result = new List<string>();
        try
        {
            foreach (NetworkInterface ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up ||
                    ni.NetworkInterfaceType == NetworkInterfaceType.Loopback)
                {
                    continue;
                }

                foreach (UnicastIPAddressInformation ip in ni.GetIPProperties().UnicastAddresses)
                {
                    if (ip.Address.AddressFamily == AddressFamily.InterNetwork &&
                        !IPAddress.IsLoopback(ip.Address))
                    {
                        result.Add(ip.Address.ToString());
                    }
                }
            }
        }
        catch
        {
        }

        if (result.Count == 0)
        {
            result.Add("127.0.0.1");
        }

        return result.Distinct().ToList();
    }

    public void Dispose()
    {
        Stop();
    }
}
