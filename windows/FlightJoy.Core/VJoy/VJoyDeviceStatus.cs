namespace FlightJoy.Core.VJoy;

/// <summary>
/// Estados oficiais VjdStat definidos em vjoyinterface.h (SDK vJoy 2.1.9.1).
/// </summary>
public enum VjdStat : int
{
    VJD_STAT_OWN = 0,
    VJD_STAT_FREE = 1,
    VJD_STAT_BUSY = 2,
    VJD_STAT_MISS = 3,
    VJD_STAT_UNKN = 4
}
