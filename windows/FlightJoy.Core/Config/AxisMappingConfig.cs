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
    Aux = 4
}

public sealed class AxisMappingConfig
{
    public SourceAxis Source { get; set; }

    [JsonConverter(typeof(JsonStringEnumConverter))]
    public VJoyAxisUsage TargetAxis { get; set; } = VJoyAxisUsage.X;

    public bool Invert { get; set; }

    /// <summary>Multiplicador de sensibilidade (0.1 a 3.0)</summary>
    public double Sensitivity { get; set; } = 1.0;

    /// <summary>Zona morta central (0.0 a 0.40)</summary>
    public double Deadzone { get; set; } = 0.03;

    /// <summary>Curva exponencial (0.0 = linear, 1.0 = cúbica suave no centro)</summary>
    public double Exponential { get; set; } = 0.20;

    /// <summary>Fator de suavização passa-baixa (0.0 = sem filtro, 0.85 = muito suave)</summary>
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
