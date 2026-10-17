using System.Collections.Concurrent;
using FlightJoy.Core.Logging;

namespace FlightJoy.Core.VJoy;

/// <summary>
/// Serviço feeder real para o driver vJoy 2.1.9.1 via vJoyInterface.dll.
/// Caso o driver ou a DLL não estejam disponíveis no momento, entra automaticamente
/// em Modo Simulação (Preview) mantendo diagnóstico detalhado nos logs.
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
    public string StatusMessage { get; private set; } = "Não inicializado";

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
            EnableSimulationFallback("Sistema operacional não é Windows.");
            return false;
        }

        try
        {
            VJoyNativeMethods.EnsureResolverRegistered();

            if (!VJoyNativeMethods.vJoyEnabled())
            {
                EnableSimulationFallback("Driver vJoy não está habilitado (vJoyEnabled retornou FALSE). Verifique o vJoyConf / Gerenciador de Dispositivos.");
                return false;
            }

            short rawVersion = VJoyNativeMethods.GetvJoyVersion();
            bool versionMatched = VJoyNativeMethods.DriverMatch(out ushort dllVer, out ushort drvVer);
            string dllPathInfo = VJoyNativeMethods.ResolvedDllPath ?? "vJoyInterface.dll (PATH)";

            if (versionMatched)
            {
                _logger.Info("vJoy", $"vJoy detectado (Versão 0x{rawVersion:X}, DLL=0x{dllVer:X}, Driver=0x{drvVer:X}) via '{dllPathInfo}'.");
            }
            else
            {
                _logger.Warn("vJoy", $"Aviso de versão vJoy: DLL (0x{dllVer:X}) difere do Driver (0x{drvVer:X}) em '{dllPathInfo}'. Tentando adquirir mesmo assim.");
            }

            VjdStat status = VJoyNativeMethods.GetVJDStatus(deviceId);
            switch (status)
            {
                case VjdStat.VJD_STAT_OWN:
                    _logger.Info("vJoy", $"Dispositivo vJoy #{deviceId} já pertence a este processo.");
                    break;
                case VjdStat.VJD_STAT_FREE:
                    _logger.Info("vJoy", $"Dispositivo vJoy #{deviceId} está livre. Adquirindo...");
                    break;
                case VjdStat.VJD_STAT_BUSY:
                    EnableSimulationFallback($"Dispositivo vJoy #{deviceId} está ocupado por outro aplicativo (VJD_STAT_BUSY). Feche outros feeders ou escolha outro ID.");
                    return false;
                case VjdStat.VJD_STAT_MISS:
                    EnableSimulationFallback($"Dispositivo vJoy #{deviceId} não está configurado/habilitado no vJoyConf (VJD_STAT_MISS).");
                    return false;
                default:
                    EnableSimulationFallback($"Dispositivo vJoy #{deviceId} em estado desconhecido ({status}).");
                    return false;
            }

            if (status != VjdStat.VJD_STAT_OWN && !VJoyNativeMethods.AcquireVJD(deviceId))
            {
                EnableSimulationFallback($"Falha ao chamar AcquireVJD({deviceId}).");
                return false;
            }

            IsAcquired = true;
            IsSimulationMode = false;
            QueryAxisCapabilities(deviceId);
            ResetToNeutral();

            int buttonCount = VJoyNativeMethods.GetVJDButtonNumber(deviceId);
            StatusMessage = $"vJoy Ativo — Device #{deviceId} (0x{drvVer:X}, {buttonCount} botões)";
            _logger.Info("vJoy", $"Dispositivo vJoy #{deviceId} adquirido com sucesso ({buttonCount} botões habilitados).");
            return true;
        }
        catch (DllNotFoundException ex)
        {
            EnableSimulationFallback($"vJoyInterface.dll não encontrada no sistema ({ex.Message}). Instale o vJoy 2.1.9.1.");
            return false;
        }
        catch (BadImageFormatException ex)
        {
            EnableSimulationFallback($"Incompatibilidade de arquitetura x86/x64 ao carregar vJoyInterface.dll ({ex.Message}).");
            return false;
        }
        catch (Exception ex)
        {
            EnableSimulationFallback($"Erro ao inicializar vJoy: {ex.Message}");
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
        StatusMessage = $"Simulação (vJoy inativo): {reason}";
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
                _logger.Info("vJoy", $"Dispositivo vJoy #{DeviceId} liberado.");
            }
            catch (Exception ex)
            {
                _logger.Warn("vJoy", $"Erro ao liberar vJoy #{DeviceId}: {ex.Message}");
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
                _logger.Warn("vJoy", $"Falha em ResetVJD: {ex.Message}");
            }
        }

        // Centraliza eixos de voo (X, Y, Z, RX, RY, RZ) em 0.0 e sliders em -1.0 (0% acelerador) ou 0.0
        foreach (VJoyAxisUsage axis in Enum.GetValues<VJoyAxisUsage>())
        {
            if (axis == VJoyAxisUsage.None)
                continue;

            double neutralNormalized = (axis == VJoyAxisUsage.SL0) ? -1.0 : 0.0;
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
                    // Ignora erro individual de botão não configurado
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
                _logger.Error("vJoy", $"Erro em SetAxis({axis}): {ex.Message}");
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
                _logger.Error("vJoy", $"Erro em SetBtn({vjoyButtonNumber}): {ex.Message}");
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
