using System;
using System.Collections.Generic;
using UnityEngine;

public static class NroKeyboard
{
    private const string Preference = "NgocRongLive.KeyBindings.v1";
    private static readonly NroBindings bindings = new NroBindings();
    private static readonly Dictionary<KeyCode, int> held = new Dictionary<KeyCode, int>();
    private static bool initialized, visible;
    private static int waiting = -1;
    private static string notice = "F10: mở/đóng. Esc: hủy chọn phím.";
    private static readonly string[] labels = { "Lên / bay", "Xuống", "Sang trái", "Sang phải", "Hành động / chọn", "Ô kỹ năng 1", "Ô kỹ năng 2", "Ô kỹ năng 3", "Ô kỹ năng 4", "Ô kỹ năng 5", "Ô kỹ năng 6", "Ô kỹ năng 7", "Ô kỹ năng 8", "Ô kỹ năng 9" };
    [Serializable] private class Saved { public string[] keys; }

    private static void Initialize()
    {
        if (initialized) return;
        initialized = true;
        try {
            var saved = JsonUtility.FromJson<Saved>(PlayerPrefs.GetString(Preference, "{}"));
            if (saved != null && saved.keys != null) {
                foreach (string key in saved.keys) { KeyCode parsed; if (!Enum.TryParse(key, out parsed) || !Enum.IsDefined(typeof(KeyCode), parsed)) return; }
                bindings.Restore(saved.keys);
            }
        } catch (Exception) { notice = "Cấu hình phím lỗi; dùng phím mặc định."; }
    }
    private static void Save()
    {
        PlayerPrefs.SetString(Preference, JsonUtility.ToJson(new Saved { keys = bindings.Snapshot() }));
        PlayerPrefs.Save();
    }
    public static int MapKey(KeyCode key, bool released)
    {
        Initialize();
        int result;
        if (released && held.TryGetValue(key, out result)) { held.Remove(key); return result; }
        bool gameplay = GameCanvas.currentScreen is GameScr && GameCanvas.currentDialog == null
            && (GameCanvas.panel == null || !GameCanvas.panel.isShow) && !ChatTextField.gI().isShow;
        result = bindings.Map(key.ToString(), MyKeyMap.map(key), gameplay);
        if (!released && result != 0) held[key] = result;
        return result;
    }
    public static void ClearPressed() { ReleaseHeld(); }
    private static void ReleaseHeld()
    {
        if (GameMidlet.gameCanvas != null) foreach (int code in held.Values) GameMidlet.gameCanvas.keyReleasedz(code);
        held.Clear();
        GameCanvas.clearKeyHold();
        GameCanvas.clearKeyPressed();
    }
    // Called before Main processes mouse/keyboard, so editing cannot move a character.
    public static bool Draw()
    {
        Initialize();
        var ev = Event.current;
        if (ev == null) return visible;
        if (ev.type == EventType.KeyDown && ev.keyCode == KeyCode.F10) {
            visible = !visible; waiting = -1; ReleaseHeld(); ev.Use();
        }
        if (!visible) return false;
        if (ev.type == EventType.KeyDown) {
            if (ev.keyCode == KeyCode.Escape) { if (waiting >= 0) waiting = -1; else visible = false; }
            else if (waiting >= 0) {
                if (bindings.Bind(NroBindings.Names[waiting], ev.keyCode.ToString())) { Save(); waiting = -1; notice = "Đã lưu phím. Áp dụng khi chơi, giữ nguyên nhập chat."; }
                else notice = "Phím trùng hoặc dành riêng. Chọn phím khác.";
            }
            ev.Use();
        }
        if (!visible) return true;
        float width = Mathf.Min(510, Screen.width - 12), height = Mathf.Min(590, Screen.height - 12);
        GUI.Window(9071, new Rect((Screen.width-width)/2, (Screen.height-height)/2, width, height), Window, "NgocRongLive — Phím tắt (F10)");
        return true;
    }
    private static Vector2 scroll;
    private static void Window(int id)
    {
        GUILayout.Label(notice);
        scroll = GUILayout.BeginScrollView(scroll, GUILayout.Height(Mathf.Min(420, Screen.height - 150)));
        for (int i = 0; i < NroBindings.Names.Length; i++) {
            GUILayout.BeginHorizontal(); GUILayout.Label(labels[i], GUILayout.Width(180));
            if (GUILayout.Button(waiting == i ? "Nhấn phím mới…" : bindings.Get(NroBindings.Names[i]))) waiting = i;
            GUILayout.EndHorizontal();
        }
        GUILayout.EndScrollView();
        if (GUILayout.Button("Khôi phục mặc định")) { bindings.Reset(); waiting = -1; Save(); }
        if (GUILayout.Button("Đóng")) { visible = false; waiting = -1; ReleaseHeld(); }
    }
}
