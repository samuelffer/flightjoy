using System.Security.Cryptography;
using System.Text;
using FlightJoy.Core.Protocol;

namespace FlightJoy.Core.Network;

/// <summary>
/// Implementa o pareamento seguro com PIN de 6 dígitos e assinatura HMAC-SHA256
/// de todos os pacotes de sessão e controle sem trafegar o PIN na rede.
/// </summary>
public static class PairingAuthenticator
{
    public static string GenerateSixDigitPin()
    {
        int value = RandomNumberGenerator.GetInt32(100000, 1000000);
        return value.ToString("D6");
    }

    public static string GenerateNonceHex(int byteLength = 16)
    {
        byte[] bytes = RandomNumberGenerator.GetBytes(byteLength);
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }

    public static string ComputeHmacSha256Hex(string secretKey, string canonicalPayload)
    {
        byte[] keyBytes = Encoding.UTF8.GetBytes(secretKey);
        byte[] messageBytes = Encoding.UTF8.GetBytes(canonicalPayload);
        byte[] hash = HMACSHA256.HashData(keyBytes, messageBytes);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    public static bool ConstantTimeEqualsHex(string? actualHex, string expectedHex)
    {
        if (string.IsNullOrEmpty(actualHex) || actualHex.Length != expectedHex.Length)
            return false;

        byte[] a = Encoding.ASCII.GetBytes(actualHex.ToLowerInvariant());
        byte[] b = Encoding.ASCII.GetBytes(expectedHex.ToLowerInvariant());
        return CryptographicOperations.FixedTimeEquals(a, b);
    }

    public static string DeriveSessionKey(string pin, string deviceId, string clientNonce, string sessionId)
    {
        string canonical = $"SESSION|{ProtocolConstants.ProtocolVersion}|{deviceId}|{clientNonce}|{sessionId}";
        return ComputeHmacSha256Hex(pin, canonical);
    }

    public static string BuildPairReqCanonical(FlightJoyPacket packet)
    {
        return $"PAIR_REQ|{packet.Version}|{packet.DeviceId}|{packet.DeviceName}|{packet.ClientNonce}|{packet.TimestampMs}";
    }

    public static string BuildPairAckCanonical(FlightJoyPacket packet)
    {
        return $"PAIR_ACK|{packet.Version}|{packet.DeviceId}|{packet.ClientNonce}|{packet.SessionId}|{packet.TimestampMs}";
    }

    public static string BuildStateCanonical(FlightJoyPacket packet)
    {
        return $"STATE|{packet.Version}|{packet.SessionId}|{packet.Sequence}|{packet.TimestampMs}|{packet.PitchScaled}|{packet.RollScaled}|{packet.YawScaled}|{packet.ThrottleScaled}|{packet.AuxScaled}|{packet.ButtonsMask}";
    }

    public static string BuildPingCanonical(FlightJoyPacket packet)
    {
        return $"PING|{packet.Version}|{packet.SessionId}|{packet.Sequence}|{packet.TimestampMs}";
    }

    public static string BuildPongCanonical(FlightJoyPacket packet)
    {
        return $"PONG|{packet.Version}|{packet.SessionId}|{packet.Sequence}|{packet.ClientTimestampMs}";
    }

    public static string BuildDisconnectCanonical(FlightJoyPacket packet)
    {
        return $"DISCONNECT|{packet.Version}|{packet.SessionId}|{packet.TimestampMs}";
    }

    public static void SignPairReq(FlightJoyPacket packet, string pin)
    {
        packet.Mac = ComputeHmacSha256Hex(pin, BuildPairReqCanonical(packet));
    }

    public static bool VerifyPairReq(FlightJoyPacket packet, string pin)
    {
        if (packet.Type != ProtocolConstants.TypePairReq ||
            string.IsNullOrWhiteSpace(packet.DeviceId) ||
            string.IsNullOrWhiteSpace(packet.ClientNonce))
        {
            return false;
        }

        string expected = ComputeHmacSha256Hex(pin, BuildPairReqCanonical(packet));
        return ConstantTimeEqualsHex(packet.Mac, expected);
    }

    public static void SignPairAck(FlightJoyPacket packet, string pin)
    {
        packet.Mac = ComputeHmacSha256Hex(pin, BuildPairAckCanonical(packet));
    }

    public static bool VerifyPairAck(FlightJoyPacket packet, string pin)
    {
        if (packet.Type != ProtocolConstants.TypePairAck ||
            string.IsNullOrWhiteSpace(packet.SessionId))
        {
            return false;
        }

        string expected = ComputeHmacSha256Hex(pin, BuildPairAckCanonical(packet));
        return ConstantTimeEqualsHex(packet.Mac, expected);
    }

    public static void SignState(FlightJoyPacket packet, string sessionKey)
    {
        packet.Mac = ComputeHmacSha256Hex(sessionKey, BuildStateCanonical(packet));
    }

    public static bool VerifyState(FlightJoyPacket packet, string sessionKey)
    {
        if (packet.Type != ProtocolConstants.TypeState)
            return false;

        string expected = ComputeHmacSha256Hex(sessionKey, BuildStateCanonical(packet));
        return ConstantTimeEqualsHex(packet.Mac, expected);
    }

    public static void SignPing(FlightJoyPacket packet, string sessionKey)
    {
        packet.Mac = ComputeHmacSha256Hex(sessionKey, BuildPingCanonical(packet));
    }

    public static bool VerifyPing(FlightJoyPacket packet, string sessionKey)
    {
        if (packet.Type != ProtocolConstants.TypePing)
            return false;

        string expected = ComputeHmacSha256Hex(sessionKey, BuildPingCanonical(packet));
        return ConstantTimeEqualsHex(packet.Mac, expected);
    }

    public static void SignPong(FlightJoyPacket packet, string sessionKey)
    {
        packet.Mac = ComputeHmacSha256Hex(sessionKey, BuildPongCanonical(packet));
    }

    public static bool VerifyPong(FlightJoyPacket packet, string sessionKey)
    {
        if (packet.Type != ProtocolConstants.TypePong)
            return false;

        string expected = ComputeHmacSha256Hex(sessionKey, BuildPongCanonical(packet));
    }

    public static void SignDisconnect(FlightJoyPacket packet, string sessionKey)
    {
        packet.Mac = ComputeHmacSha256Hex(sessionKey, BuildDisconnectCanonical(packet));
    }

    public static bool VerifyDisconnect(FlightJoyPacket packet, string sessionKey)
    {
        if (packet.Type != ProtocolConstants.TypeDisconnect)
            return false;

        string expected = ComputeHmacSha256Hex(sessionKey, BuildDisconnectCanonical(packet));
        return ConstantTimeEqualsHex(packet.Mac, expected);
    }
}
