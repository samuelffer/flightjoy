using System.Collections.Concurrent;
using FlightJoy.Core.Logging;

namespace FlightJoy.Core.VJoy;

/// <summary>
/// Real feeder service for vJoy 2.1.9.1 driver via vJoyInterface.dll.
/// Falls back to Simulation Mode (Preview) if the driver or DLL is unavailable.
/// </summary>
public sealed class VJoyFeederService : IVJoyFeeder
{
    private readonly FlightLogger _logger;
    private readonly ConcurrentDictionary<VJoyAxisUsage, (int Min, int Max)> _axisBounds = new();
    private readonly ConcurrentDictionary<VJoyAxisUsage, int> _lastAxisValues = new();
    private readonly ConcurrentDictionary<int, bool> _lastButtonValues = new();
    private bool _disposed;

    public bool IsAcquired { get; private set; }
    public bool IsSimulationMode { get; private set; }
    public uint DeviceId { get; private set; } = 1;
    public string StatusMessage { get; private set; } = "Not initialized";

    public VJoyFeederService(FlightLogger logger)
    {
        _logger = logger;
        InitializeDefaultBounds();
    }

    private void InitializeDefaultBounds()
    {
        foreach (VJoyAxisUsage axis in Enum.GetValues<VJoyAxisUsage>())
        {
            if (axis != VJoyAxisUsage.None)
            {
                _axisBounds[axis] = (MockVJoyFeeder.DefaultMin, MockVJoyFeeder.DefaultMax);
                _lastAxisValues[axis] = MockVJoyFeeder.DefaultCenter;
            }
        }
    }

    public bool Acquire(uint deviceId = 1)
    {
        Relinquish();
        DeviceId = deviceId;

        if (!OperatingSystem.IsWindows())
        {
            EnableSimulationFallback("OS is not Windows.");
            return false;
        }

        try
        {
            VJoyNativeMethods.EnsureResolverRegistered();

            if (!VJoyNativeMethods.vJoyEnabled())
            {
                EnableSimulationFallback("vJoy driver is not enabled (vJoyEnabled returned FALSE). Check vJoyConf / Device Manager.");
                return false;
            }

            short rawVersion = VJoyNativeMethods.GetvJoyVersion();
            bool versionMatched = VJoyNativeMethods.DriverMatch(out ushort dllVer, out ushort drvVer);
            string dllPathInfo = VJoyNativeMethods.ResolvedDllPath ?? "vJoyInterface.dll (PATH)";

            if (versionMatched)
            {
                _logger.Info("vJoy", $"vJoy detected (Version 0x{rawVersion:X}, DLL=0x{dllVer:X}, Driver=0x{drvVer:X}) via '{dllPathInfo}'.");
            }
            else
            {
                _logger.Warn("vJoy", $"vJoy version warning: DLL (0x{dllVer:X}) differs from Driver (0x{drvVer:X}) at '{dllPathInfo}'. Attempting to acquire anyway.");
            }

            VjdStat status = VJoyNativeMethods.GetVJDStatus(deviceId);
            switch (status)
            {
                case VjdStat.VJD_STAT_OWN:
                    _logger.Info("vJoy", $"vJoy Device #{deviceId} is already owned by this process.");
                    break;
                case VjdStat.VJD_STAT_FREE:
                    _logger.Info("vJoy", $"vJoy Device #{deviceId} is free. Acquiring...");
                    break;
                case VjdStat.VJD_STAT_BUSY:
                    EnableSimulationFallback($"vJoy Device #{deviceId} is busy (used by another app). Close other feeders or switch Device ID.");
                    return false;
                case VjdStat.VJD_STAT_MISS:
                    EnableSimulationFallback($"vJoy Device #{deviceId} is not configured/enabled in vJoyConf.");
                    return false;
                default:
                    EnableSimulationFallback($"vJoy Device #{deviceId} is in unknown state ({status}).");
                    return false;
            }

            if (status != VjdStat.VJD_STAT_OWN && !VJoyNativeMethods.AcquireVJD(deviceId))
            {
                EnableSimulationFallback($"Failed to call AcquireVJD({deviceId}).");
                return false;
            }

            IsAcquired = true;
            IsSimulationMode = false;
            QueryAxisCapabilities(deviceId);
            ResetToNeutral();

            int buttonCount = VJoyNativeMethods.GetVJDButtonNumber(deviceId);
            StatusMessage = $"vJoy Active — Device #{deviceId} (0x{drvVer:X}, {buttonCount} buttons)";
            _logger.Info("vJoy", $"vJoy Device #{deviceId} acquired ({buttonCount} buttons enabled).");
            return true;
        }
        catch (DllNotFoundException ex)
        {
            EnableSimulationFallback($"vJoyInterface.dll not found ({ex.Message}). Install vJoy 2.1.9.1.");
            return false;
        }
        catch (BadImageFormatException ex)
        {
            EnableSimulationFallback($"x86/x64 architecture mismatch loading vJoyInterface.dll ({ex.Message}).");
            return false;
        }
        catch (Exception ex)
        {
            EnableSimulationFallback($"Error initializing vJoy: {ex.Message}");
            return false;
        }
    }

