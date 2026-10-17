using System.Net;
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
    public void PairReqVerification_ValidPinSucceeds_WrongPinFails()
    {
        const string pin = "482910";
        var req = new FlightJoyPacket
        {
            Version = 1,
            Type = ProtocolConstants.TypePairReq,
            DeviceId = "pixel-8-id",
            DeviceName = "Pixel 8",
            ClientNonce = "0011223344556677",
            TimestampMs = 1729180000000
        };

        PairingAuthenticator.SignPairReq(req, pin);

        Assert.True(PairingAuthenticator.VerifyPairReq(req, pin));
        Assert.False(PairingAuthenticator.VerifyPairReq(req, "999999"));
    }

    [Fact]
    public void FullHandshakeAndStatePacket_EndToEndThroughUdpControlServer()
    {
        var config = new AppConfig { PairingPin = "654321" };
        var logger = new FlightLogger();
        var feeder = new MockVJoyFeeder();
        feeder.Acquire(1);
        var pipeline = new ControlPipeline(config);
        using var server = new UdpControlServer(config, pipeline, feeder, logger);

        var remoteEp = new IPEndPoint(IPAddress.Loopback, 55000);

        // 1. Envia PAIR_REQ assinado com PIN correto
        var pairReq = new FlightJoyPacket
        {
            Version = 1,
            Type = ProtocolConstants.TypePairReq,
            DeviceId = "android-test-01",
            DeviceName = "Galaxy S23",
            ClientNonce = "aabbccddeeff0011",
            TimestampMs = 1000
        };
        PairingAuthenticator.SignPairReq(pairReq, "654321");

        FlightJoyPacket? pairAck = server.ProcessDatagram(
            PacketSerializer.SerializeToUtf8Bytes(pairReq),
            remoteEp,
            nowTickMs: 1000);

        Assert.NotNull(pairAck);
        Assert.Equal(ProtocolConstants.TypePairAck, pairAck.Type);
        Assert.True(PairingAuthenticator.VerifyPairAck(pairAck, "654321"));
        Assert.NotNull(pairAck.SessionId);

        // 2. Deriva chave de sessão e envia STATE
        string sessionKey = PairingAuthenticator.DeriveSessionKey(
            "654321",
            pairReq.DeviceId!,
            pairReq.ClientNonce!,
            pairAck.SessionId!);

        var statePacket = new FlightJoyPacket
        {
            Version = 1,
            Type = ProtocolConstants.TypeState,
            SessionId = pairAck.SessionId,
            Sequence = 1,
            TimestampMs = 1016,
            PitchScaled = 10000, // +1.0
            RollScaled = -10000, // -1.0
            YawScaled = 0,
            ThrottleScaled = 5000,
            AuxScaled = 0,
            ButtonsMask = 0b0000_0101 // Botões 1 e 3
        };
        PairingAuthenticator.SignState(statePacket, sessionKey);

        server.ProcessDatagram(
            PacketSerializer.SerializeToUtf8Bytes(statePacket),
            remoteEp,
            nowTickMs: 1016);

        Assert.True(feeder.GetLastButtonState(1));
        Assert.False(feeder.GetLastButtonState(2));
        Assert.True(feeder.GetLastButtonState(3));
        Assert.True(feeder.GetLastRawAxisValue(VJoyAxisUsage.Y) > MockVJoyFeeder.DefaultCenter);
        Assert.True(feeder.GetLastRawAxisValue(VJoyAxisUsage.X) < MockVJoyFeeder.DefaultCenter);
    }
}
