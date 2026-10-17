using System.ComponentModel;
using System.Windows;
using FlightJoy.Windows.ViewModels;

namespace FlightJoy.Windows;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;
    private bool _allowFullExit;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = new MainViewModel(Dispatcher);
        DataContext = _viewModel;
        Closing += OnWindowClosing;
        Closed += (_, _) => _viewModel.Dispose();
    }

    private void OnWindowClosing(object? sender, CancelEventArgs e)
    {
        // Ao clicar no X da janela, permanece aberto e minimizado na barra de tarefas
        if (!_allowFullExit)
        {
            e.Cancel = true;
            WindowState = WindowState.Minimized;
            ShowInTaskbar = true;
        }
    }

    private void OnReconnectVJoyClick(object sender, RoutedEventArgs e)
    {
        _viewModel.AcquireVJoyDevice();
    }

    private void OnToggleServerClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ToggleServer();
    }

    private void OnToggleLogsClick(object sender, RoutedEventArgs e)
    {
        _viewModel.ToggleLogs();
    }

    private void OnForceExitClick(object sender, RoutedEventArgs e)
    {
        _allowFullExit = true;
        Close();
    }
}
