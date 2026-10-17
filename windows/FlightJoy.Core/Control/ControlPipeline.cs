using FlightJoy.Core.Config;
using FlightJoy.Core.Protocol;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Core.Control;

/// <summary>
/// Pipeline central que transforma pacotes de telemetria em comandos de eixos e botões vJoy.
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

            var phoneButtons = new Dictionary<int, bool>();
            for (int i = 1; i <= ProtocolConstants.MaxButtons; i++)
            {
                phoneButtons[i] = packet.IsButtonPressed(i);
            }

            var targetButtons = new Dictionary<int, bool>();
            foreach (ButtonMappingConfig btnMap in _config.ButtonMappings)
            {
                bool pressed = phoneButtons.TryGetValue(btnMap.PhoneButtonNumber, out bool state) && state;
                if (btnMap.TargetVJoyButton >= 1 && btnMap.TargetVJoyButton <= 32)
                {
                    targetButtons[btnMap.TargetVJoyButton] = pressed;
                    feeder.SetButton(btnMap.TargetVJoyButton, pressed);
                }
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
