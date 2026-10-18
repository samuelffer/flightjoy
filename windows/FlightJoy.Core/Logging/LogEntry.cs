namespace FlightJoy.Core.Logging;

public enum LogLevel
{
    Debug = 0,
    Info = 1,
    Warning = 2,
    Error = 3,
    Security = 4
}

public sealed record LogEntry(
    DateTimeOffset Timestamp,
    LogLevel Level,
    string Category,
    string Message)
{
    public override string ToString()
    {
        return $"[{Timestamp:HH:mm:ss.fff}] [{Level.ToString().ToUpperInvariant()}] [{Category}] {Message}";
    }
}
