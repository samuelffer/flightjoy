using System.Runtime.InteropServices;

namespace FlightJoy.Core.Audio;

/// <summary>
/// Synthesizes minimalist 16-bit PCM WAV sound effects in memory:
/// - Connected: 1 pleasant harmonic chime.
/// - Disconnected: 2 soft tones.
/// </summary>
public static class SoundNotifier
{
    private const uint SND_SYNC = 0x0000;
    private const uint SND_NODEFAULT = 0x0002;
    private const uint SND_MEMORY = 0x0004;

    private static readonly byte[] ConnectedChimeWav = BuildConnectedSingleChimeWav();
    private static readonly byte[] DisconnectedDoubleWav = BuildDisconnectedDoubleToneWav();

    [DllImport("winmm.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool PlaySound(byte[] pszSound, IntPtr hmod, uint fdwSound);

    /// <summary>
    /// Plays 1 harmonic chime when a phone connects.
    /// </summary>
    public static void PlayConnected()
    {
        if (!OperatingSystem.IsWindows())
            return;

        _ = Task.Run(() =>
        {
            try
            {
                PlaySound(ConnectedChimeWav, IntPtr.Zero, SND_MEMORY | SND_SYNC | SND_NODEFAULT);
            }
            catch
            {
            }
        });
    }

    /// <summary>
    /// Plays 2 soft tones when a phone disconnects.
    /// </summary>
    public static void PlayDisconnected()
    {
        if (!OperatingSystem.IsWindows())
            return;

        _ = Task.Run(() =>
        {
            try
            {
                PlaySound(DisconnectedDoubleWav, IntPtr.Zero, SND_MEMORY | SND_SYNC | SND_NODEFAULT);
            }
            catch
            {
            }
        });
    }

    public static byte[] BuildConnectedSingleChimeWav()
    {
        const int sampleRate = 44100;
        const double durationSec = 0.22;
        int totalSamples = (int)(sampleRate * durationSec);
        short[] pcm = new short[totalSamples];

        for (int i = 0; i < totalSamples; i++)
        {
            double t = (double)i / sampleRate;
            double attack = Math.Min(1.0, t / 0.012);
            double decay = Math.Exp(-t * 14.0);
            double env = attack * decay;

            double wave = 0.70 * Math.Sin(2.0 * Math.PI * 880.0 * t)
                        + 0.30 * Math.Sin(2.0 * Math.PI * 1318.5 * t);

            pcm[i] = (short)Math.Clamp(wave * env * 14000.0, short.MinValue, short.MaxValue);
        }

        return EncodeWavePcm16(pcm, sampleRate);
    }

    public static byte[] BuildDisconnectedDoubleToneWav()
    {
        const int sampleRate = 44100;
        const double tone1Sec = 0.11;
        const double gapSec = 0.055;
        const double tone2Sec = 0.13;
        double totalSec = tone1Sec + gapSec + tone2Sec;

        int totalSamples = (int)(sampleRate * totalSec);
        short[] pcm = new short[totalSamples];

        for (int i = 0; i < totalSamples; i++)
        {
            double t = (double)i / sampleRate;
            double sampleVal = 0.0;

            if (t < tone1Sec)
            {
                double localT = t;
                double env = Math.Min(1.0, localT / 0.008) * Math.Exp(-localT * 18.0);
                sampleVal = Math.Sin(2.0 * Math.PI * 587.33 * localT) * env;
            }
            else if (t >= tone1Sec + gapSec)
            {
                double localT = t - (tone1Sec + gapSec);
                double env = Math.Min(1.0, localT / 0.008) * Math.Exp(-localT * 16.0);
                sampleVal = Math.Sin(2.0 * Math.PI * 440.0 * localT) * env;
            }

            pcm[i] = (short)Math.Clamp(sampleVal * 13500.0, short.MinValue, short.MaxValue);
        }

        return EncodeWavePcm16(pcm, sampleRate);
    }

    private static byte[] EncodeWavePcm16(short[] samples, int sampleRate)
    {
        int dataBytes = samples.Length * sizeof(short);
        using var ms = new MemoryStream(44 + dataBytes);
        using var bw = new BinaryWriter(ms);

        bw.Write("RIFF"u8);
        bw.Write(36 + dataBytes);
        bw.Write("WAVE"u8);
        bw.Write("fmt "u8);
        bw.Write(16);
        bw.Write((short)1); // PCM
        bw.Write((short)1); // Mono
        bw.Write(sampleRate);
        bw.Write(sampleRate * 2);
        bw.Write((short)2);
        bw.Write((short)16);
        bw.Write("data"u8);
        bw.Write(dataBytes);

        foreach (short s in samples)
        {
            bw.Write(s);
        }

        bw.Flush();
        return ms.ToArray();
    }
}
