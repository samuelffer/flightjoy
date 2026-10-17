using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;
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
    private AppConfig _config;

    private string _connectionBadgeText = "Iniciando...";
    private string _vjoyBadgeText = "Verificando vJoy...";
    private string _telemetryText = "0.0 Hz | Seq: 0 | Perdas: 0";
    private string _calibrationText = "Centro PC: Pitch 0.0% | Roll 0.0% | Yaw 0.0%";
    private bool _isServerRunning;

    public event PropertyChangedEventHandler? PropertyChanged;

    public ObservableCollection<AxisMonitorItem> Axes { get; } = [];
    public ObservableCollection<ButtonMonitorItem> Buttons { get; } = [];
    public ObservableCollection<string> Logs { get; } = [];

    public IReadOnlyList<VJoyAxisUsage> AvailableVJoyAxes { get; } =
        Enum.GetValues<VJoyAxisUsage>();

    public IReadOnlyList<int> AvailableVJoyButtons { get; } =
        Enumerable.Range(1, 32).ToArray();

    public IReadOnlyList<uint> AvailableDeviceIds { get; } =
        Enumerable.Range(1, 16).Select(i => (uint)i).ToArray();

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

        LocalIpSummary = string.Join("  |  ", UdpControlServer.GetLocalLanIpAddresses());

        RebuildMappingCollections();

        foreach (LogEntry existing in _logger.GetRecentEntries())
        {
            Logs.Add(existing.ToString());
        }

        AcquireVJoyDevice();
        StartServer();
    }

    public string LogDirectoryPath => _logger.LogDirectory ?? AppContext.BaseDirectory;

    public int UdpPort
    {
        get => _config.UdpPort;
        set
        {
            int clamped = Math.Clamp(value, 1024, 65535);
            if (_config.UdpPort != clamped)
            {
                _config.UdpPort = clamped;
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
                SaveConfiguration();
            }
        }
    }

    public string PairingPin
    {
        get => _config.PairingPin;
        set
        {
            string cleaned = (value ?? string.Empty).Trim();
            if (cleaned.Length >= 4 && _config.PairingPin != cleaned)
            {
                _config.PairingPin = cleaned;
                _server.UpdateConfig(_config);
                OnPropertyChanged();
            }
        }
    }

    public int WatchdogTimeoutMs
    {
        get => _config.WatchdogTimeoutMs;
        set
        {
            int clamped = Math.Clamp(value, 200, 10000);
            if (_config.WatchdogTimeoutMs != clamped)
            {
                _config.WatchdogTimeoutMs = clamped;
                _server.UpdateConfig(_config);
                OnPropertyChanged();
            }
        }
    }

    public bool ResetAxesOnDisconnect
    {
        get => _config.ResetAxesOnDisconnect;
        set
        {
            if (_config.ResetAxesOnDisconnect != value)
            {
                _config.ResetAxesOnDisconnect = value;
                _server.UpdateConfig(_config);
                OnPropertyChanged();
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

    public string ServerButtonText => IsServerRunning ? "Parar Servidor UDP" : "Iniciar Servidor UDP";

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

    public string CalibrationText
    {
        get => _calibrationText;
        private set
        {
            if (_calibrationText != value)
            {
                _calibrationText = value;
                OnPropertyChanged();
            }
        }
    }

    private void RebuildMappingCollections()
    {
        Axes.Clear();
        foreach (AxisMappingConfig axisConfig in _config.AxisMappings)
        {
            var item = new AxisMonitorItem(axisConfig);
            item.MappingChanged += () => _pipeline.UpdateConfig(_config);
            Axes.Add(item);
        }

        Buttons.Clear();
        foreach (ButtonMappingConfig btnConfig in _config.ButtonMappings)
        {
            var item = new ButtonMonitorItem(btnConfig);
            item.MappingChanged += () => _pipeline.UpdateConfig(_config);
            Buttons.Add(item);
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
            _logger.Error("Rede", $"Não foi possível iniciar servidor na porta {_config.UdpPort}: {ex.Message}");
            ConnectionBadgeText = $"Erro Porta {_config.UdpPort}";
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

    public void GenerateNewPin()
    {
        PairingPin = PairingAuthenticator.GenerateSixDigitPin();
        SaveConfiguration();
        _logger.Info("Segurança", $"Novo PIN de pareamento gerado: {PairingPin}");
    }

    public void CalibrateCenterOnPc()
    {
        _pipeline.CalibrateCenterFromLastReading();
        var cal = _pipeline.Calibration;
        CalibrationText = $"Centro PC: Pitch {cal.PitchOffset * 100:+0.0;-0.0;0.0}% | Roll {cal.RollOffset * 100:+0.0;-0.0;0.0}% | Yaw {cal.YawOffset * 100:+0.0;-0.0;0.0}%";
        _logger.Info("Calibração", $"Centro calibrado no PC ({CalibrationText}).");
    }

    public void ResetPcCalibration()
    {
        _pipeline.ResetCalibration();
        _feeder.ResetToNeutral();
        CalibrationText = "Centro PC: Pitch 0.0% | Roll 0.0% | Yaw 0.0%";
        _logger.Info("Calibração", "Offsets de calibração do PC zerados e eixos neutralizados.");
    }

    public void SaveConfiguration()
    {
        _pipeline.UpdateConfig(_config);
        _server.UpdateConfig(_config);
        ConfigManager.Save(_config);
        _logger.Info("Config", "Configurações de rede e mapeamento salvas em flightjoy.config.json.");
    }

    public void RestoreDefaults()
    {
        _config.AxisMappings = AppConfig.CreateDefaultAxisMappings();
        _config.ButtonMappings = AppConfig.CreateDefaultButtonMappings();
        _pipeline.UpdateConfig(_config);
        RebuildMappingCollections();
        ConfigManager.Save(_config);
        _logger.Info("Config", "Mapeamento de eixos e botões restaurado para o padrão de fábrica.");
    }

    public void ClearLogs()
    {
        _logger.ClearMemory();
        Logs.Clear();
    }

    private void OnConnectionStateChanged(ConnectionState state, ConnectedDeviceInfo? device)
    {
        _dispatcher.BeginInvoke(() =>
        {
            ConnectionBadgeText = state switch
            {
                ConnectionState.Stopped => "Servidor Parado",
                ConnectionState.Listening => $"Aguardando Celular (Porta {_config.UdpPort})",
                ConnectionState.PairedAndConnected => $"Conectado: {device?.DeviceName} ({device?.RemoteEndpoint})",
                ConnectionState.DisconnectedTimeout => $"Desconectado (Timeout — {device?.DeviceName ?? "Celular"})",
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
                if (state.RawAxes.TryGetValue(axisItem.Source, out double raw))
                    axisItem.RawNormalized = raw;
                if (state.ProcessedSourceAxes.TryGetValue(axisItem.Source, out double proc))
                    axisItem.ProcessedNormalized = proc;

                axisItem.VJoyRawValue = _feeder.GetLastRawAxisValue(axisItem.TargetAxis);
            }

            foreach (ButtonMonitorItem btnItem in Buttons)
            {
                if (state.PhoneButtons.TryGetValue(btnItem.PhoneButtonNumber, out bool pressed))
                    btnItem.IsPressed = pressed;
            }
        });
    }

    private void OnTelemetryUpdated(TelemetryStats stats)
    {
        _dispatcher.BeginInvoke(() =>
        {
            TelemetryText =
                $"Taxa: {stats.CurrentPacketsPerSecond:0.0} Hz  |  Recebidos: {stats.TotalPacketsReceived}  |  Perdas/Fora de Ordem: {stats.OutOfOrderOrDroppedPackets}  |  Inatividade: {stats.LastPacketAgeMs} ms";
        });
    }

    private void OnEntryLogged(LogEntry entry)
    {
        _dispatcher.BeginInvoke(() =>
        {
            Logs.Add(entry.ToString());
            while (Logs.Count > 300)
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
