using System.Collections.Concurrent;

namespace FlightJoy.Core.VJoy;

/// <summary>
/// Implementação em memória de IVJoyFeeder usada em testes automatizados
/// e quando o driver vJoy não está ativo no ambiente.
/// </summary>
public sealed class MockVJoyFeeder : IVJoyFeeder
{
    public const int DefaultMin = 1;
    public const int DefaultMax = 32768;
    public const int DefaultCenter = 16384;

    private readonly ConcurrentDictionary<VJoyAxisUsage, int> _axes = new();
    private readonly ConcurrentDictionary<int, bool> _buttons = new();

    public bool IsAcquired { get; private set; }
    public bool IsSimulationMode => true;
    public uint DeviceId { get; private set; } = 1;
    public string StatusMessage { get; private set; } = "Modo Simulação (Mock vJoy)";

    public bool Acquire(uint deviceId = 1)
    {
        DeviceId = deviceId;
        IsAcquired = true;
        ResetToNeutral();
        StatusMessage = $"Modo Simulação Ativo (Device #{deviceId})";
        return true;
    }

    public void Relinquish()
    {
        IsAcquired = false;
        StatusMessage = "Dispositivo Simulado Liberado";
    }

    public void ResetToNeutral()
    {
        foreach (VJoyAxisUsage axis in Enum.GetValues<VJoyAxisUsage>())
        {
            if (axis != VJoyAxisUsage.None)
            {
                _axes[axis] = DefaultCenter;
            }
        }

        for (int i = 1; i <= 32; i++)
        {
            _buttons[i] = false;
        }
    }

    public void SetNormalizedAxis(VJoyAxisUsage axis, double normalizedValue)
    {
        if (axis == VJoyAxisUsage.None)
            return;

        double clamped = Math.Clamp(normalizedValue, -1.0, 1.0);
        int raw = ConvertNormalizedToRaw(clamped, DefaultMin, DefaultMax);
        _axes[axis] = raw;
    }

    public void SetButton(int vjoyButtonNumber, bool pressed)
    {
        if (vjoyButtonNumber < 1 || vjoyButtonNumber > 32)
            return;

        _buttons[vjoyButtonNumber] = pressed;
    }

    public int GetLastRawAxisValue(VJoyAxisUsage axis)
    {
        return _axes.TryGetValue(axis, out int val) ? val : DefaultCenter;
    }

    public bool GetLastButtonState(int vjoyButtonNumber)
    {
        return _buttons.TryGetValue(vjoyButtonNumber, out bool state) && state;
    }

    public static int ConvertNormalizedToRaw(double normalized, int min, int max)
    {
        double clamped = Math.Clamp(normalized, -1.0, 1.0);
        double t = (clamped + 1.0) * 0.5;
        int value = (int)Math.Round(min + t * (max - min));
        return Math.Clamp(value, min, max);
    }

    public void Dispose()
    {
        Relinquish();
    }
}
