using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Threading;

// Core Audio session meters: only QQMusic-owned sessions, never the game's mixed output.
public static class QQAudioMeter {
    [DllImport("ole32.dll")] private static extern int CoInitializeEx(IntPtr reserved, uint flags);
    [DllImport("ole32.dll")] private static extern void CoUninitialize();
    [DllImport("ole32.dll")] private static extern int CoCreateInstance(ref Guid clsid, IntPtr outer, uint context, ref Guid iid, out IntPtr result);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int Enumerate(IntPtr self, int flow, uint state, out IntPtr collection);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int Count(IntPtr self, out uint count);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int Item(IntPtr self, uint index, out IntPtr item);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int Activate(IntPtr self, ref Guid iid, uint context, IntPtr args, out IntPtr result);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int GetObject(IntPtr self, out IntPtr result);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int GetId(IntPtr self, out uint id);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)] private delegate int Peak(IntPtr self, out float peak);
    private static readonly Guid DeviceClass = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static readonly Guid DeviceEnumerator = new Guid("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static readonly Guid SessionManager = new Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F");
    private static readonly Guid SessionControl2 = new Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D");
    private static readonly Guid MeterInterface = new Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064");
    private static readonly List<IntPtr> Meters = new List<IntPtr>();
    private static volatile bool stopping;
    private static Thread worker;
    private static T Method<T>(IntPtr value, int slot) {
        IntPtr table = Marshal.ReadIntPtr(value);
        return (T)(object)Marshal.GetDelegateForFunctionPointer(Marshal.ReadIntPtr(table, slot * IntPtr.Size), typeof(T));
    }
    private static void Release(ref IntPtr value) {
        if (value != IntPtr.Zero) { Marshal.Release(value); value = IntPtr.Zero; }
    }
    private static void Clear() { foreach (IntPtr meter in Meters) Marshal.Release(meter); Meters.Clear(); }
    private static void Check(int result) { if (result < 0) Marshal.ThrowExceptionForHR(result); }
    private static void Refresh() {
        Clear();
        IntPtr enumerator = IntPtr.Zero, devices = IntPtr.Zero;
        try {
            Guid clsid = DeviceClass, iid = DeviceEnumerator;
            Check(CoCreateInstance(ref clsid, IntPtr.Zero, 23, ref iid, out enumerator));
            Check(Method<Enumerate>(enumerator, 3)(enumerator, 0, 1, out devices));
            uint count; Check(Method<Count>(devices, 3)(devices, out count));
            for (uint i = 0; i < count; i++) {
                IntPtr device = IntPtr.Zero, manager = IntPtr.Zero, sessions = IntPtr.Zero;
                try {
                    Check(Method<Item>(devices, 4)(devices, i, out device));
                    iid = SessionManager;
                    Check(Method<Activate>(device, 3)(device, ref iid, 23, IntPtr.Zero, out manager));
                    Check(Method<GetObject>(manager, 5)(manager, out sessions));
                    uint sessionCount; Check(Method<Count>(sessions, 3)(sessions, out sessionCount));
                    for (uint s = 0; s < sessionCount; s++) {
                        IntPtr control = IntPtr.Zero, control2 = IntPtr.Zero, meter = IntPtr.Zero;
                        try {
                            Check(Method<Item>(sessions, 4)(sessions, s, out control));
                            iid = SessionControl2; Check(Marshal.QueryInterface(control, ref iid, out control2));
                            uint pid; Check(Method<GetId>(control2, 14)(control2, out pid));
                            if (pid == 0) continue;
                            using (Process process = Process.GetProcessById((int)pid)) {
                                if (!process.ProcessName.StartsWith("QQMusic", StringComparison.OrdinalIgnoreCase)) continue;
                            }
                            iid = MeterInterface;
                            Check(Marshal.QueryInterface(control, ref iid, out meter));
                            Meters.Add(meter); meter = IntPtr.Zero;
                        } catch { }
                        finally { Release(ref meter); Release(ref control2); Release(ref control); }
                    }
                } catch { }
                finally { Release(ref sessions); Release(ref manager); Release(ref device); }
            }
        } finally { Release(ref devices); Release(ref enumerator); }
    }
    private static float Read() {
        float maximum = 0;
        foreach (IntPtr meter in Meters) {
            float value;
            if (Method<Peak>(meter, 3)(meter, out value) >= 0 && !float.IsNaN(value)) maximum = Math.Max(maximum, value);
        }
        return Math.Max(0, Math.Min(1, maximum));
    }
    public static void Start() {
        if (worker != null) return;
        worker = new Thread(() => {
            int initialized = CoInitializeEx(IntPtr.Zero, 0);
            DateTime refresh = DateTime.MinValue;
            try {
                while (!stopping) {
                    try {
                        if ((DateTime.UtcNow - refresh).TotalSeconds >= 2) {
                            Refresh(); refresh = DateTime.UtcNow;
                        }
                        float peak = Read();
                        Console.WriteLine("{\"status\":\"meter\",\"available\":" + (Meters.Count > 0 ? "true" : "false")
                            + ",\"peak\":" + peak.ToString("0.00000", CultureInfo.InvariantCulture) + "}");
                    } catch {
                        Clear(); refresh = DateTime.UtcNow;
                        Console.WriteLine("{\"status\":\"meter\",\"available\":false,\"peak\":0}");
                    }
                    Thread.Sleep(33);
                }
            } finally { Clear(); if (initialized >= 0) CoUninitialize(); }
        });
        worker.IsBackground = true; worker.Name = "QQMusic-audio-meter"; worker.Start();
    }
    public static void Stop() { stopping = true; if (worker != null) worker.Join(300); }
}
