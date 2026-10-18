using System.Collections.Concurrent;
using System.Text;

namespace FlightJoy.Core.Logging;

public sealed class FlightLogger : ILogSink
{
    private readonly ConcurrentQueue<LogEntry> _recentEntries = new();
    private readonly object _fileLock = new();
    private readonly string? _logDirectory;
    private readonly int _maxMemoryEntries;

    public event Action<LogEntry>? EntryLogged;

    public string? LogDirectory => _logDirectory;

    public FlightLogger(string? logDirectory = null, int maxMemoryEntries = 500)
    {
        _logDirectory = logDirectory;
        _maxMemoryEntries = Math.Max(50, maxMemoryEntries);

        if (!string.IsNullOrWhiteSpace(_logDirectory))
        {
            try
            {
                Directory.CreateDirectory(_logDirectory);
            }
            catch
            {
                // Ignora caso não seja possível criar pasta de logs no ambiente atual
            }
        }
    }

    public void Write(LogEntry entry)
    {
        _recentEntries.Enqueue(entry);
        while (_recentEntries.Count > _maxMemoryEntries && _recentEntries.TryDequeue(out _))
        {
        }

        if (!string.IsNullOrWhiteSpace(_logDirectory))
        {
            try
            {
                string fileName = $"flightjoy-{entry.Timestamp:yyyyMMdd}.log";
                string fullPath = Path.Combine(_logDirectory, fileName);
                lock (_fileLock)
                {
                    File.AppendAllText(fullPath, entry + Environment.NewLine, Encoding.UTF8);
                }
            }
            catch
            {
                // Falha de I/O não deve interromper o loop de telemetria
            }
        }

        EntryLogged?.Invoke(entry);
    }

    public void Info(string category, string message) =>
        Write(new LogEntry(DateTimeOffset.Now, LogLevel.Info, category, message));

    public void Warn(string category, string message) =>
        Write(new LogEntry(DateTimeOffset.Now, LogLevel.Warning, category, message));

    public void Error(string category, string message) =>
        Write(new LogEntry(DateTimeOffset.Now, LogLevel.Error, category, message));

    public void Security(string category, string message) =>
        Write(new LogEntry(DateTimeOffset.Now, LogLevel.Security, category, message));

    public IReadOnlyList<LogEntry> GetRecentEntries() => _recentEntries.ToArray();

    public void ClearMemory()
    {
        while (_recentEntries.TryDequeue(out _))
        {
        }
    }
}
