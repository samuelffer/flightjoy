namespace FlightJoy.Core.Network;

public enum ConnectionState
{
    Stopped = 0,
    Listening = 1,
    PairedAndConnected = 2,
    DisconnectedTimeout = 3
}

public sealed record ConnectedDeviceInfo(
    string DeviceId,
    string DeviceName,
    string RemoteEndpoint,
    string SessionId,
    DateTimeOffset ConnectedAtUtc);
