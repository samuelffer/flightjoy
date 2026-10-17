using System.Diagnostics;
using System.IO;
using System.Windows;
using FlightJoy.Windows.ViewModels;

namespace FlightJoy.Windows;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = new MainViewModel(Dispatcher);
        DataContext = _viewModel;
        Closed += (_, _) => _viewModel.Dispose();
    }

    private void OnGeneratePinClick(object sender, RoutedEventArgs e)
    {
        _viewModel.GenerateNewPin();
    }

    private void OnReconnectVJoyClick(object sender, RoutedEventArgs e)
    {
        _viewModel.AcquireVJoyDevice();
    }

    private void OnToggleServerClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ToggleServer();
    }

    private void OnCalibrateCenterClick(object sender, RoutedEventArgs e)
    {
        _viewModel.CalibrateCenterOnPc();
    }

    private void OnResetCalibrationClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ResetPcCalibration();
    }

    private void OnSaveConfigClick(object sender, RoutedEventArgs e)
    {
        _viewModel.SaveConfiguration();
    }

    private void OnRestoreDefaultsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.RestoreDefaults();
    }

    private void OnClearLogsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ClearLogs();
    }

    private void OnOpenLogsFolderClick(object sender, RoutedEventArgs e)
    {
        try
        {
            string folder = _viewModel.LogDirectoryPath;
            Directory.CreateDirectory(folder);
            Process.Start(new ProcessStartInfo
            {
                FileName = folder,
                UseShellExecute = true
            });
        }
        catch
        {
        }
    }
}
