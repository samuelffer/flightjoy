using FlightJoy.Core.Protocol;
using Xunit;

namespace FlightJoy.Tests;

public sealed class PacketSerializerTests
{
    [Fact]
    public void SerializeAndDeserialize_PreservesScaledAxesAndButtonBitmask()
    {
        var packet = new FlightJoyPacket
        {
            Version = ProtocolConstants.ProtocolVersion,
            Type = ProtocolConstants.TypeState,
            SessionId = "abc123session",
            Sequence = 42,
            TimestampMs = 1729180000123,
            PitchScaled = FlightJoyPacket.ScaleAxis(0.5),
            RollScaled = FlightJoyPacket.ScaleAxis(-0.25),
            YawScaled = FlightJoyPacket.ScaleAxis(0.1),
            ThrottleScaled = FlightJoyPacket.ScaleAxis(0.8),
            AuxScaled = FlightJoyPacket.ScaleAxis(-1.0),
            BrakeScaled = FlightJoyPacket.ScaleAxis(0.5),
            ButtonsMask = (1 << 0) | (1 << 3) | (1 << 7)
        };

        byte[] bytes = PacketSerializer.SerializeToUtf8Bytes(packet);
        FlightJoyPacket? restored = PacketSerializer.Deserialize(bytes);

        Assert.NotNull(restored);
        Assert.Equal(ProtocolConstants.TypeState, restored.Type);
        Assert.Equal("abc123session", restored.SessionId);
        Assert.Equal(42, restored.Sequence);
        Assert.Equal(5000, restored.PitchScaled);
        Assert.Equal(-2500, restored.RollScaled);
        Assert.Equal(1000, restored.YawScaled);
        Assert.Equal(8000, restored.ThrottleScaled);
        Assert.Equal(-10000, restored.AuxScaled);
        Assert.Equal(5000, restored.BrakeScaled);
        Assert.Equal(0.5, restored.PitchNormalized, 4);
        Assert.Equal(-0.25, restored.RollNormalized, 4);
        Assert.True(restored.IsButtonPressed(1));
        Assert.False(restored.IsButtonPressed(2));
        Assert.True(restored.IsButtonPressed(4));
        Assert.True(restored.IsButtonPressed(8));
    }

    [Fact]
    public void Deserialize_InvalidJson_ReturnsNull()
    {
        FlightJoyPacket? result = PacketSerializer.Deserialize("{not-valid-json");
        Assert.Null(result);
    }
}
