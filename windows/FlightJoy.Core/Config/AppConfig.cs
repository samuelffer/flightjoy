using FlightJoy.Core.Protocol;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Config;

public sealed class AppConfig
{
    public int UdpPort { get; set; } = ProtocolConstants.DefaultUdpPort;
    public uint VJoyDeviceId { get; set; } = 1;
    public string PairingPin { get; set; } = "482910";
    public int WatchdogTimeoutMs { get; set; } = ProtocolConstants.DefaultWatchdogTimeoutMs;
    public bool ResetAxesOnDisconnect { get; set; } = true;

    public List<AxisMappingConfig> AxisMappings { get; set; } = CreateDefaultAxisMappings();
    public List<ButtonMappingConfig> ButtonMappings { get; set; } = CreateDefaultButtonMappings();

    public static List<AxisMappingConfig> CreateDefaultAxisMappings() =>
    [
        new AxisMappingConfig
        {
            Source = SourceAxis.Roll,
            TargetAxis = VJoyAxisUsage.X,
            Invert = false,
            Sensitivity = 1.0,
            Deadzone = 0.03,
            Exponential = 0.25,
            Smoothing = 0.15
        },
        new AxisMappingConfig
        {
            Source = SourceAxis.Pitch,
            TargetAxis = VJoyAxisUsage.Y,
            Invert = false,
            Sensitivity = 1.0,
            Deadzone = 0.03,
            Exponential = 0.25,
            Smoothing = 0.15
        },
        new AxisMappingConfig
        {
            Source = SourceAxis.Yaw,
            TargetAxis = VJoyAxisUsage.RZ,
            Invert = false,
            Sensitivity = 1.0,
            Deadzone = 0.05,
            Exponential = 0.30,
            Smoothing = 0.20
        },
        new AxisMappingConfig
        {
            Source = SourceAxis.Throttle,
            TargetAxis = VJoyAxisUsage.Z,
            Invert = false,
            Sensitivity = 1.0,
            Deadzone = 0.00,
            Exponential = 0.00,
            Smoothing = 0.05
        },
        new AxisMappingConfig
        {
            Source = SourceAxis.Aux,
            TargetAxis = VJoyAxisUsage.SL0,
            Invert = false,
            Sensitivity = 1.0,
            Deadzone = 0.02,
            Exponential = 0.00,
            Smoothing = 0.05
        }
    ];

    public static List<ButtonMappingConfig> CreateDefaultButtonMappings() =>
    [
        new ButtonMappingConfig { PhoneButtonNumber = 1, TargetVJoyButton = 1, Label = "Gatilho / AP Disconnect" },
        new ButtonMappingConfig { PhoneButtonNumber = 2, TargetVJoyButton = 2, Label = "Trem de Pouso (Gear)" },
        new ButtonMappingConfig { PhoneButtonNumber = 3, TargetVJoyButton = 3, Label = "Flaps Up (-)" },
        new ButtonMappingConfig { PhoneButtonNumber = 4, TargetVJoyButton = 4, Label = "Flaps Down (+)" },
        new ButtonMappingConfig { PhoneButtonNumber = 5, TargetVJoyButton = 5, Label = "Freio de Estac. (Brakes)" },
        new ButtonMappingConfig { PhoneButtonNumber = 6, TargetVJoyButton = 6, Label = "Alternar Câmera (View)" },
        new ButtonMappingConfig { PhoneButtonNumber = 7, TargetVJoyButton = 7, Label = "Piloto Automático (AP)" },
        new ButtonMappingConfig { PhoneButtonNumber = 8, TargetVJoyButton = 8, Label = "Spoiler / Airbrake" }
    ];
}
