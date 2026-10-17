using System.Net;
using FlightJoy.Core.Audio;
using FlightJoy.Core.Config;
using FlightJoy.Core.Control;
using FlightJoy.Core.Logging;
using FlightJoy.Core.Network;
using FlightJoy.Core.Protocol;
using FlightJoy.Core.VJoy;
using Xunit;

namespace FlightJoy.Tests;

public sealed class PairingAuthenticatorTests
{
    [Fact]
    public void DiscoverRequest_ReturnsDiscoverReplyWithMachineName()
    {
        var config = new AppConfig();
        var logger = new FlightLogger();
        var feeder = new MockVJoyFeeder();
        feeder.Acquire(1);
        var pipeline = new ControlPipeline(config);
        using var server = new UdpControlServer(config, pipeline, feeder, logger);

        var remoteEp = new IPEndPoint(IPAddress.Loopback, 55000);
        var discoverReq = new FlightJoyPacket
        {
            Version = 1,
            Type = ProtocolConstants.TypeDiscover,
            DeviceId = "android-01",
            TimestampMs = 1000
        };

        FlightJoyPacket? reply = server.ProcessDatagram(
            PacketSerializer.SerializeToUtf8Bytes(discoverReq),
            remoteEp,
            nowTickMs: 1000);

        Assert.NotNull(reply);
        Assert.Equal(ProtocolConstants.TypeDiscoverReply, reply.Type);
        Assert.False(string.IsNullOrWhiteSpace(reply.PcName));
    }

    [Fact]
    public void DirectHelloAndPhoneMappedState_UpdatesVJoyAxesAndButtons()
    {
        var config = new AppConfig();
        var logger = new FlightLogger();
        var feeder = new MockVJoyFeeder();
        feeder.Acquire(1);
        var pipeline = new ControlPipeline(config);
        using var server = new UdpControlServer(config, pipeline, feeder, logger);

        var remoteEp = new IPEndPoint(IPAddress.Loopback, 55000);

        var hello = new FlightJoyPacket
        {
            Version = 1,
            Type = ProtocolConstants.TypeHello,
            DeviceId = "android-test-01",
            DeviceName = "Galaxy S23",
            TimestampMs = 1000
        };

        FlightJoyPacket? welcome = server.ProcessDatagram(
            PacketSerializer.SerializeToUtf8Bytes(hello),
            remoteEp,
            nowTickMs: 1000);

        Assert.NotNull(welcome);
        Assert.Equal(ProtocolConstants.TypeWelcome, welcome.Type);
        Assert.NotNull(welcome.SessionId);

        // Envia STATE com mapeamento exclusivo vindo do celular (ex:Botão vJoy 12 ativo)
        var statePacket = new FlightJoyPacket
        {
            Version = 1,
            Type = ProtocolConstants.TypeState,
            SessionId = welcome.SessionId,
            Sequence = 1,
            TimestampMs = 1016,
            PitchScaled = 10000,   // +1.0
            RollScaled = -10000,   // -1.0
            YawScaled = 5000,      // +0.5 Rudder
            ThrottleScaled = 8000, // +0.8 Throttle
            MapPitch = "Y",
            MapRoll = "X",
            MapRudder = "RZ",
            MapThrottle = "Z",
            ButtonsMask = (1 << 0) | (1 << 11) // Botão vJoy 1 e Botão vJoy 12
        };

        server.ProcessDatagram(
            PacketSerializer.SerializeToUtf8Bytes(statePacket),
            remoteEp,
            nowTickMs: 1016);

        Assert.True(feeder.GetLastButtonState(1));
        Assert.False(feeder.GetLastButtonState(2));
        Assert.True(feeder.GetLastButtonState(12));
        Assert.Equal(MockVJoyFeeder.DefaultMax, feeder.GetLastRawAxisValue(VJoyAxisUsage.Y));
        Assert.Equal(MockVJoyFeeder.DefaultMin, feeder.GetLastRawAxisValue(VJoyAxisUsage.X));
        Assert.True(feeder.GetLastRawAxisValue(VJoyAxisUsage.RZ) > MockVJoyFeeder.DefaultCenter);
        Assert.True(feeder.GetLastRawAxisValue(VJoyAxisUsage.Z) > MockVJoyFeeder.DefaultCenter);
    }

    [Fact]
    public void SoundNotifier_BuildsValidRiffWaveHeaders()
    {
        byte[] connWav = SoundNotifier.BuildConnectedSingleChimeWav();
        byte[] discWav = SoundNotifier.BuildDisconnectedDoubleToneWav();

        Assert.True(connWav.Length > 44);
        Assert.True(discWav.Length > connWav.Length);
        Assert.Equal((byte)'R', connWav[0]);
        Assert.Equal((byte)'I', connWav[1]);
        Assert.Equal((byte)'F', connWav[2]);
        Assert.Equal((byte)'F', connWav[3]);
    }
}
