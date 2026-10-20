using System;
using System.Collections.Generic;

// Pure configuration logic; Unity input and persistence are kept in NroKeyboard.
public sealed class NroBindings
{
    public static readonly string[] Names = { "Up", "Down", "Left", "Right", "Action", "Skill1", "Skill2", "Skill3", "Skill4", "Skill5", "Skill6", "Skill7", "Skill8", "Skill9" };
    public static readonly string[] Defaults = { "UpArrow", "DownArrow", "LeftArrow", "RightArrow", "Return", "Alpha1", "Alpha2", "Alpha3", "Alpha4", "Alpha5", "Alpha6", "Alpha7", "Alpha8", "Alpha9" };
    public static readonly int[] Codes = { -1, -2, -3, -4, -5, 49, 50, 51, 52, 53, 54, 55, 56, 57 };
    private readonly Dictionary<string, string> keys = new Dictionary<string, string>();

    public NroBindings() { Reset(); }
    public void Reset() { keys.Clear(); for (int i = 0; i < Names.Length; i++) keys[Names[i]] = Defaults[i]; }
    public string Get(string action) { return keys[action]; }
    public bool Bind(string action, string key)
    {
        if (!keys.ContainsKey(action) || string.IsNullOrEmpty(key) || key == "None" || key == "F10" || key == "Escape") return false;
        foreach (var item in keys) if (item.Key != action && item.Value == key) return false;
        keys[action] = key;
        return true;
    }
    public int Map(string key, int original, bool gameplay)
    {
        if (!gameplay) return original;
        for (int i = 0; i < Names.Length; i++) if (keys[Names[i]] == key) return Codes[i];
        // An action moved to another key must no longer fire from its old key.
        foreach (string previous in Defaults) if (previous == key) return 0;
        return original;
    }
    // Restore all-or-nothing so corrupt/duplicate saved bindings never half-apply.
    public bool Restore(string[] values)
    {
        if (values == null || values.Length != Names.Length) return false;
        var unique = new HashSet<string>();
        foreach (string value in values)
            if (string.IsNullOrEmpty(value) || value == "None" || value == "F10" || value == "Escape" || !unique.Add(value)) return false;
        for (int i = 0; i < Names.Length; i++) keys[Names[i]] = values[i];
        return true;
    }
    public string[] Snapshot() { var result = new string[Names.Length]; for (int i = 0; i < result.Length; i++) result[i] = keys[Names[i]]; return result; }
}
