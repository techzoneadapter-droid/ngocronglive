using System;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using HarmonyLib;
using UnityEngine;
using System.IO;

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
        int critical=actor.cCriticalFull<0?unchecked((byte)actor.cCriticalFull):actor.cCriticalFull;
        G1.mFont.tahoma_7_yellow.drawString(__0, "Giáp: " + ExactStatsState.Compact(state.Defense) + ", Chí mạng: " + critical + "%", __1, __2 + 38, 0);
        return false;
    }
}
[HarmonyPatch(typeof(G1.Panel), "paintItemBodyBagInfo", new Type[] { typeof(G1.mGraphics) })]
internal static class ExactPcInventoryTop
{
    static bool Prepare() { return AccessTools.Method(typeof(G1.Panel),"paintItemBodyBagInfo",new Type[]{typeof(G1.mGraphics)})!=null; }
    static bool Prefix(G1.Panel __instance, G1.mGraphics __0) { return ExactPcInventoryHeader.Prefix(__instance, __0, __instance.X + 60, 0); }
}

[HarmonyPatch(typeof(G1.GameScr),"paint")]
internal static class ExactHudScope {
    internal static bool Active;
    static void Prefix(){Active=true;}
    static void Postfix(){Active=false;}
    static Exception Finalizer(Exception __exception){Active=false;return __exception;}
}
[HarmonyPatch]
internal static class ExactHudText {
    static System.Collections.Generic.IEnumerable<System.Reflection.MethodBase> TargetMethods() {
        foreach(var method in typeof(G1.mFont).GetMethods(System.Reflection.BindingFlags.Public|System.Reflection.BindingFlags.NonPublic|System.Reflection.BindingFlags.Instance))
            if((method.Name=="drawString" || method.Name=="drawStringBorder" || method.Name=="drawStringBd" || method.Name=="_drawString")
                && method.GetParameters().Length>=5)yield return method;
    }
    internal static void Prefix(ref string __1,int __2,int __3) {
        if(!ExactHudScope.Active || __3<0 || __3>35 || string.IsNullOrEmpty(__1))return;
        var actor=G1.Char.myCharz();if(actor==null)return;
        var state=ExactPc.State.Get(actor.charID,G1.TileMap.mapID,G1.TileMap.zoneID);if(state==null)return;
        // Restrict replacement to the native vital bar numeric labels; names/target labels stay native.
        string oldHp=G1.NinjaUtil.getMoneys(actor.cHP),oldKi=G1.NinjaUtil.getMoneys(actor.cMP);
        string digits=__1.Replace(".","").Replace(",","").Replace(" ","");
        bool hp=__3<17 && (__1==oldHp || digits==actor.cHP.ToString());
        bool ki=__3>=17 && (__1==oldKi || digits==actor.cMP.ToString());
        if(hp)__1=ExactStatsState.CompactBar(state.Hp);
        else if(ki)__1=ExactStatsState.CompactBar(state.Ki);
    }
}

[HarmonyPatch(typeof(G1.Npc),"paint")]
internal static class KaizenNpcArt {
    private static Texture2D atlas;
    static bool Prefix(G1.Npc __instance,G1.mGraphics __0) {
        if(__instance.template==null || __instance.template.name!="Thần Rèn Kaizen")return true;
        if(Event.current==null || Event.current.type!=EventType.Repaint)return false;
        if(atlas==null) {
            string file=Path.Combine(Path.GetDirectoryName(typeof(ExactStatsPcPlugin).Assembly.Location),"KaizenAssets","kaizen-atlas-v1.png");
            if(!File.Exists(file))return true;
            atlas=new Texture2D(2,2,TextureFormat.RGBA32,false);
            if(!ImageConversion.LoadImage(atlas,File.ReadAllBytes(file),true))return true;
            atlas.filterMode=FilterMode.Bilinear;atlas.wrapMode=TextureWrapMode.Clamp;
        }
        int zoom=Math.Max(1,G1.mGraphics.zoomLevel),frame=(int)(Time.realtimeSinceStartup*7)%4;
        float height=72,width=height*(atlas.width/4f)/atlas.height;
        float bob=Mathf.Sin(Time.realtimeSinceStartup*2.7f)*.8f;
        float x=__instance.cx-width/2,y=__instance.cy-height+bob;
        var rect=new Rect((x+__0.getTranslateX())*zoom,(y+__0.getTranslateY())*zoom,width*zoom,height*zoom);
        var color=GUI.color;
        try{GUI.color=Color.white;Graphics.DrawTexture(rect,atlas,new Rect(frame/4f,0,.25f,1),0,0,0,0);}
        finally{GUI.color=color;}
        return false;
    }
}
