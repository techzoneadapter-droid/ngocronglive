using System;
using System.Text.Json;

// Test-only JSON adapter, not a replacement for Unity runtime testing.
namespace UnityEngine {
    public static class JsonUtility {
        public static T FromJson<T>(string value) { return JsonSerializer.Deserialize<T>(value, new JsonSerializerOptions { IncludeFields = true }); }
    }
}
public static class Program
{
    private static void Check(bool condition, string message) { if (!condition) throw new Exception(message); }
    public static void Main()
    {
        var a = new NroBindings();
        Check(a.Map("LeftArrow", -3, true) == -3, "Default movement");
        Check(a.Bind("Left", "A"), "Rebind movement");
        Check(a.Map("A", 97, true) == -3, "New key controls movement");
        Check(a.Map("A", 97, false) == 97, "Chat and menus retain original input");
        Check(a.Map("LeftArrow", -3, true) == 0, "Old movement key released");
        Check(!a.Bind("Right", "A"), "Reject duplicates");
        Check(!a.Bind("Left", "F10") && !a.Bind("Left", "Escape"), "Reserve settings/cancel keys");
        var b = new NroBindings(); Check(b.Restore(a.Snapshot()), "Restore saved keys");
        Check(b.Map("A", 97, true) == -3, "Restored mapping");
        var invalid = a.Snapshot(); invalid[0] = invalid[1];
        Check(!b.Restore(invalid) && b.Get("Left") == "A", "Atomic corrupt config rejection");
        b.Reset(); Check(b.Map("LeftArrow", -3, true) == -3, "Reset defaults");
        const string json = "[{\"nameServer\":\"NgocRongLive\",\"address\":\"127.0.0.1\",\"port\":14445,\"language\":0,\"typeSv\":0}]";
        const string legacy = "NgocRongLive:127.0.0.1:14445:0,0,0";
        Check(NroServerList.Normalize(json) == legacy, "Yanii JSON bootstrap adapts to source client");
        Check(NroServerList.Normalize(legacy) == legacy, "Legacy list remains accepted");
        bool rejected = false; try { NroServerList.Normalize("[]"); } catch (ArgumentException) { rejected = true; }
        Check(rejected, "Reject empty JSON server list");
        Console.WriteLine("PASS: bindings, typing isolation, persistence, duplicate rejection, reset, JSON/legacy bootstrap");
    }
}
