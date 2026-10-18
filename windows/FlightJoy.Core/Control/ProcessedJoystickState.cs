using FlightJoy.Core.Config;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Control;

public sealed class ProcessedJoystickState
{
    public IReadOnlyDictionary<SourceAxis, double> RawAxes { get; init; } =
        new Dictionary<SourceAxis, double>();

    public IReadOnlyDictionary<SourceAxis, double> ProcessedSourceAxes { get; init; } =
        new Dictionary<SourceAxis, double>();

    public IReadOnlyDictionary<VJoyAxisUsage, double> TargetVJoyAxes { get; init; } =
        new Dictionary<VJoyAxisUsage, double>();

    public IReadOnlyDictionary<int, bool> PhoneButtons { get; init; } =
        new Dictionary<int, bool>();

    public IReadOnlyDictionary<int, bool> TargetVJoyButtons { get; init; } =
        new Dictionary<int, bool>();
}
