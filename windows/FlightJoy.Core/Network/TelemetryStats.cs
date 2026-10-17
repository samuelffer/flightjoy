namespace FlightJoy.Core.Network;

public sealed class TelemetryStats
{
    public long TotalPacketsReceived { get; set; }
    public long OutOfOrderOrDroppedPackets { get; set; }
    public long InvalidAuthPackets { get; set; }
    public long LastSequenceNumber { get; set; }
    public double CurrentPacketsPerSecond { get; set; }
    public long LastPacketAgeMs { get; set; }
}
