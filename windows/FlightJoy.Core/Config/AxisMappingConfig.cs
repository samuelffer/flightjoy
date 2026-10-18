using System.Text.Json.Serialization;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Config;

[JsonConverter(typeof(JsonStringEnumConverter))]
public enum SourceAxis
{
    Pitch = 0,
    Roll = 1,
    Yaw = 2,
    Throttle = 3,
    Aux = 4,
    Brake = 5
}

public sealed class AxisMappingConfig
{
    public SourceAxis Source { get; set; }

    [JsonConverter(typeof(JsonStringEnumConverter))]
    public VJoyAxisUsage TargetAxis { get; set; } = VJoyAxisUsage.X;

    public bool Invert { get; set; }

    /// <summary>Sensitivity multiplier (0.1 to 3.0)</summary>
    public double Sensitivity { get; set; } = 1.0;

    /// <summary>Center deadzone (0.0 to 0.40)</summary>
    public double Deadzone { get; set; } = 0.03;

    /// <summary>Exponential curve (0.0 = linear, 1.0 = cubic smooth center)</summary>
    public double Exponential { get; set; } = 0.20;

    /// <summary>Low-pass smoothing factor (0.0 = unfiltered, 0.85 = very smooth)</summary>
    public double Smoothing { get; set; } = 0.15;

    public AxisMappingConfig Clone() => new()
    {
        Source = Source,
        TargetAxis = TargetAxis,
        Invert = Invert,
        Sensitivity = Sensitivity,
        Deadzone = Deadzone,
        Exponential = Exponential,
        Smoothing = Smoothing
    };
}
