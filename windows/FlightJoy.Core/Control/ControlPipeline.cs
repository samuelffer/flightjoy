using FlightJoy.Core.Config;
using FlightJoy.Core.Protocol;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Control;

/// <summary>
/// Pipeline central que transforma os pacotes enviados pelo aplicativo Android
/// em entradas de eixos e botões vJoy, respeitando o mapeamento definido no celular.
/// </summary>
public sealed class ControlPipeline
{
    private readonly object _sync = new();
    private readonly Dictionary<SourceAxis, double> _previousSmoothed = new();
    private readonly CalibrationOffset _calibration = new();
    private AppConfig _config;
    private double _lastRawPitch;
    private double _lastRawRoll;
    private double _lastRawYaw;

    public ControlPipeline(AppConfig config)
    {
        _config = config ?? throw new ArgumentNullException(nameof(config));
        foreach (SourceAxis axis in Enum.GetValues<SourceAxis>())
        {
            _previousSmoothed[axis] = 0.0;
        }
    }

    public CalibrationOffset Calibration => _calibration;

    public void UpdateConfig(AppConfig config)
    {
        ArgumentNullException.ThrowIfNull(config);
        lock (_sync)
        {
            _config = config;
        }
    }

    public void CalibrateCenterFromLastReading()
    {
        lock (_sync)
        {
            _calibration.CaptureCenter(_lastRawPitch, _lastRawRoll, _lastRawYaw);
        }
    }

    public void ResetCalibration()
    {
        lock (_sync)
        {
            _calibration.Reset();
            foreach (SourceAxis axis in Enum.GetValues<SourceAxis>())
            {
                _previousSmoothed[axis] = 0.0;
            }
        }
    }

