using System.Text;
using System.Text.Json;

namespace FlightJoy.Core.Protocol;

public static class PacketSerializer
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = null,
        WriteIndented = false
    };

    public static byte[] SerializeToUtf8Bytes(FlightJoyPacket packet)
    {
        ArgumentNullException.ThrowIfNull(packet);
        return JsonSerializer.SerializeToUtf8Bytes(packet, Options);
    }

    public static string Serialize(FlightJoyPacket packet)
    {
        ArgumentNullException.ThrowIfNull(packet);
        return JsonSerializer.Serialize(packet, Options);
    }

    public static FlightJoyPacket? Deserialize(ReadOnlySpan<byte> utf8Bytes)
    {
        if (utf8Bytes.IsEmpty)
            return null;

        try
        {
            return JsonSerializer.Deserialize<FlightJoyPacket>(utf8Bytes, Options);
        }
        catch (JsonException)
        {
            return null;
        }
    }

    public static FlightJoyPacket? Deserialize(string json)
    {
        if (string.IsNullOrWhiteSpace(json))
            return null;

        return Deserialize(Encoding.UTF8.GetBytes(json));
    }
}
