namespace FlightJoy.Core.Protocol;

public static class ProtocolConstants
{
    public const int ProtocolVersion = 1;
    public const int DefaultUdpPort = 49152;
    public const int AxisScaleFactor = 10000;
    public const int MaxButtons = 8;
    public const int DefaultWatchdogTimeoutMs = 1000;

    public const string TypePairReq = "PAIR_REQ";
    public const string TypePairAck = "PAIR_ACK";
    public const string TypePairErr = "PAIR_ERR";
    public const string TypeState = "STATE";
    public const string TypePing = "PING";
    public const string TypePong = "PONG";
    public const string TypeDisconnect = "DISCONNECT";
}
