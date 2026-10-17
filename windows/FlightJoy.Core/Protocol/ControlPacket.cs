using System.Text.Json.Serialization;

namespace FlightJoy.Core.Protocol;

/// <summary>
/// Representa qualquer pacote UDP do protocolo FlightJoy v1.
/// Os eixos são transmitidos como inteiros escalados [-10000, +10000]
/// para eliminar variações de formatação de ponto flutuante entre plataformas.
/// </summary>
public sealed class FlightJoyPacket
{
    [JsonPropertyName("v")]
    public int Version { get; set; } = ProtocolConstants.ProtocolVersion;

    [JsonPropertyName("type")]
    public string Type { get; set; } = string.Empty;

    [JsonPropertyName("deviceId")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DeviceId { get; set; }

    [JsonPropertyName("deviceName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? DeviceName { get; set; }

    [JsonPropertyName("clientNonce")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? ClientNonce { get; set; }

    [JsonPropertyName("sessionId")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? SessionId { get; set; }

    [JsonPropertyName("reason")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Reason { get; set; }

    [JsonPropertyName("seq")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingDefault)]
    public long Sequence { get; set; }

    [JsonPropertyName("ts")]
    public long TimestampMs { get; set; }

    [JsonPropertyName("clientTs")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingDefault)]
    public long ClientTimestampMs { get; set; }

    /// <summary>Pitch escalado [-10000, 10000]</summary>
    [JsonPropertyName("p")]
    public int PitchScaled { get; set; }

    /// <summary>Roll escalado [-10000, 10000]</summary>
    [JsonPropertyName("r")]
    public int RollScaled { get; set; }

    /// <summary>Yaw escalado [-10000, 10000]</summary>
    [JsonPropertyName("y")]
    public int YawScaled { get; set; }

    /// <summary>Throttle slider escalado [-10000, 10000]</summary>
    [JsonPropertyName("t")]
    public int ThrottleScaled { get; set; }

    /// <summary>Slider Auxiliar / Flaps escalado [-10000, 10000]</summary>
    [JsonPropertyName("a")]
    public int AuxScaled { get; set; }

    /// <summary>Bitmask dos botões (Bit 0 = Botão 1 ... Bit 7 = Botão 8)</summary>
    [JsonPropertyName("b")]
    public int ButtonsMask { get; set; }

    [JsonPropertyName("mac")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? Mac { get; set; }

    [JsonIgnore]
    public double PitchNormalized => Math.Clamp((double)PitchScaled / ProtocolConstants.AxisScaleFactor, -1.0, 1.0);

    [JsonIgnore]
    public double RollNormalized => Math.Clamp((double)RollScaled / ProtocolConstants.AxisScaleFactor, -1.0, 1.0);

    [JsonIgnore]
    public double YawNormalized => Math.Clamp((double)YawScaled / ProtocolConstants.AxisScaleFactor, -1.0, 1.0);

    [JsonIgnore]
    public double ThrottleNormalized => Math.Clamp((double)ThrottleScaled / ProtocolConstants.AxisScaleFactor, -1.0, 1.0);

    [JsonIgnore]
    public double AuxNormalized => Math.Clamp((double)AuxScaled / ProtocolConstants.AxisScaleFactor, -1.0, 1.0);

    public bool IsButtonPressed(int buttonOneBasedIndex)
    {
        if (buttonOneBasedIndex < 1 || buttonOneBasedIndex > 32)
            return false;
        return (ButtonsMask & (1 << (buttonOneBasedIndex - 1))) != 0;
    }

    public static int ScaleAxis(double normalized)
    {
        double clamped = Math.Clamp(normalized, -1.0, 1.0);
        return (int)Math.Round(clamped * ProtocolConstants.AxisScaleFactor);
    }
}
