using System.ComponentModel;
using System.Runtime.CompilerServices;
using FlightJoy.Core.Config;
using FlightJoy.Core.VJoy;

namespace FlightJoy.Windows.ViewModels;

public sealed class AxisMonitorItem : INotifyPropertyChanged
{
    private readonly AxisMappingConfig _model;
    private double _rawNormalized;
    private double _processedNormalized;
    private int _vjoyRawValue = MockVJoyFeeder.DefaultCenter;

    public event PropertyChangedEventHandler? PropertyChanged;
    public event Action? MappingChanged;

    public AxisMonitorItem(AxisMappingConfig model)
    {
        _model = model ?? throw new ArgumentNullException(nameof(model));
    }

    public AxisMappingConfig Model => _model;

    public SourceAxis Source => _model.Source;

    public string DisplayName => _model.Source switch
    {
        SourceAxis.Pitch => "Pitch (Profundor / Arfagem)",
        SourceAxis.Roll => "Roll (Aileron / Rolagem)",
        SourceAxis.Yaw => "Yaw (Leme / Guinada)",
        SourceAxis.Throttle => "Throttle (Manete Potência)",
        SourceAxis.Aux => "Auxiliar (Flaps / Trim)",
        _ => _model.Source.ToString()
    };

    public VJoyAxisUsage TargetAxis
    {
        get => _model.TargetAxis;
        set
        {
            if (_model.TargetAxis != value)
            {
                _model.TargetAxis = value;
                OnPropertyChanged();
                MappingChanged?.Invoke();
            }
        }
    }

    public bool Invert
    {
        get => _model.Invert;
        set
        {
            if (_model.Invert != value)
            {
                _model.Invert = value;
                OnPropertyChanged();
                MappingChanged?.Invoke();
            }
        }
    }

    public double Sensitivity
    {
        get => _model.Sensitivity;
        set
        {
            double clamped = Math.Round(Math.Clamp(value, 0.1, 3.0), 2);
            if (Math.Abs(_model.Sensitivity - clamped) > 0.001)
            {
                _model.Sensitivity = clamped;
                OnPropertyChanged();
                OnPropertyChanged(nameof(SensitivityLabel));
                MappingChanged?.Invoke();
            }
        }
    }

    public string SensitivityLabel => $"{Sensitivity:0.00}x";

    public double Deadzone
    {
        get => _model.Deadzone;
        set
        {
            double clamped = Math.Round(Math.Clamp(value, 0.0, 0.40), 2);
            if (Math.Abs(_model.Deadzone - clamped) > 0.001)
            {
                _model.Deadzone = clamped;
                OnPropertyChanged();
                OnPropertyChanged(nameof(DeadzoneLabel));
                MappingChanged?.Invoke();
            }
        }
    }

    public string DeadzoneLabel => $"{(int)Math.Round(Deadzone * 100)}%";

    public double Exponential
    {
        get => _model.Exponential;
        set
        {
            double clamped = Math.Round(Math.Clamp(value, 0.0, 1.0), 2);
            if (Math.Abs(_model.Exponential - clamped) > 0.001)
            {
                _model.Exponential = clamped;
                OnPropertyChanged();
                OnPropertyChanged(nameof(ExponentialLabel));
                MappingChanged?.Invoke();
            }
        }
    }

    public string ExponentialLabel => $"{(int)Math.Round(Exponential * 100)}%";

    public double Smoothing
    {
        get => _model.Smoothing;
        set
        {
            double clamped = Math.Round(Math.Clamp(value, 0.0, 0.90), 2);
            if (Math.Abs(_model.Smoothing - clamped) > 0.001)
            {
                _model.Smoothing = clamped;
                OnPropertyChanged();
                OnPropertyChanged(nameof(SmoothingLabel));
                MappingChanged?.Invoke();
            }
        }
    }

    public string SmoothingLabel => $"{(int)Math.Round(Smoothing * 100)}%";

    public double RawNormalized
    {
        get => _rawNormalized;
        set
        {
            if (Math.Abs(_rawNormalized - value) > 0.0005)
            {
                _rawNormalized = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(RawPercentText));
            }
        }
    }

    public double ProcessedNormalized
    {
        get => _processedNormalized;
        set
        {
            if (Math.Abs(_processedNormalized - value) > 0.0005)
            {
                _processedNormalized = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(ProcessedPercentBarValue));
                OnPropertyChanged(nameof(ProcessedPercentText));
            }
        }
    }

    /// <summary>Valor de 0 a 100 para ProgressBar (onde 50 é o centro neutro 0.0)</summary>
    public double ProcessedPercentBarValue => (ProcessedNormalized + 1.0) * 50.0;

    public string RawPercentText => $"{RawNormalized * 100.0:+00.0;-00.0;0.0}%";

    public string ProcessedPercentText => $"{ProcessedNormalized * 100.0:+00.0;-00.0;0.0}%";

    public int VJoyRawValue
    {
        get => _vjoyRawValue;
        set
        {
            if (_vjoyRawValue != value)
            {
                _vjoyRawValue = value;
                OnPropertyChanged();
            }
        }
    }

    private void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
