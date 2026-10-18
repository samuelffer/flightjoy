using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Media;
using System.Windows.Threading;
using FlightJoy.Core.Config;
using FlightJoy.Core.Control;
using FlightJoy.Core.Logging;
using FlightJoy.Core.Network;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Windows.ViewModels;

public sealed class MainViewModel : INotifyPropertyChanged, IDisposable
{
    private const double CrossCenter = 64.0;
    private const double CrossHalfArm = 48.0;

    private readonly Dispatcher _dispatcher;
    private readonly DispatcherTimer _smoothUiTimer;
    private readonly FlightLogger _logger;
    private readonly VJoyFeederService _feeder;
    private readonly ControlPipeline _pipeline;
    private readonly UdpControlServer _server;
    private readonly AppConfig _config;

    private string _connectionBadgeText = "Waiting for phone...";
    private string _vjoyBadgeText = "Checking vJoy...";
    private string _telemetryText = "0.0 Hz";
    private string _activeButtonsText = "None";
    private bool _isServerRunning;
    private bool _isLogsVisible;
    private bool _isPhoneConnected;

    private double _targetPitchNorm;
    private double _targetRollNorm;
    private double _targetRudderNorm;

    private double _displayPitchNorm;
    private double _displayRollNorm;
    private double _displayRudderNorm;

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

        _smoothUiTimer = new DispatcherTimer(DispatcherPriority.Render, _dispatcher)
        {
            Interval = TimeSpan.FromMilliseconds(16)
        };
        _smoothUiTimer.Tick += OnSmoothUiTick;
        _smoothUiTimer.Start();

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

    public string ServerButtonText => IsServerRunning ? "Stop" : "Start";

    public bool IsPhoneConnected
    {
        get => _isPhoneConnected;
        private set
        {
            if (_isPhoneConnected != value)
            {
                _isPhoneConnected = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(StatusDotBrush));
            }
        }
    }

    public Brush StatusDotBrush => _isPhoneConnected
        ? new SolidColorBrush(Color.FromRgb(0x34, 0xD3, 0x99))
        : new SolidColorBrush(Color.FromRgb(0x55, 0x55, 0x55));

    // Real-time smoothly interpolated coordinates for the 128x128 Pitch/Roll Cross (Center = 64,64)
    public double StickDotLeft => CrossCenter + (_displayRollNorm * CrossHalfArm) - 5.0;
    public double StickDotTop => CrossCenter + (_displayPitchNorm * CrossHalfArm) - 5.0;
    public double StickCenterX => CrossCenter + (_displayRollNorm * CrossHalfArm);
    public double StickCenterY => CrossCenter + (_displayPitchNorm * CrossHalfArm);

    // Real-time smoothly interpolated coordinate for the horizontal Rudder line (Width 128, Center = 64)
    public double RudderDotLeft => CrossCenter + (_displayRudderNorm * CrossHalfArm) - 4.5;

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
            _logger.Error("Network", $"Port {_config.UdpPort} error: {ex.Message}");
            ConnectionBadgeText = $"Port error {_config.UdpPort}";
            IsServerRunning = false;
        }
    }

    public void StopServer()
    {
        _server.Stop();
        IsServerRunning = false;
        IsPhoneConnected = false;
        ResetVisualAxes();
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

    private void ResetVisualAxes()
    {
        _targetPitchNorm = 0.0;
        _targetRollNorm = 0.0;
        _targetRudderNorm = 0.0;
    }

    private void OnSmoothUiTick(object? sender, EventArgs e)
    {
        const double lerpFactor = 0.32;
        const double epsilon = 0.0005;

        double dp = _targetPitchNorm - _displayPitchNorm;
        double dr = _targetRollNorm - _displayRollNorm;
        double dy = _targetRudderNorm - _displayRudderNorm;

        if (Math.Abs(dp) > epsilon || Math.Abs(dr) > epsilon || Math.Abs(dy) > epsilon)
        {
            _displayPitchNorm = Math.Abs(dp) <= epsilon ? _targetPitchNorm : _displayPitchNorm + (dp * lerpFactor);
            _displayRollNorm = Math.Abs(dr) <= epsilon ? _targetRollNorm : _displayRollNorm + (dr * lerpFactor);
            _displayRudderNorm = Math.Abs(dy) <= epsilon ? _targetRudderNorm : _displayRudderNorm + (dy * lerpFactor);
            NotifyCrossChanged();
        }
    }

    private void NotifyCrossChanged()
    {
        OnPropertyChanged(nameof(StickDotLeft));
        OnPropertyChanged(nameof(StickDotTop));
        OnPropertyChanged(nameof(StickCenterX));
        OnPropertyChanged(nameof(StickCenterY));
        OnPropertyChanged(nameof(RudderDotLeft));
    }

    private void OnConnectionStateChanged(ConnectionState state, ConnectedDeviceInfo? device)
    {
        _dispatcher.BeginInvoke(() =>
        {
            IsPhoneConnected = state == ConnectionState.PairedAndConnected;
            if (!IsPhoneConnected)
            {
                ResetVisualAxes();
            }

            ConnectionBadgeText = state switch
            {
                ConnectionState.Stopped => "Server stopped",
                ConnectionState.Listening => "Waiting for phone...",
                ConnectionState.PairedAndConnected => string.IsNullOrWhiteSpace(device?.DeviceName)
                    ? "Phone connected"
                    : $"Phone connected — {device.DeviceName}",
                ConnectionState.DisconnectedTimeout => "Phone disconnected",
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

            if (state.ProcessedSourceAxes.TryGetValue(SourceAxis.Pitch, out double p))
                _targetPitchNorm = Math.Clamp(p, -1.0, 1.0);
            if (state.ProcessedSourceAxes.TryGetValue(SourceAxis.Roll, out double r))
                _targetRollNorm = Math.Clamp(r, -1.0, 1.0);
            if (state.ProcessedSourceAxes.TryGetValue(SourceAxis.Yaw, out double y))
                _targetRudderNorm = Math.Clamp(y, -1.0, 1.0);

            var pressedList = state.TargetVJoyButtons
                .Where(kv => kv.Value)
                .Select(kv => $"B{kv.Key}")
                .ToList();

            ActiveButtonsText = pressedList.Count > 0
                ? string.Join(", ", pressedList)
                : "None";
        });
    }

    private void OnTelemetryUpdated(TelemetryStats stats)
    {
        _dispatcher.BeginInvoke(() =>
        {
            TelemetryText = $"{stats.CurrentPacketsPerSecond:0.0} Hz  •  Packets: {stats.TotalPacketsReceived}";
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
        _smoothUiTimer.Stop();
        _server.Dispose();
        _feeder.Dispose();
    }

    private void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