    private void QueryAxisCapabilities(uint deviceId)
    {
        foreach (VJoyAxisUsage axis in Enum.GetValues<VJoyAxisUsage>())
        {
            if (axis == VJoyAxisUsage.None)
                continue;

            uint usage = (uint)axis;
            if (VJoyNativeMethods.GetVJDAxisExist(deviceId, usage) &&
                VJoyNativeMethods.GetVJDAxisMin(deviceId, usage, out int min) &&
                VJoyNativeMethods.GetVJDAxisMax(deviceId, usage, out int max) &&
                max > min)
            {
                _axisBounds[axis] = (min, max);
            }
            else
            {
                _axisBounds[axis] = (MockVJoyFeeder.DefaultMin, MockVJoyFeeder.DefaultMax);
            }
        }
    }

    private void EnableSimulationFallback(string reason)
    {
        IsAcquired = false;
        IsSimulationMode = true;
        StatusMessage = $"Simulation (vJoy inactive): {reason}";
        _logger.Warn("vJoy", StatusMessage);
    }

    public void Relinquish()
    {
        if (IsAcquired && !IsSimulationMode)
        {
            try
            {
                VJoyNativeMethods.ResetVJD(DeviceId);
                VJoyNativeMethods.RelinquishVJD(DeviceId);
                _logger.Info("vJoy", $"vJoy Device #{DeviceId} released.");
            }
            catch (Exception ex)
            {
                _logger.Warn("vJoy", $"Error releasing vJoy #{DeviceId}: {ex.Message}");
            }
        }

        IsAcquired = false;
    }

    public void ResetToNeutral()
    {
        if (IsAcquired && !IsSimulationMode)
        {
            try
            {
                VJoyNativeMethods.ResetVJD(DeviceId);
                VJoyNativeMethods.ResetButtons(DeviceId);
            }
            catch (Exception ex)
            {
                _logger.Warn("vJoy", $"Failed in ResetVJD: {ex.Message}");
            }
        }

        foreach (VJoyAxisUsage axis in Enum.GetValues<VJoyAxisUsage>())
        {
            if (axis == VJoyAxisUsage.None)
                continue;

            double neutralNormalized = (axis is VJoyAxisUsage.SL0 or VJoyAxisUsage.SL1) ? -1.0 : 0.0;
            SetNormalizedAxis(axis, neutralNormalized);
        }

        for (int btn = 1; btn <= 32; btn++)
        {
            _lastButtonValues[btn] = false;
            if (IsAcquired && !IsSimulationMode)
            {
                try
                {
                    VJoyNativeMethods.SetBtn(false, DeviceId, (byte)btn);
                }
                catch
                {
                }
            }
        }
    }

    public void SetNormalizedAxis(VJoyAxisUsage axis, double normalizedValue)
    {
        if (axis == VJoyAxisUsage.None)
            return;

        (int min, int max) = _axisBounds.TryGetValue(axis, out var bounds)
            ? bounds
            : (MockVJoyFeeder.DefaultMin, MockVJoyFeeder.DefaultMax);

        int rawValue = MockVJoyFeeder.ConvertNormalizedToRaw(normalizedValue, min, max);
        _lastAxisValues[axis] = rawValue;

        if (IsAcquired && !IsSimulationMode)
        {
            try
            {
                VJoyNativeMethods.SetAxis(rawValue, DeviceId, (uint)axis);
            }
            catch (Exception ex)
            {
                _logger.Error("vJoy", $"Error in SetAxis({axis}): {ex.Message}");
            }
        }
    }

    public void SetButton(int vjoyButtonNumber, bool pressed)
    {
        if (vjoyButtonNumber < 1 || vjoyButtonNumber > 32)
            return;

        bool previous = _lastButtonValues.TryGetValue(vjoyButtonNumber, out bool prev) && prev;
        _lastButtonValues[vjoyButtonNumber] = pressed;

        if (IsAcquired && !IsSimulationMode && previous != pressed)
        {
            try
            {
                VJoyNativeMethods.SetBtn(pressed, DeviceId, (byte)vjoyButtonNumber);
            }
            catch (Exception ex)
            {
                _logger.Error("vJoy", $"Error in SetBtn({vjoyButtonNumber}): {ex.Message}");
            }
        }
    }

    public int GetLastRawAxisValue(VJoyAxisUsage axis)
    {
        return _lastAxisValues.TryGetValue(axis, out int val) ? val : MockVJoyFeeder.DefaultCenter;
    }

    public bool GetLastButtonState(int vjoyButtonNumber)
    {
        return _lastButtonValues.TryGetValue(vjoyButtonNumber, out bool state) && state;
    }

    public void Dispose()
    {
        if (_disposed)
            return;
        _disposed = true;
        Relinquish();
    }
}
