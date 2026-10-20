using System.Collections.Generic;
using UnityEngine;

// F10 documents the existing gameplay controls. It never remaps keys.
public static class NroKeyboard
{
    private static readonly Dictionary<KeyCode, int> held = new Dictionary<KeyCode, int>();
    private static bool visible;
    private static Vector2 scroll;
    private static readonly string[,] help = {
        { "R", "Mở ô chat" },
        { "U", "Bật / tắt tự động đánh" },
        { "N", "Bật / tắt tự động nhặt đồ" },
        { "F", "Dùng bông tai Porata (cần có vật phẩm và đủ điều kiện hợp thể)" },
        { "Mũi tên", "Di chuyển / bay" },
        { "Enter", "Hành động / xác nhận theo màn hình hiện tại" },
        { "1–9, 0", "Chọn ô kỹ năng đã gán" },
        { "Z", "Mở hành trang" },
        { "E", "Mở danh sách bạn bè" },
        { "M", "Mở danh sách khu" },
        { "H", "Yêu cầu cập nhật khu" },
        { "X", "Mở menu chức năng: bản đồ, luyện tập, nhặt đồ, đệ tử..." },
        { "G", "Mời giao dịch với nhân vật đang chọn" },
        { "J / K / L", "Chuyển theo lối trái / giữa / phải bằng chức năng Xmap" },
        { "W / A / S / D", "Di chuyển nhanh của bản nguồn (khác phím mũi tên)" },
        { "F10 / Esc", "Đóng bảng hướng dẫn" }
    };
    public static int MapKey(KeyCode key, bool released)
    {
        int code;
        if (released && held.TryGetValue(key, out code)) { held.Remove(key); return code; }
        code = MyKeyMap.map(key);
        if (!released && code != 0) held[key] = code;
        return code;
    }
    public static void ClearPressed() { ReleaseHeld(); }
    private static void ReleaseHeld()
    {
        if (GameMidlet.gameCanvas != null)
            foreach (int code in held.Values) GameMidlet.gameCanvas.keyReleasedz(code);
        held.Clear();
        GameCanvas.clearKeyHold();
        GameCanvas.clearKeyPressed();
    }
    public static bool Draw()
    {
        var ev = Event.current;
        if (ev == null) return visible;
        if (ev.type == EventType.KeyDown && ev.keyCode == KeyCode.F10) {
            visible = !visible; ReleaseHeld(); ev.Use();
        }
        if (!visible) return false;
        if (ev.type == EventType.KeyDown && ev.keyCode == KeyCode.Escape) {
            visible = false; ReleaseHeld(); ev.Use(); return true;
        }
        float width = Mathf.Min(680, Screen.width - 12);
        float height = Mathf.Min(560, Screen.height - 12);
        GUI.Window(9071, new Rect((Screen.width-width)/2, (Screen.height-height)/2, width, height),
            Window, "NgocRongLive — Hướng dẫn thao tác (F10)");
        return true;
    }
    private static void Window(int id)
    {
        GUILayout.Label("Phím thao tác có sẵn trong bản nguồn. Đóng ô chat trước khi sử dụng.");
        scroll = GUILayout.BeginScrollView(scroll, GUILayout.Height(Mathf.Max(40, Mathf.Min(430, Screen.height - 150))));
        for (int i = 0; i < help.GetLength(0); i++) {
            GUILayout.BeginHorizontal();
            GUILayout.Label(help[i, 0], GUILayout.Width(135));
            GUILayout.Label(help[i, 1]);
            GUILayout.EndHorizontal();
        }
        GUILayout.EndScrollView();
        if (GUILayout.Button("Đóng")) { visible = false; ReleaseHeld(); }
    }
}
