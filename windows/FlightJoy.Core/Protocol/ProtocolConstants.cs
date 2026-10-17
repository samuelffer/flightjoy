namespace FlightJoy.Core.Protocol;

public static class ProtocolConstants
{
    public const int ProtocolVersion = 1;
    public const int DefaultUdpPort = 49152;
    public const int AxisScaleFactor = 10000;
    public const int MaxVJoyButtons = 32;
    public const int DefaultWatchdogTimeoutMs = 1200;

    public const string TypeDiscover = "DISCOVER";
    public const string TypeDiscoverReply = "DISCOVER_REPLY";
    public const string TypeHello = "HELLO";
    public const string TypeWelcome = "WELCOME";
    public const string TypeState = "STATE";
    public const string TypePing = "PING";
    public const string TypePong = "PONG";
    public const string TypeDisconnect = "DISCONNECT";

    // Compatibilidade legada
    public const string TypePairReq = "PAIR_REQ";
    public const string TypePairAck = "PAIR_ACK";
}
