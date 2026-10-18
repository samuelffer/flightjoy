using System.Text.Json.Serialization;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Protocol;

/// <summary>
/// Represents a UDP packet in the FlightJoy v1 protocol.
/// All sensitivity, calibration, and axis/button mapping settings are controlled directly by the Android app.
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

    /// <summary>Scaled Pitch [-10000, 10000]</summary>
    [JsonPropertyName("p")]
    public int PitchScaled { get; set; }

    /// <summary>Scaled Roll [-10000, 10000]</summary>
    [JsonPropertyName("r")]
    public int RollScaled { get; set; }

    /// <summary>Scaled Rudder / Yaw [-10000, 10000]</summary>
    [JsonPropertyName("y")]
    public int YawScaled { get; set; }

    /// <summary>Scaled vertical Throttle slider [-10000, 10000]</summary>
    [JsonPropertyName("t")]
    public int ThrottleScaled { get; set; }

    /// <summary>Scaled Reverse Throttle slider [-10000, 10000]</summary>
    [JsonPropertyName("a")]
    public int AuxScaled { get; set; } = -10000;

    /// <summary>Scaled progressive Brake slider [-10000, 10000]</summary>
    [JsonPropertyName("brk")]
    public int BrakeScaled { get; set; } = -10000;

    /// <summary>
    /// 32-bit bitmask of vJoy buttons mapped by the phone
    /// (bit 0 = vJoy Button 1, ..., bit 31 = vJoy Button 32).
    /// </summary>
    [JsonPropertyName("b")]
    public int ButtonsMask { get; set; }

    /// <summary>Target vJoy axis for Pitch sent by the phone (e.g. "Y")</summary>
    [JsonPropertyName("mp")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapPitch { get; set; }

    /// <summary>Target vJoy axis for Roll sent by the phone (e.g. "X")</summary>
    [JsonPropertyName("mr")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapRoll { get; set; }

    /// <summary>Target vJoy axis for Rudder sent by the phone (e.g. "RZ")</summary>
    [JsonPropertyName("my")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapRudder { get; set; }

    /// <summary>Target vJoy axis for Throttle sent by the phone (e.g. "Z")</summary>
    [JsonPropertyName("mt")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapThrottle { get; set; }

    /// <summary>Target vJoy axis for Reverse Throttle sent by the phone (e.g. "SL0")</summary>
    [JsonPropertyName("ma")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapReverse { get; set; }

    /// <summary>Target vJoy axis for Progressive Brake slider sent by the phone (e.g. "SL1")</summary>
    [JsonPropertyName("mb")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? MapBrake { get; set; }

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

    [JsonIgnore]
    public double BrakeNormalized => Math.Clamp((double)BrakeScaled / ProtocolConstants.AxisScaleFactor, -1.0, 1.0);

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
