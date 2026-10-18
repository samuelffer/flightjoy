using FlightJoy.Core.Network;
using Xunit;

namespace FlightJoy.Tests;

public sealed class WatchdogTests
{
    [Fact]
    public void EvaluateTimeout_TriggersOnceAfterTimeoutMs()
    {
        int triggeredCount = 0;
        var watchdog = new ConnectionWatchdog(timeoutMs: 1000);
        watchdog.TimeoutDetected += () => triggeredCount++;

        watchdog.StartSession(nowMs: 5000);

        Assert.False(watchdog.EvaluateTimeout(nowMs: 5500));
        Assert.Equal(0, triggeredCount);

        watchdog.RecordActivity(nowMs: 5800);
        Assert.False(watchdog.EvaluateTimeout(nowMs: 6500));

        // 1000ms após última atividade em 5800 -> 6800
        Assert.True(watchdog.EvaluateTimeout(nowMs: 6800));
        Assert.Equal(1, triggeredCount);

        // Não deve disparar novamente até nova atividade
        Assert.False(watchdog.EvaluateTimeout(nowMs: 7500));
        Assert.Equal(1, triggeredCount);
    }
}
