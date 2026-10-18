using System.Windows;
using System.Windows.Threading;

namespace FlightJoy.Windows;

public partial class App : Application
{
    public static bool IsSelfTestMode { get; private set; }

    protected override void OnStartup(StartupEventArgs e)
    {
        if (e.Args.Any(a => string.Equals(a, "--self-test", StringComparison.OrdinalIgnoreCase)))
        {
            IsSelfTestMode = true;
        }

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        base.OnStartup(e);
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        if (IsSelfTestMode)
        {
            Console.Error.WriteLine($"SELF-TEST ERROR: {e.Exception}");
            Environment.Exit(1);
            return;
        }

        MessageBox.Show(
            $"Erro inesperado no FlightJoy:\n\n{e.Exception.Message}",
            "FlightJoy — Erro",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        e.Handled = true;
    }
}
