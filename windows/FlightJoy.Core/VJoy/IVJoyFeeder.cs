namespace FlightJoy.Core.VJoy;

public interface IVJoyFeeder : IDisposable
{
    bool IsAcquired { get; }
    bool IsSimulationMode { get; }
    uint DeviceId { get; }
    string StatusMessage { get; }

    bool Acquire(uint deviceId = 1);
    void Relinquish();
    void ResetToNeutral();
    void SetNormalizedAxis(VJoyAxisUsage axis, double normalizedValue);
    void SetButton(int vjoyButtonNumber, bool pressed);
    int GetLastRawAxisValue(VJoyAxisUsage axis);
    bool GetLastButtonState(int vjoyButtonNumber);
}
