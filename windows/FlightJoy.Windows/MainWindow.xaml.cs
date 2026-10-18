using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using FlightJoy.Windows.ViewModels;

namespace FlightJoy.Windows;

public partial class MainWindow : Window
{
    private const int WmTrayIcon = 0x8000 + 101;
    private const int WmLButtonUp = 0x0202;
    private const int WmLButtonDblClk = 0x0203;
    private const int WmRButtonUp = 0x0205;

    private const uint NimAdd = 0x00000000;
    private const uint NimModify = 0x00000001;
    private const uint NimDelete = 0x00000002;
    private const uint NifMessage = 0x00000001;
    private const uint NifIcon = 0x00000002;
    private const uint NifTip = 0x00000004;

    private const uint TpmReturnCmd = 0x0100;
    private const uint TpmRightButton = 0x0002;
    private const uint MfString = 0x00000000;
    private const uint MfSeparator = 0x00000800;

    private readonly MainViewModel _viewModel;
    private bool _allowFullExit;
    private bool _trayIconAdded;
    private IntPtr _hwnd;
    private IntPtr _hIcon;
    private NotifyIconData _nid;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = new MainViewModel(Dispatcher);
        DataContext = _viewModel;
        SourceInitialized += OnWindowSourceInitialized;
        ContentRendered += OnWindowContentRendered;
        Closing += OnWindowClosing;
        Closed += OnWindowClosed;
    }

    private void OnWindowContentRendered(object? sender, EventArgs e)
    {
        if (App.IsSelfTestMode)
        {
            _allowFullExit = true;
            Dispatcher.BeginInvoke(new Action(Close), System.Windows.Threading.DispatcherPriority.ApplicationIdle);
        }
    }

    private void OnWindowSourceInitialized(object? sender, EventArgs e)
    {
        _hwnd = new WindowInteropHelper(this).Handle;
        HwndSource? source = HwndSource.FromHwnd(_hwnd);
        source?.AddHook(WndProc);
        EnsureTrayIconRegistered();
    }

    private void EnsureTrayIconRegistered()
    {
        if (_trayIconAdded || _hwnd == IntPtr.Zero)
            return;

        try
        {
            string? exePath = Environment.ProcessPath;
            if (!string.IsNullOrEmpty(exePath))
            {
                ushort iconIndex = 0;
                _hIcon = ExtractAssociatedIconW(IntPtr.Zero, exePath, ref iconIndex);
            }

            if (_hIcon == IntPtr.Zero)
            {
                _hIcon = LoadIconW(IntPtr.Zero, new IntPtr(32512)); // IDI_APPLICATION
            }

            _nid = new NotifyIconData
            {
                cbSize = (uint)Marshal.SizeOf<NotifyIconData>(),
                hWnd = _hwnd,
                uID = 1001,
                uFlags = NifMessage | NifIcon | NifTip,
                uCallbackMessage = WmTrayIcon,
                hIcon = _hIcon,
                szTip = "FlightJoy — Segundo Plano (Clique para abrir)"
            };

            _trayIconAdded = Shell_NotifyIconW(NimAdd, ref _nid);
        }
        catch
        {
            // Ignora falha silenciosamente em ambientes sem bandeja
        }
    }

    private void RemoveTrayIcon()
    {
        if (_trayIconAdded)
        {
            try
            {
                Shell_NotifyIconW(NimDelete, ref _nid);
            }
            catch
            {
            }
            _trayIconAdded = false;
        }
    }

    private void HideToSystemTray()
    {
        EnsureTrayIconRegistered();
        ShowInTaskbar = false;
        Hide();
    }

    private void RestoreFromSystemTray()
    {
        ShowInTaskbar = true;
        Show();
        WindowState = WindowState.Normal;
        Activate();
        Topmost = true;
        Topmost = false;
        Focus();
    }

    private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WmTrayIcon)
        {
            int mouseMsg = (int)(lParam.ToInt64() & 0xFFFF);
            if (mouseMsg is WmLButtonUp or WmLButtonDblClk)
            {
                RestoreFromSystemTray();
                handled = true;
            }
            else if (mouseMsg == WmRButtonUp)
            {
                ShowTrayContextMenu();
                handled = true;
            }
        }
        return IntPtr.Zero;
    }

    private void ShowTrayContextMenu()
    {
        IntPtr hMenu = CreatePopupMenu();
        if (hMenu == IntPtr.Zero)
            return;

        try
        {
            AppendMenuW(hMenu, MfString, new UIntPtr(1), "Abrir FlightJoy");
            AppendMenuW(hMenu, MfSeparator, UIntPtr.Zero, null);
            AppendMenuW(hMenu, MfString, new UIntPtr(2), "Encerrar FlightJoy");

            GetCursorPos(out PointWin32 pt);
            SetForegroundWindow(_hwnd);

            uint cmd = TrackPopupMenuEx(
                hMenu,
                TpmReturnCmd | TpmRightButton,
                pt.X,
                pt.Y,
                _hwnd,
                IntPtr.Zero);

            if (cmd == 1)
            {
                RestoreFromSystemTray();
            }
            else if (cmd == 2)
            {
                _allowFullExit = true;
                Close();
            }
        }
        finally
        {
            DestroyMenu(hMenu);
        }
    }

    private void OnTitleBarMouseDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton == MouseButton.Left && e.ButtonState == MouseButtonState.Pressed)
        {
            // Impede maximizar com duplo clique no cabeçalho; apenas move a janela
            if (e.ClickCount == 1)
            {
                DragMove();
            }
        }
    }

    private void OnMinimizeTitleBarClick(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void OnCloseToTrayTitleBarClick(object sender, RoutedEventArgs e)
    {
        HideToSystemTray();
    }

    private void OnWindowClosing(object? sender, CancelEventArgs e)
    {
        // Ao fechar no X, vai direto para segundo plano nos Ícones Ocultos (System Tray)
        if (!_allowFullExit)
        {
            e.Cancel = true;
            HideToSystemTray();
        }
    }

    private void OnWindowClosed(object? sender, EventArgs e)
    {
        RemoveTrayIcon();
        _viewModel.Dispose();
    }

    private void OnReconnectVJoyClick(object sender, RoutedEventArgs e)
    {
        _viewModel.AcquireVJoyDevice();
    }

    private void OnCycleVJoyDeviceClick(object sender, RoutedEventArgs e)
    {
        uint next = (_viewModel.VJoyDeviceId % 4) + 1;
        _viewModel.VJoyDeviceId = next;
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

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NotifyIconData
    {
        public uint cbSize;
        public IntPtr hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public IntPtr hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string szTip;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct PointWin32
    {
        public int X;
        public int Y;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern bool Shell_NotifyIconW(uint dwMessage, ref NotifyIconData lpData);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern IntPtr ExtractAssociatedIconW(IntPtr hInst, string lpIconPath, ref ushort lpiIcon);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern IntPtr LoadIconW(IntPtr hInstance, IntPtr lpIconName);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern IntPtr CreatePopupMenu();

    [DllImport("user32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern bool AppendMenuW(IntPtr hMenu, uint uFlags, UIntPtr uIDNewItem, string? lpNewItem);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern uint TrackPopupMenuEx(IntPtr hmenu, uint fuFlags, int x, int y, IntPtr hwnd, IntPtr lptpm);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool DestroyMenu(IntPtr hMenu);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool GetCursorPos(out PointWin32 lpPoint);

    [DllImport("user32.dll", ExactSpelling = true)]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
