namespace FlightJoy.Core.Config;

public sealed class ButtonMappingConfig
{
    /// <summary>Índice do botão no celular (1 a 8)</summary>
    public int PhoneButtonNumber { get; set; }

    /// <summary>Número do botão vJoy alvo (1 a 32)</summary>
    public int TargetVJoyButton { get; set; }

    /// <summary>Nome descritivo da função de voo (ex: Gatilho, Trem de Pouso, Flaps+)</summary>
    public string Label { get; set; } = string.Empty;

    public ButtonMappingConfig Clone() => new()
    {
        PhoneButtonNumber = PhoneButtonNumber,
        TargetVJoyButton = TargetVJoyButton,
        Label = Label
    };
}
