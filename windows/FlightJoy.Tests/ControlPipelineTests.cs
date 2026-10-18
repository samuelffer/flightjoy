using FlightJoy.Core.Config;
using FlightJoy.Core.Control;
using FlightJoy.Core.Protocol;
using FlightJoy.Core.VJoy;
using Xunit;

namespace FlightJoy.Tests;

public sealed class ControlPipelineTests
{
    [Fact]
    public void ApplyPacket_CalibrateCenter_NeutralizesSubsequentReadings()
    {
        var config = new AppConfig();
        foreach (var m in config.AxisMappings)
        {
            m.Smoothing = 0.0;
            m.Deadzone = 0.02;
        }

        var feeder = new MockVJoyFeeder();
        feeder.Acquire(1);
        var pipeline = new ControlPipeline(config);

        var tiltedPacket = new FlightJoyPacket
        {
            PitchScaled = 3000, // +0.30
            RollScaled = -2000, // -0.20
            YawScaled = 1000    // +0.10
        };

        pipeline.ApplyPacket(tiltedPacket, feeder);
        pipeline.CalibrateCenterFromLastReading();

        ProcessedJoystickState afterCal = pipeline.ApplyPacket(tiltedPacket, feeder);

        Assert.Equal(0.0, afterCal.ProcessedSourceAxes[SourceAxis.Pitch], 4);
        Assert.Equal(0.0, afterCal.ProcessedSourceAxes[SourceAxis.Roll], 4);
        Assert.Equal(0.0, afterCal.ProcessedSourceAxes[SourceAxis.Yaw], 4);
        Assert.Equal(MockVJoyFeeder.DefaultCenter, feeder.GetLastRawAxisValue(VJoyAxisUsage.Y));
        Assert.Equal(MockVJoyFeeder.DefaultCenter, feeder.GetLastRawAxisValue(VJoyAxisUsage.X));
    }
}
