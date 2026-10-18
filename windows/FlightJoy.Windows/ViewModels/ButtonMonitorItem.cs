using System.ComponentModel;
using System.Runtime.CompilerServices;
using FlightJoy.Core.Config;

namespace FlightJoy.Windows.ViewModels;

public sealed class ButtonMonitorItem : INotifyPropertyChanged
{
    private readonly ButtonMappingConfig _model;
    private bool _isPressed;

    public event PropertyChangedEventHandler? PropertyChanged;
    public event Action? MappingChanged;

    public ButtonMonitorItem(ButtonMappingConfig model)
    {
        _model = model ?? throw new ArgumentNullException(nameof(model));
    }

    public ButtonMappingConfig Model => _model;

    public int PhoneButtonNumber => _model.PhoneButtonNumber;

    public string PhoneButtonTitle => $"Botão Celular B{PhoneButtonNumber}";

    public int TargetVJoyButton
    {
        get => _model.TargetVJoyButton;
        set
        {
            int clamped = Math.Clamp(value, 1, 32);
            if (_model.TargetVJoyButton != clamped)
            {
                _model.TargetVJoyButton = clamped;
                OnPropertyChanged();
                MappingChanged?.Invoke();
            }
        }
    }

    public string Label
    {
        get => _model.Label;
        set
        {
            if (_model.Label != value)
            {
                _model.Label = value ?? string.Empty;
                OnPropertyChanged();
                MappingChanged?.Invoke();
            }
        }
    }

    public bool IsPressed
    {
        get => _isPressed;
        set
        {
            if (_isPressed != value)
            {
                _isPressed = value;
                OnPropertyChanged();
                OnPropertyChanged(nameof(StateText));
            }
        }
    }

    public string StateText => IsPressed ? "PRESSIONADO" : "LIVRE";

    private void OnPropertyChanged([CallerMemberName] string? name = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
