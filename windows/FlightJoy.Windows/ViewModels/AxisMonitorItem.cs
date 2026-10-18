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

    public AxisMonitorItem(AxisMappingConfig model)
    {
        _model = model ?? throw new ArgumentNullException(nameof(model));
        if (_model.Source is SourceAxis.Throttle or SourceAxis.Aux or SourceAxis.Brake)
        {
            _rawNormalized = -1.0;
            _processedNormalized = -1.0;
            _vjoyRawValue = MockVJoyFeeder.DefaultMin;
        }
    }

    public AxisMappingConfig Model => _model;
    public SourceAxis Source => _model.Source;

    public double RawNormalized
    {
        get => _rawNormalized;
        set
        {
            if (Math.Abs(_rawNormalized - value) > 0.0005)
            {
                _rawNormalized = value;
                OnPropertyChanged();
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
            }
        }
    }

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
