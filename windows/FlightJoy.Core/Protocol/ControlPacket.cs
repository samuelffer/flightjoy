using System.Text.Json.Serialization;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Protocol;

/// <summary>
/// Representa qualquer pacote UDP do protocolo FlightJoy v1.
/// Toda a configuração de sensibilidade, calibração e mapeamento de eixos/botões
/// vem diretamente do aplicativo Android.
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

    [JsonPropertyName("pcName")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? PcName { get; set; }

    [JsonPropertyName("port")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingDefault)]
    public int Port { get; set; }

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

    /// <summary>Rudder / Yaw (pedais + giroscópio opcional) escalado [-10000, 10000]</summary>
    [JsonPropertyName("y")]
    public int YawScaled { get; set; }

    /// <summary>Throttle slider vertical escalado [-10000, 10000]</summary>
    [JsonPropertyName("t")]
    public int ThrottleScaled { get; set; }

    /// <summary>Throttle Reverso (3º slider acionado pelo switch REV) escalado [-10000, 10000]</summary>
    [JsonPropertyName("a")]
    public int AuxScaled { get; set; } = -10000;

    /// <summary>
    /// Máscara de 32 bits dos botões vJoy já mapeados pelo celular
    /// (bit 0 = Botão vJoy 1, ..., bit 31 = Botão vJoy 32).
    /// </summary>
    [JsonPropertyName("b")]
    public int ButtonsMask { get; set; }

    /// <summary>Eixo vJoy alvo para Pitch enviado pelo celular (ex: "Y")</summary>
    [JsonPropertyName("mp")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapPitch { get; set; }

    /// <summary>Eixo vJoy alvo para Roll enviado pelo celular (ex: "X")</summary>
    [JsonPropertyName("mr")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapRoll { get; set; }

    /// <summary>Eixo vJoy alvo para Rudder/Pedais enviado pelo celular (ex: "RZ")</summary>
    [JsonPropertyName("my")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapRudder { get; set; }

    /// <summary>Eixo vJoy alvo para Throttle enviado pelo celular (ex: "Z")</summary>
    [JsonPropertyName("mt")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapThrottle { get; set; }

    /// <summary>Eixo vJoy alvo para Throttle Reverso enviado pelo celular (ex: "SL0")</summary>
    [JsonPropertyName("ma")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapReverse { get; set; }

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

    public bool IsButtonPressed(int vjoyButtonOneBasedIndex)
    {
        if (vjoyButtonOneBasedIndex < 1 || vjoyButtonOneBasedIndex > 32)
            return false;
        uint mask = unchecked((uint)ButtonsMask);
        return (mask & (1u << (vjoyButtonOneBasedIndex - 1))) != 0;
    }

    public static int ScaleAxis(double normalized)
    {
        double clamped = Math.Clamp(normalized, -1.0, 1.0);
        return (int)Math.Round(clamped * ProtocolConstants.AxisScaleFactor);
    }

    public static VJoyAxisUsage ParseAxisOrDefault(string? axisName, VJoyAxisUsage defaultAxis)
    {
        if (!string.IsNullOrWhiteSpace(axisName) &&
            Enum.TryParse<VJoyAxisUsage>(axisName.Trim(), ignoreCase: true, out VJoyAxisUsage parsed))
        {
            return parsed;
        }
        return defaultAxis;
    }
}
