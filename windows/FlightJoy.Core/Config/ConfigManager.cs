using System.Text;
using System.Text.Json;

namespace FlightJoy.Core.Config;

public static class ConfigManager
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    public static string GetDefaultConfigPath()
    {
        return Path.Combine(AppContext.BaseDirectory, "flightjoy.config.json");
    }

    public static AppConfig LoadOrCreateDefault(string? filePath = null)
    {
        string path = filePath ?? GetDefaultConfigPath();
        if (File.Exists(path))
        {
            try
            {
                string json = File.ReadAllText(path, Encoding.UTF8);
                AppConfig? loaded = JsonSerializer.Deserialize<AppConfig>(json, JsonOptions);
                if (loaded != null)
                {
                    NormalizeConfig(loaded);
                    return loaded;
                }
            }
            catch
            {
            }
        }

        AppConfig fresh = new();
        Save(fresh, path);
        return fresh;
    }

    public static void Save(AppConfig config, string? filePath = null)
    {
        ArgumentNullException.ThrowIfNull(config);
        NormalizeConfig(config);
        string path = filePath ?? GetDefaultConfigPath();
        try
        {
            string json = JsonSerializer.Serialize(config, JsonOptions);
            File.WriteAllText(path, json, Encoding.UTF8);
        }
        catch
        {
        }
    }

    private static void NormalizeConfig(AppConfig config)
    {
        if (config.UdpPort < 1024 || config.UdpPort > 65535)
            config.UdpPort = 49152;
        if (config.VJoyDeviceId < 1 || config.VJoyDeviceId > 16)
            config.VJoyDeviceId = 1;
        if (config.WatchdogTimeoutMs < 200 || config.WatchdogTimeoutMs > 10000)
            config.WatchdogTimeoutMs = 1200;

        if (config.AxisMappings == null || config.AxisMappings.Count == 0)
            config.AxisMappings = AppConfig.CreateDefaultAxisMappings();

        if (config.ButtonMappings == null || config.ButtonMappings.Count == 0)
            config.ButtonMappings = AppConfig.CreateDefaultButtonMappings();
    }
}
