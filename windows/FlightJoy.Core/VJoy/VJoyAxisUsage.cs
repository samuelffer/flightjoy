namespace FlightJoy.Core.VJoy;

/// <summary>
/// Constantes oficiais HID_USAGE_* definidas em public.h do SDK vJoy 2.1.9.1.
/// </summary>
public enum VJoyAxisUsage : uint
{
    None = 0x00,
    X = 0x30,
    Y = 0x31,
    Z = 0x32,
    RX = 0x33,
    RY = 0x34,
    RZ = 0x35,
    SL0 = 0x36,
    SL1 = 0x37
}
