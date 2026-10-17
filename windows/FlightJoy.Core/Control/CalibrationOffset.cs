namespace FlightJoy.Core.Control;

/// <summary>
/// Armazena o offset de calibração (tara/zero) de orientação no cliente Windows.
/// </summary>
public sealed class CalibrationOffset
{
    public double PitchOffset { get; set; }
    public double RollOffset { get; set; }
    public double YawOffset { get; set; }

    public void CaptureCenter(double currentPitch, double currentRoll, double currentYaw)
    {
        PitchOffset = Math.Clamp(currentPitch, -0.9, 0.9);
        RollOffset = Math.Clamp(currentRoll, -0.9, 0.9);
        YawOffset = Math.Clamp(currentYaw, -0.9, 0.9);
    }

    public void Reset()
    {
        PitchOffset = 0.0;
        RollOffset = 0.0;
        YawOffset = 0.0;
    }
}
