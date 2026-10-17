using FlightJoy.Core.Control;
using Xunit;

namespace FlightJoy.Tests;

public sealed class AxisCurveProcessorTests
{
    [Fact]
    public void ProcessAxis_InsideDeadzone_ReturnsZero()
    {
        double result = AxisCurveProcessor.ProcessAxis(
            rawInput: 0.04,
            centerOffset: 0.0,
            deadzone: 0.05,
            exponential: 0.2,
            sensitivity: 1.0,
            invert: false);

        Assert.Equal(0.0, result, 5);
    }

    [Fact]
    public void ProcessAxis_InvertAndSensitivity_RespectsBounds()
    {
        double result = AxisCurveProcessor.ProcessAxis(
            rawInput: 0.5,
            centerOffset: 0.0,
            deadzone: 0.0,
            exponential: 0.0,
            sensitivity: 1.5,
            invert: true);

        Assert.Equal(-0.75, result, 4);
    }

    [Fact]
    public void ProcessAxis_WithCenterOffset_ZeroesTiltedNeutralPosition()
    {
        double result = AxisCurveProcessor.ProcessAxis(
            rawInput: 0.22,
            centerOffset: 0.22,
            deadzone: 0.02,
            exponential: 0.0,
            sensitivity: 1.0,
            invert: false);

        Assert.Equal(0.0, result, 5);
    }
}
