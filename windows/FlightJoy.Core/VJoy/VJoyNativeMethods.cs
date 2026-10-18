using System.Reflection;
using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace FlightJoy.Core.VJoy;

/// <summary>
/// P/Invoke para as funções C exportadas pela vJoyInterface.dll oficial (vJoy 2.1.9.1).
/// Inclui resolvedor automático que localiza a DLL instalada pelo driver vJoy no Windows
/// via Registro ou em "C:\Program Files\vJoy\x64\vJoyInterface.dll".
/// </summary>
public static class VJoyNativeMethods
{
    public const string DllName = "vJoyInterface.dll";
    public const string VJoyUninstallRegistryKey =
        @"HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\{8E31F76F-74C3-47F1-9550-E041EEDC5FBB}_is1";

    private static int _resolverInitialized;
    public static string? ResolvedDllPath { get; private set; }

    static VJoyNativeMethods()
    {
        EnsureResolverRegistered();
    }

    public static void EnsureResolverRegistered()
    {
        if (Interlocked.Exchange(ref _resolverInitialized, 1) == 0)
        {
            NativeLibrary.SetDllImportResolver(typeof(VJoyNativeMethods).Assembly, ResolveVJoyLibrary);
        }
    }

    private static IntPtr ResolveVJoyLibrary(string libraryName, Assembly assembly, DllImportSearchPath? searchPath)
    {
        if (!string.Equals(libraryName, DllName, StringComparison.OrdinalIgnoreCase))
            return IntPtr.Zero;

        foreach (string candidatePath in GetCandidateDllPaths())
        {
            if (File.Exists(candidatePath) && NativeLibrary.TryLoad(candidatePath, out IntPtr handle))
            {
                ResolvedDllPath = candidatePath;
                return handle;
            }
        }

        if (NativeLibrary.TryLoad(libraryName, assembly, searchPath, out IntPtr defaultHandle))
        {
            ResolvedDllPath = libraryName;
            return defaultHandle;
        }

        return IntPtr.Zero;
    }

    public static IEnumerable<string> GetCandidateDllPaths()
    {
        bool is64Bit = Environment.Is64BitProcess;
        string archSubfolder = is64Bit ? "x64" : "x86";

        // 1. Diretório da aplicação (caso o usuário coloque a DLL junto ao .exe)
        yield return Path.Combine(AppContext.BaseDirectory, DllName);
        yield return Path.Combine(AppContext.BaseDirectory, archSubfolder, DllName);

        // 2. Registro oficial do instalador vJoy 2.1.9.1 no Windows
        if (OperatingSystem.IsWindows())
        {
            string? regArchDir = null;
            string? regInstallDir = null;
            try
            {
                string valueName = is64Bit ? "DllX64Location" : "DllX86Location";
                regArchDir = Registry.GetValue(VJoyUninstallRegistryKey, valueName, null) as string;
                regInstallDir = Registry.GetValue(VJoyUninstallRegistryKey, "InstallLocation", null) as string;
            }
            catch
            {
                // Ignora caso sem permissão de leitura no registro
            }

            if (!string.IsNullOrWhiteSpace(regArchDir))
                yield return Path.Combine(regArchDir, DllName);

            if (!string.IsNullOrWhiteSpace(regInstallDir))
            {
                yield return Path.Combine(regInstallDir, archSubfolder, DllName);
                yield return Path.Combine(regInstallDir, DllName);
            }
        }

        // 3. Caminho padrão de instalação do vJoy 2.1.9.1 no Windows
        string programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        if (!string.IsNullOrWhiteSpace(programFiles))
        {
            yield return Path.Combine(programFiles, "vJoy", archSubfolder, DllName);
            yield return Path.Combine(programFiles, "vJoy", DllName);
        }

        yield return Path.Combine(@"C:\Program Files\vJoy", archSubfolder, DllName);
    }

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern short GetvJoyVersion();

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool vJoyEnabled();

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool DriverMatch(out ushort dllVer, out ushort drvVer);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern VjdStat GetVJDStatus(uint rID);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool AcquireVJD(uint rID);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void RelinquishVJD(uint rID);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool ResetVJD(uint rID);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool ResetButtons(uint rID);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int GetVJDButtonNumber(uint rID);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool GetVJDAxisExist(uint rID, uint axis);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool GetVJDAxisMax(uint rID, uint axis, out int max);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool GetVJDAxisMin(uint rID, uint axis, out int min);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetAxis(int value, uint rID, uint axis);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetBtn([MarshalAs(UnmanagedType.Bool)] bool value, uint rID, byte nBtn);
}
