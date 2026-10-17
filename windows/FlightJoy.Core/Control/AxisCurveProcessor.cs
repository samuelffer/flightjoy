namespace FlightJoy.Core.Control;

/// <summary>
/// Funções matemáticas puras para aplicação de calibração, zona morta (deadzone),
/// curva exponencial (expo), ganho de sensibilidade, inversão e suavização passa-baixa.
/// </summary>
public static class AxisCurveProcessor
{
    public static double ProcessAxis(
        double rawInput,
        double centerOffset,
        double deadzone,
        double exponential,
        double sensitivity,
        bool invert,
        double previousSmoothedOutput = 0.0,
        double smoothingFactor = 0.0)
    {
        double input = Math.Clamp(rawInput - centerOffset, -1.0, 1.0);
        double dz = Math.Clamp(deadzone, 0.0, 0.80);
        double expo = Math.Clamp(exponential, 0.0, 1.0);
        double sens = Math.Clamp(sensitivity, 0.05, 5.0);
        double smooth = Math.Clamp(smoothingFactor, 0.0, 0.95);

        double abs = Math.Abs(input);
        double output;

        if (abs <= dz)
        {
            output = 0.0;
        }
        else
        {
            double sign = Math.Sign(input);
            double rescaled = (abs - dz) / (1.0 - dz);
            double curved = (1.0 - expo) * rescaled + expo * (rescaled * rescaled * rescaled);
            double gained = sign * curved * sens;
            if (invert)
            {
                gained = -gained;
            }
            output = Math.Clamp(gained, -1.0, 1.0);
        }

        if (smooth > 0.0)
        {
            output = previousSmoothedOutput * smooth + output * (1.0 - smooth);
        }

        return Math.Clamp(output, -1.0, 1.0);
    }
}
