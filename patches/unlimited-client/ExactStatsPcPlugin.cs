using System;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using HarmonyLib;
using UnityEngine;

[BepInPlugin("nero.progression.exact", "NERO Exact Stats", "0.1.0")]
public sealed class ExactStatsPcPlugin : BasePlugin
{
    public override void Load() { new Harmony("nero.progression.exact").PatchAll(typeof(ExactStatsPcPlugin).Assembly); }
}
internal static class ExactPc
{
    internal static readonly ExactStatsState State = new ExactStatsState();
    private static float nextPoll;
    private static int owner = int.MinValue;
    internal static bool Receive(G1.Message message)
    {
        if (message.command != -128) return false;
        var data = message.reader().buffer;
        if (data == null || data.Length < 6 || data[0] != 101 || data[1] != 78 || data[2] != 69 || data[3] != 88 || data[4] != 49) return false;
        try {
            var bytes = new byte[data.Length - 1];
            for (int index = 0; index < bytes.Length; index++) bytes[index] = unchecked((byte)data[index + 1]);
            State.Apply(bytes, G1.TileMap.mapID, G1.TileMap.zoneID);
        } catch (Exception error) { Debug.LogWarning("Exact stat packet rejected: " + error.GetType().Name); }
        return true;
    }
    internal static void Poll()
    {
        if (G1.Char.isLoadingMap || G1.GameCanvas.currentScreen != G1.GameScr.gI()) return;
        var actor = G1.Char.myCharz();
        if (actor == null) return;
        if (owner != actor.charID) { State.Clear(); owner = actor.charID; nextPoll = 0; }
        if (Time.realtimeSinceStartup < nextPoll) return;
        nextPoll = Time.realtimeSinceStartup + 1;
        var request = new G1.Message(-128);
        try {
            request.writer().writeByte(101); request.writer().writeInt(0x4e455831);
            request.writer().writeByte(1); request.writer().writeByte(0);
            G1.Session_ME.gI().sendMessage(request);
        } finally { request.cleanup(); }
    }
}
[HarmonyPatch(typeof(G1.Controller), "onMessage")]
internal static class ExactPcPacket { static bool Prefix(G1.Message __0) { return !ExactPc.Receive(__0); } }
[HarmonyPatch(typeof(G1.GameScr), "update")]
internal static class ExactPcPoll { static void Postfix() { ExactPc.Poll(); } }
[HarmonyPatch(typeof(G1.Panel), "paintItemBodyBagInfo", new Type[] { typeof(G1.mGraphics), typeof(int), typeof(int) })]
internal static class ExactPcInventoryHeader
{
    static bool Prepare() { return AccessTools.Method(typeof(G1.Panel),"paintItemBodyBagInfo",new Type[]{typeof(G1.mGraphics),typeof(int),typeof(int)})!=null; }
    internal static bool Prefix(G1.Panel __instance, G1.mGraphics __0, int __1, int __2)
    {
        var actor = G1.Char.myCharz();
        if (actor == null) return true;
        var state = ExactPc.State.Get(actor.charID, G1.TileMap.mapID, G1.TileMap.zoneID);
        if (state == null) return true;
        G1.mFont.tahoma_7_yellow.drawString(__0, "HP: " + ExactStatsState.Compact(state.Hp) + " / " + ExactStatsState.Compact(state.HpMax), __1, __2 + 2, 0);
        G1.mFont.tahoma_7_yellow.drawString(__0, "KI: " + ExactStatsState.Compact(state.Ki) + " / " + ExactStatsState.Compact(state.KiMax), __1, __2 + 14, 0);
        G1.mFont.tahoma_7_yellow.drawString(__0, "Sức đánh: " + ExactStatsState.Compact(state.Attack), __1, __2 + 26, 0);
        G1.mFont.tahoma_7_yellow.drawString(__0, "Giáp: " + ExactStatsState.Compact(state.Defense) + ", Chí mạng: " + actor.cCriticalFull + "%", __1, __2 + 38, 0);
        return false;
    }
}
[HarmonyPatch(typeof(G1.Panel), "paintItemBodyBagInfo", new Type[] { typeof(G1.mGraphics) })]
internal static class ExactPcInventoryTop
{
    static bool Prepare() { return AccessTools.Method(typeof(G1.Panel),"paintItemBodyBagInfo",new Type[]{typeof(G1.mGraphics)})!=null; }
    static bool Prefix(G1.Panel __instance, G1.mGraphics __0) { return ExactPcInventoryHeader.Prefix(__instance, __0, __instance.X + 60, 0); }
}