    public ProcessedJoystickState ApplyPacket(FlightJoyPacket packet, IVJoyFeeder feeder)
    {
        ArgumentNullException.ThrowIfNull(packet);
        ArgumentNullException.ThrowIfNull(feeder);

        lock (_sync)
        {
            _lastRawPitch = packet.PitchNormalized;
            _lastRawRoll = packet.RollNormalized;
            _lastRawYaw = packet.YawNormalized;

            var rawAxes = new Dictionary<SourceAxis, double>
            {
                [SourceAxis.Pitch] = _lastRawPitch,
                [SourceAxis.Roll] = _lastRawRoll,
                [SourceAxis.Yaw] = _lastRawYaw,
                [SourceAxis.Throttle] = packet.ThrottleNormalized,
                [SourceAxis.Aux] = packet.AuxNormalized
            };

            var processedSource = new Dictionary<SourceAxis, double>();
            var targetVJoyAxes = new Dictionary<VJoyAxisUsage, double>();

            bool hasPhoneAxisMapping =
                !string.IsNullOrWhiteSpace(packet.MapPitch) ||
                !string.IsNullOrWhiteSpace(packet.MapRoll) ||
                !string.IsNullOrWhiteSpace(packet.MapRudder) ||
                !string.IsNullOrWhiteSpace(packet.MapThrottle);

            if (hasPhoneAxisMapping)
            {
                // Mapeamento e curvas definidos diretamente pelo aplicativo Android
                double pitchVal = Math.Clamp(_lastRawPitch - _calibration.PitchOffset, -1.0, 1.0);
                double rollVal = Math.Clamp(_lastRawRoll - _calibration.RollOffset, -1.0, 1.0);
                double rudderVal = Math.Clamp(_lastRawYaw - _calibration.YawOffset, -1.0, 1.0);
                double throttleVal = packet.ThrottleNormalized;
                double reverseVal = packet.AuxNormalized;

                processedSource[SourceAxis.Pitch] = pitchVal;
                processedSource[SourceAxis.Roll] = rollVal;
                processedSource[SourceAxis.Yaw] = rudderVal;
                processedSource[SourceAxis.Throttle] = throttleVal;
                processedSource[SourceAxis.Aux] = reverseVal;

                VJoyAxisUsage pitchTarget = FlightJoyPacket.ParseAxisOrDefault(packet.MapPitch, VJoyAxisUsage.Y);
                VJoyAxisUsage rollTarget = FlightJoyPacket.ParseAxisOrDefault(packet.MapRoll, VJoyAxisUsage.X);
                VJoyAxisUsage rudderTarget = FlightJoyPacket.ParseAxisOrDefault(packet.MapRudder, VJoyAxisUsage.RZ);
                VJoyAxisUsage throttleTarget = FlightJoyPacket.ParseAxisOrDefault(packet.MapThrottle, VJoyAxisUsage.Z);
                VJoyAxisUsage reverseTarget = FlightJoyPacket.ParseAxisOrDefault(packet.MapReverse, VJoyAxisUsage.SL0);

                if (pitchTarget != VJoyAxisUsage.None)
                {
                    targetVJoyAxes[pitchTarget] = pitchVal;
                    feeder.SetNormalizedAxis(pitchTarget, pitchVal);
                }
                if (rollTarget != VJoyAxisUsage.None)
                {
                    targetVJoyAxes[rollTarget] = rollVal;
                    feeder.SetNormalizedAxis(rollTarget, rollVal);
                }
                if (rudderTarget != VJoyAxisUsage.None)
                {
                    targetVJoyAxes[rudderTarget] = rudderVal;
                    feeder.SetNormalizedAxis(rudderTarget, rudderVal);
                }
                if (throttleTarget != VJoyAxisUsage.None)
                {
                    targetVJoyAxes[throttleTarget] = throttleVal;
                    feeder.SetNormalizedAxis(throttleTarget, throttleVal);
                }
                if (reverseTarget != VJoyAxisUsage.None)
                {
                    targetVJoyAxes[reverseTarget] = reverseVal;
                    feeder.SetNormalizedAxis(reverseTarget, reverseVal);
                }
            }
            else
            {
                foreach (AxisMappingConfig mapping in _config.AxisMappings)
                {
                    double raw = rawAxes.TryGetValue(mapping.Source, out double r) ? r : 0.0;
                    double offset = mapping.Source switch
                    {
                        SourceAxis.Pitch => _calibration.PitchOffset,
                        SourceAxis.Roll => _calibration.RollOffset,
                        SourceAxis.Yaw => _calibration.YawOffset,
                        _ => 0.0
                    };

                    double prev = _previousSmoothed.TryGetValue(mapping.Source, out double p) ? p : 0.0;
                    double processed = AxisCurveProcessor.ProcessAxis(
                        rawInput: raw,
                        centerOffset: offset,
                        deadzone: mapping.Deadzone,
                        exponential: mapping.Exponential,
                        sensitivity: mapping.Sensitivity,
                        invert: mapping.Invert,
                        previousSmoothedOutput: prev,
                        smoothingFactor: mapping.Smoothing);

                    _previousSmoothed[mapping.Source] = processed;
                    processedSource[mapping.Source] = processed;

                    if (mapping.TargetAxis != VJoyAxisUsage.None)
                    {
                        targetVJoyAxes[mapping.TargetAxis] = processed;
                        feeder.SetNormalizedAxis(mapping.TargetAxis, processed);
                    }
                }
            }

            // Os 32 bits de ButtonsMask representam diretamente os botões vJoy 1..32 mapeados pelo celular
            var phoneButtons = new Dictionary<int, bool>();
            var targetButtons = new Dictionary<int, bool>();

            for (int btn = 1; btn <= ProtocolConstants.MaxVJoyButtons; btn++)
            {
                bool pressed = packet.IsButtonPressed(btn);
                targetButtons[btn] = pressed;
                if (btn <= 8)
                {
                    phoneButtons[btn] = pressed;
                }
                feeder.SetButton(btn, pressed);
            }

            return new ProcessedJoystickState
            {
                RawAxes = rawAxes,
                ProcessedSourceAxes = processedSource,
                TargetVJoyAxes = targetVJoyAxes,
                PhoneButtons = phoneButtons,
                TargetVJoyButtons = targetButtons
            };
        }
    }
}
