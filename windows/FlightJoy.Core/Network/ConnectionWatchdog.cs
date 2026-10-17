namespace FlightJoy.Core.Network;

/// <summary>
/// Monitora continuamente a chegada de pacotes do dispositivo pareado
/// e dispara evento de desconexão (fail-safe) quando o tempo limite expira.
/// </summary>
public sealed class ConnectionWatchdog
{
    private readonly object _sync = new();
    private long _lastActivityTimestampMs;
    private bool _isActiveSession;
    private bool _hasTimedOut;

    public int TimeoutMs { get; set; }

    public event Action? TimeoutDetected;

    public ConnectionWatchdog(int timeoutMs = 1000)
    {
        TimeoutMs = Math.Max(200, timeoutMs);
    }

    public void StartSession(long nowMs)
    {
        lock (_sync)
        {
            _lastActivityTimestampMs = nowMs;
            _isActiveSession = true;
            _hasTimedOut = false;
        }
    }

    public void RecordActivity(long nowMs)
    {
        lock (_sync)
        {
            _lastActivityTimestampMs = nowMs;
            _isActiveSession = true;
            _hasTimedOut = false;
        }
    }

    public void StopSession()
    {
        lock (_sync)
        {
            _isActiveSession = false;
            _hasTimedOut = false;
        }
    }

    public long GetElapsedSinceLastActivityMs(long nowMs)
    {
        lock (_sync)
        {
            if (!_isActiveSession && !_hasTimedOut)
                return 0;
            return Math.Max(0, nowMs - _lastActivityTimestampMs);
        }
    }

    /// <summary>
    /// Avalia se a sessão ativa ultrapassou o limite de inatividade.
    /// Retorna true exatamente na transição para timeout.
    /// </summary>
    public bool EvaluateTimeout(long nowMs)
    {
        bool triggered = false;
        lock (_sync)
        {
            if (_isActiveSession && !_hasTimedOut)
            {
                long elapsed = nowMs - _lastActivityTimestampMs;
                if (elapsed >= TimeoutMs)
                {
                    _hasTimedOut = true;
                    _isActiveSession = false;
                    triggered = true;
                }
            }
        }

        if (triggered)
        {
            TimeoutDetected?.Invoke();
        }

        return triggered;
    }
}
