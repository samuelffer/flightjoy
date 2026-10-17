using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Threading;
using FlightJoy.Core.Config;
using FlightJoy.Core.Control;
using FlightJoy.Core.Logging;
using FlightJoy.Core.Network;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Windows.ViewModels;

public sealed class MainViewModel : INotifyPropertyChanged, IDisposable
{
    private readonly Dispatcher _dispatcher;
    private readonly FlightLogger _logger;
    private readonly VJoyFeederService _feeder;
    private readonly ControlPipeline _pipeline;
    private readonly UdpControlServer _server;
    private readonly AppConfig _config;

    private string _connectionBadgeText = "Aguardando conexão...";
    private string _vjoyBadgeText = "Verificando vJoy...";
    private string _telemetryText = "0.0 Hz";
    private string _activeButtonsText = "Nenhum";
    private bool _isServerRunning;
    private bool _isLogsVisible;

    public event PropertyChangedEventHandler? PropertyChanged;

    public ObservableCollection<AxisMonitorItem> Axes { get; } = [];
    public ObservableCollection<string> Logs { get; } = [];

    public IReadOnlyList<uint> AvailableDeviceIds { get; } =
        Enumerable.Range(1, 16).Select(i => (uint)i).ToArray();

    public string MachineName { get; } = UdpControlServer.GetSafeMachineName();
    public string LocalIpSummary { get; }

    public MainViewModel(Dispatcher dispatcher)
    {
        _dispatcher = dispatcher;
        string logDir = Path.Combine(AppContext.BaseDirectory, "logs");
        _logger = new FlightLogger(logDir);
        _logger.EntryLogged += OnEntryLogged;

        _config = ConfigManager.LoadOrCreateDefault();
        _feeder = new VJoyFeederService(_logger);
        _pipeline = new ControlPipeline(_config);
        _server = new UdpControlServer(_config, _pipeline, _feeder, _logger);

        _server.ConnectionStateChanged += OnConnectionStateChanged;
        _server.JoystickStateUpdated += OnJoystickStateUpdated;
        _server.TelemetryUpdated += OnTelemetryUpdated;

        LocalIpSummary = string.Join("  •  ", UdpControlServer.GetLocalLanIpAddresses());

        BuildMonitorAxes();

        foreach (LogEntry existing in _logger.GetRecentEntries())
        {
            Logs.Add(existing.ToString());
        }

        AcquireVJoyDevice();
        StartServer();
    }

    public int UdpPort
    {
        get => _config.UdpPort;
        set
        {
            int clamped = Math.Clamp(value, 1024, 65535);
            if (_config.UdpPort != clamped)
            {
                _config.UdpPort = clamped;
                ConfigManager.Save(_config);
                OnPropertyChanged();
            }
        }
    }

    public uint VJoyDeviceId
    {
        get => _config.VJoyDeviceId;
        set
        {
            if (_config.VJoyDeviceId != value)
            {
                _config.VJoyDeviceId = value;
                OnPropertyChanged();
                AcquireVJoyDevice();
                ConfigManager.Save(_config);
            }
        }
    }

    public bool IsServerRunning
    {
        get => _isServerRunning;
        private set
        {
            if (_isServerRunning != value)
            {
                _isServerRunning = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(ServerButtonText));
            }
        }
    }

    public string ServerButtonText => IsServerRunning ? "Parar" : "Iniciar";

    public bool IsLogsVisible
    {
        get => _isLogsVisible;
        set
        {
            if (_isLogsVisible != value)
            {
                _isLogsVisible = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(LogsVisibility));
            }
        }
    }

    public Visibility LogsVisibility => IsLogsVisible ? Visibility.Visible : Visibility.Collapsed;

    public string ConnectionBadgeText
    {
        get => _connectionBadgeText;
        private set
        {
            if (_connectionBadgeText != value)
            {
                _connectionBadgeText = value;
                OnPropertyChanged();
            }
        }
    }

    public string VJoyBadgeText
    {
        get => _vjoyBadgeText;
        private set
        {
            if (_vjoyBadgeText != value)
            {
                _vjoyBadgeText = value;
                OnPropertyChanged();
            }
        }
    }

    public string TelemetryText
    {
        get => _telemetryText;
        private set
        {
            if (_telemetryText != value)
            {
                _telemetryText = value;
                OnPropertyChanged();
            }
        }
    }

    public string ActiveButtonsText
    {
        get => _activeButtonsText;
        private set
        {
            if (_activeButtonsText != value)
            {
                _activeButtonsText = value;
                OnPropertyChanged();
            }
        }
    }

    private void BuildMonitorAxes()
    {
        Axes.Clear();
        foreach (AxisMappingConfig axisConfig in _config.AxisMappings)
        {
            if (axisConfig.Source == SourceAxis.Aux)
                continue;

            Axes.Add(new AxisMonitorItem(axisConfig));
        }
    }

    public void AcquireVJoyDevice()
    {
        _feeder.Acquire(_config.VJoyDeviceId);
        VJoyBadgeText = _feeder.StatusMessage;
    }

    public void StartServer()
    {
        try
        {
            _server.UpdateConfig(_config);
            _server.Start();
            IsServerRunning = true;
        }
        catch (Exception ex)
        {
            _logger.Error("Rede", $"Erro na porta {_config.UdpPort}: {ex.Message}");
            ConnectionBadgeText = $"Erro porta {_config.UdpPort}";
            IsServerRunning = false;
        }
    }

    public void StopServer()
    {
        _server.Stop();
        IsServerRunning = false;
    }

    public void ToggleServer()
    {
        if (IsServerRunning)
            StopServer();
        else
            StartServer();
    }

    public void ToggleLogs()
    {
        IsLogsVisible = !IsLogsVisible;
    }

    private void OnConnectionStateChanged(ConnectionState state, ConnectedDeviceInfo? device)
    {
        _dispatcher.BeginInvoke(() =>
        {
            ConnectionBadgeText = state switch
            {
                ConnectionState.Stopped => "Servidor Parado",
                ConnectionState.Listening => "Aguardando Celular...",
                ConnectionState.PairedAndConnected => $"Conectado — {device?.DeviceName}",
                ConnectionState.DisconnectedTimeout => "Desconectado (Sinal Perdido)",
                _ => state.ToString()
            };
        });
    }

    private void OnJoystickStateUpdated(ProcessedJoystickState state)
    {
        _dispatcher.BeginInvoke(() =>
        {
            foreach (AxisMonitorItem axisItem in Axes)
            {
                if (state.ProcessedSourceAxes.TryGetValue(axisItem.Source, out double proc))
                {
                    axisItem.ProcessedNormalized = proc;
                }
            }

            var pressedList = state.TargetVJoyButtons
                .Where(kv => kv.Value)
                .Select(kv => $"B{kv.Key}")
                .ToList();

            ActiveButtonsText = pressedList.Count > 0
                ? string.Join(", ", pressedList)
                : "Nenhum";
        });
    }

    private void OnTelemetryUpdated(TelemetryStats stats)
    {
        _dispatcher.BeginInvoke(() =>
        {
            TelemetryText = $"{stats.CurrentPacketsPerSecond:0.0} Hz  •  Pacotes: {stats.TotalPacketsReceived}";
        });
    }

    private void OnEntryLogged(LogEntry entry)
    {
        _dispatcher.BeginInvoke(() =>
        {
            Logs.Add(entry.ToString());
            while (Logs.Count > 200)
            {
                Logs.RemoveAt(0);
            }
        });
    }

    public void Dispose()
    {
        _server.Dispose();
        _feeder.Dispose();
    }

    private void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
