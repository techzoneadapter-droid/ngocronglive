using System;
using System.IO;
using System.Collections.Generic;
using System.Text.Json;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using HarmonyLib;
using Il2CppInterop.Runtime.InteropTypes.Arrays;
using UnityEngine;

[BepInPlugin("nero.local.noel.gameplay", "NERO Noel local", "1.0.5")]
public sealed class NoelLocal : BasePlugin
{
    public override void Load()
    {
        WeatherView.Root=Path.Combine(Path.GetDirectoryName(typeof(NoelLocal).Assembly.Location),"NoelAssets");
        new Harmony("nero.local.noel.gameplay").PatchAll(typeof(NoelLocal).Assembly);
        Log.LogInfo("NOEL_CLIENT_READY");
    }
}

internal static class NoelItemArt
{
    internal const short CostumeIcon = 31900, SleighIcon = 31901, CostumeAvatar = 31902, BeerusIcon = 31903;
    private static readonly Dictionary<int, Texture2D> images = new Dictionary<int, Texture2D>();

    internal static bool WearingCostume(G1.Char player)
    {
        return player != null && player.arrItemBody != null && player.arrItemBody.Length > 5
            && player.arrItemBody[5]?.template?.id == 2179;
    }

    internal static Texture2D Get(int id)
    {
        string name;
        switch (id)
        {
            case 16412: name = "fish"; break;
            case CostumeIcon: name = "costume-icon"; break;
            case SleighIcon: name = "sleigh-icon"; break;
            case CostumeAvatar: name = "costume-avatar-v2"; break;
            case 32064: name = "kaizen-avatar-v2"; break;
            default: return null;
        }
        int zoom = Math.Max(1, Math.Min(4, G1.mGraphics.zoomLevel));
        int key = id * 4 + zoom;
        if (!images.TryGetValue(key, out var image))
        {
            image = WeatherView.Load(name + "-" + zoom + ".png");
            images.Add(key, image);
            Debug.Log("NOEL_ITEM_ART " + name + " zoom=" + zoom);
        }
        return image;
    }
}

[HarmonyPatch(typeof(G1.Char), "avatarz")]
internal static class NoelEquippedAvatarArt
{
    static void Postfix(G1.Char __instance, ref int __result)
    {
        if (NoelItemArt.WearingCostume(__instance)) __result = NoelItemArt.CostumeAvatar;
    }
}

[HarmonyPatch(typeof(G1.ItemTemplates), "get", new Type[] { typeof(short) })]
internal static class NoelItemTemplateArt
{
    static void Postfix(short __0, ref G1.ItemTemplate __result)
    {
        if (__result == null && (__0 == 2179 || __0 == 2180))
        {
            bool costume = __0 == 2179;
            __result = new G1.ItemTemplate(__0, (sbyte)(costume ? 5 : 23), 3,
                costume ? "Cải trang Bố Già Noel" : "Xe tuần lộc Noel",
                "", 0, 0, 7067, (short)(costume ? 657 : 96), false);
            G1.ItemTemplates.add(__result);
        }
        if (__result == null) return;
        if (__0 == 2179 && __result.part == 657) __result.iconID = NoelItemArt.CostumeIcon;
        if ((__0 == 2180 && __result.part == 96) || (__0 == 746 && __result.type == 23)) __result.iconID = NoelItemArt.SleighIcon;
        if (__0 == 2026 && __result.iconID == 15033) __result.iconID = 4432;
        if (__0 == 2161 && __result.iconID == 32737) __result.iconID = NoelItemArt.BeerusIcon;
    }
}

[HarmonyPatch(typeof(G1.Char), "getAvatar", new Type[] { typeof(int) })]
internal static class NoelAvatarArt
{
    static void Postfix(int __0, ref int __result)
    {
        if (__0 == 657) __result = NoelItemArt.CostumeAvatar;
    }
}

[HarmonyPatch(typeof(G1.SmallImage), "drawSmallImage", new Type[] {
    typeof(G1.mGraphics), typeof(int), typeof(int), typeof(int), typeof(int), typeof(int)
})]
internal static class NoelDrawItemArt
{
    private static readonly Dictionary<int,float> pending=new Dictionary<int,float>();
    private static Texture2D beerus;
    static bool Prefix(G1.mGraphics __0, int __1, int __2, int __3, int __4, int __5)
    {
        if (__1 == NoelItemArt.BeerusIcon) {
            if (beerus == null) beerus = WeatherView.Load("beerus-front.png");
            const float w = 19, h = 19;
            float headX = __2 - ((__5 & 1) != 0 ? w/2 : (__5 & 8) != 0 ? w : 0);
            float headY = __3 - ((__5 & 2) != 0 ? h/2 : (__5 & 32) != 0 ? h : 0);

            WeatherView.Draw(beerus,headX,headY,w,h,__0);
            return false;
        }
        var image = NoelItemArt.Get(__1);
        if (image == null)
        {
            var icons=G1.SmallImage.imgNew;
            if (icons == null || __1 < 0 || __1 >= icons.Length) return false;
            {
                var icon=icons[__1];
                if(icon!=null && (icon.img==null || icon.img.texture==null)) {
                    if(!pending.TryGetValue(__1,out float retry) || Time.realtimeSinceStartup>=retry) {
                        pending[__1]=Time.realtimeSinceStartup+5;
                        G1.Service.gI().requestIcon(__1);
                        Debug.LogWarning("NOEL_MISSING_NATIVE_ICON "+__1);
                    }
                    return false;
                }
            }
            return true;
        }
        float width=image.width/(float)Math.Max(1,G1.mGraphics.zoomLevel);
        float height=image.height/(float)Math.Max(1,G1.mGraphics.zoomLevel);
        if(__1==NoelItemArt.CostumeAvatar){width=44;height=44;}
        if(__1==32064){width=40;height=44;}
        float x=__2,y=__3;
        if((__5&1)!=0) x-=width/2;
        if((__5&2)!=0) y-=height/2;
        if((__5&8)!=0) x-=width;
        if((__5&32)!=0) y-=height;
        WeatherView.Draw(image,x,y,width,height,__0,__4==2);
        return false;
    }
}

public static class WeatherView {
    internal static readonly BepInEx.Logging.ManualLogSource AudioLog=BepInEx.Logging.Logger.CreateLogSource("Noel audio");
    private static int pendingBell, lastBell;
    private static float nextBellAllowed;
    private static bool bellFailed;
    private static AudioClip bossBell;
    private static AudioSource bossSpeaker;

    private static void PlayBossBell()
    {
        if (bossBell==null) {
            byte[] data=File.ReadAllBytes(Path.Combine(Root,"boss-bell.wav"));
            int pcm=-1,length=0,rate=0;
            short channels=0,bits=0;
            for(int offset=12;offset+8<=data.Length;) {
                int size=BitConverter.ToInt32(data,offset+4);
                if(size<0 || size>data.Length-offset-8) throw new IOException("Invalid bell WAV chunk");
                string kind=System.Text.Encoding.ASCII.GetString(data,offset,4);
                if(kind=="fmt ") {
                    if(size<16 || BitConverter.ToInt16(data,offset+8)!=1) throw new IOException("Bell WAV must be PCM");
                    channels=BitConverter.ToInt16(data,offset+10);
                    rate=BitConverter.ToInt32(data,offset+12);
                    bits=BitConverter.ToInt16(data,offset+22);
                }
                if(kind=="data") {pcm=offset+8;length=size;}
                offset+=8+size+(size&1);
            }
            if(pcm<0 || channels!=1 || bits!=16 || rate<=0) throw new IOException("Unsupported bell WAV");
            var samples=new float[length/2];
            for(int i=0;i<samples.Length;i++) samples[i]=BitConverter.ToInt16(data,pcm+i*2)/32768f;
            bossBell=AudioClip.Create("Noel boss bells",samples.Length,1,rate,false);
            if(!bossBell.SetData((Il2CppStructArray<float>)samples,0)) throw new IOException("Bell audio load failed");
        }
        if(bossSpeaker==null) {
            var speaker=new GameObject("Noel boss sound");
            UnityEngine.Object.DontDestroyOnLoad(speaker);
            bossSpeaker=speaker.AddComponent<AudioSource>();
            bossSpeaker.spatialBlend=0;
            bossSpeaker.playOnAwake=false;
        }
        bossSpeaker.Stop();
        bossSpeaker.clip=bossBell;
        bossSpeaker.volume=Math.Max(0,Math.Min(1,G1.SoundMn.volume))*.65f;
        bossSpeaker.loop=false;
        bossSpeaker.Play();
        AudioLog.LogInfo("NOEL_BOSS_BELL_PLAYED samples="+bossBell.samples+" audioEnabled="+G1.GameCanvas.isPlaySound);
    }

    private static void UpdateBossBell()
    {
        if(bossSpeaker!=null && (!G1.GameCanvas.isPlaySound || !G1.SoundMn.isSound))
            bossSpeaker.Stop();
        if(pendingBell==0 || pendingBell==lastBell || bellFailed) return;
        lastBell=pendingBell;
        if(!G1.GameCanvas.isPlaySound || !G1.SoundMn.isSound ||
            Time.realtimeSinceStartup<nextBellAllowed) return;
        nextBellAllowed=Time.realtimeSinceStartup+10;
        try { PlayBossBell(); }
        catch(Exception error) {
            bellFailed=true;
            AudioLog.LogWarning("NOEL_BOSS_BELL_DISABLED "+error);
        }
    }
    public static string Root;
    public static bool Enabled,Snow,Storm;
    public static int Direction,Map=-1;
    private static float received,lastRequest;
    private static Texture2D wind;
    private static readonly System.Random rng=new System.Random(17);
    private sealed class Flake { public float x,y,speed; public int variant; }
    private static readonly List<Flake> particles=new List<Flake>();
    private static int particleMap=-1;
    public static bool Current { get { return Map==G1.TileMap.mapID && Time.realtimeSinceStartup-received<8; } }

    public static void Request(int action)
    {
        var msg=new G1.Message(-128);
        try
        {
            msg.writer().writeByte(100);
            msg.writer().writeInt(0x4e575831);
            msg.writer().writeByte(1);
            msg.writer().writeByte(action);
            G1.Session_ME.gI().sendMessage(msg);
        }
        catch(Exception error) { Debug.LogWarning("NOEL_REQUEST "+error.Message); }
        finally { msg.cleanup(); }
    }

    public static void Update()
    {
        UpdateBossBell();
        if(G1.Char.isLoadingMap || G1.GameCanvas.currentScreen!=G1.GameScr.gI()) return;
        MountRideControl.Update(G1.Char.myCharz());
        NoelRemoteMount.Report();
        float now=Time.realtimeSinceStartup;
        if(now-lastRequest>=3) { lastRequest=now; Request(0); }
    }

    public static bool Receive(G1.Message msg)
    {
        if(msg.command!=-128) return false;
        var data=msg.reader().buffer;
        if(data==null || data.Length<5 || data[0]!=100 || data[1]!=78 || data[2]!=87 || data[3]!=88 || data[4]!=49) return false;
        try
        {
            var reader=msg.reader();
            reader.readByte(); reader.readInt();
            int version=reader.readByte();
            if(version==3) {NoelRemoteMount.Read(reader);return true;}
            if(version==2) {pendingBell=reader.readInt();return true;}
            if(version!=1) return true;
            Map=reader.readShort();
            int flags=reader.readUnsignedByte();
            Direction=reader.readByte();
            reader.readInt();
            if((flags&8)!=0) { pendingBell=reader.readInt();  }
            Enabled=(flags&1)!=0; Snow=(flags&2)!=0; Storm=(flags&4)!=0;
            received=Time.realtimeSinceStartup;
        }
        catch(Exception error) { Enabled=Snow=Storm=false; Debug.LogWarning("NOEL_PACKET "+error.Message); }
        return true;
    }

    public static Texture2D Load(string file)
    {
        var image=new Texture2D(2,2,TextureFormat.RGBA32,false);
        image.hideFlags=HideFlags.HideAndDontSave;
        var bytes=(Il2CppInterop.Runtime.InteropTypes.Arrays.Il2CppStructArray<byte>)File.ReadAllBytes(Path.Combine(Root,file));
        if(!ImageConversion.LoadImage(image,bytes,true)) throw new IOException(file);
        image.filterMode=FilterMode.Bilinear;
        image.wrapMode=TextureWrapMode.Clamp;
        return image;
    }

    public static bool Draw(Texture2D image,float x,float y,float width,float height,G1.mGraphics g,bool flip=false)
        => Draw(image,x,y,width,height,g,flip,null);

    public static bool Draw(Texture2D image,float x,float y,float width,float height,G1.mGraphics g,bool flip,Rect? source)
    {
        if(image==null || Event.current==null || Event.current.type!=EventType.Repaint) return false;
        int zoom=Math.Max(1,G1.mGraphics.zoomLevel);
        var rect=new Rect((x+g.getTranslateX())*zoom,(y+g.getTranslateY())*zoom,width*zoom,height*zoom);
        if(rect.xMax<=0 || rect.yMax<=0 || rect.x>=G1.GameCanvas.w*zoom || rect.y>=G1.GameCanvas.h*zoom) return true;
        Color color=GUI.color;
        try
        {
            GUI.color=Color.white;
            Graphics.DrawTexture(rect,image,source ?? (flip?new Rect(1,0,-1,1):new Rect(0,0,1,1)),0,0,0,0);
        }
        finally { GUI.color=color; }
        return true;
    }

    public static void Paint(G1.mGraphics g)
    {
        if(!Current || !Snow || G1.Char.isLoadingMap || Event.current==null || Event.current.type!=EventType.Repaint) return;
        if(Storm && wind==null) wind=Load("wind.png");
        if(particleMap!=Map)
        {
            particleMap=Map; particles.Clear();
            for(int i=0;i<72;i++) particles.Add(new Flake {x=(float)rng.NextDouble()*G1.GameCanvas.w,
                y=-(float)rng.NextDouble()*G1.GameCanvas.h*.6f,speed=18+rng.Next(18),variant=rng.Next(240)});
        }
        int z=Math.Max(1,G1.mGraphics.zoomLevel);
        float dt=Math.Min(.05f,Time.unscaledDeltaTime);
        foreach(var flake in particles)
        {
            flake.y+=flake.speed*dt;
            flake.x+=(Storm?Direction*flake.speed*1.8f:2)*dt;
            if(flake.x<0) flake.x+=G1.GameCanvas.w;
            if(flake.x>G1.GameCanvas.w) flake.x-=G1.GameCanvas.w;
            int wx=(int)flake.x+G1.GameScr.cmx;
            int wy=(int)flake.y+G1.GameScr.cmy-G1.GameCanvas.transY;
            if(flake.y>=G1.GameCanvas.h || (G1.TileMap.tileTypeAtPixel(wx,wy)&2)!=0)
            {
                flake.y=-5; flake.x=(float)rng.NextDouble()*G1.GameCanvas.w;
                continue;
            }
            if((flake.x<95 && flake.y<45) || (flake.x>G1.GameCanvas.w-160 && flake.y<95)) continue;
            g.setColor(0xecf6ff);
            g.fillRect((int)flake.x,(int)flake.y,1,1);
            if(Storm && flake.variant%3==0)
            {
                int frame=((int)(Time.realtimeSinceStartup*30)+flake.variant)%240;
                int col=frame%12,row=frame/12;
                var uv=new Rect(col/12f,(20-row-1)/20f,1/12f,1/20f);
                if(Direction<0) { uv.x+=uv.width;uv.width=-uv.width; }
                float bottom=(flake.y+2)*z;
                Graphics.DrawTexture(new Rect((flake.x-29)*z,bottom-12*z,58*z,12*z),wind,uv,0,0,0,0);
            }
        }
    }
}


[HarmonyPatch(typeof(G1.Controller),"onMessage")]
public static class NoelWeatherPacket { static bool Prefix(G1.Message __0) { return !WeatherView.Receive(__0); } }
[HarmonyPatch(typeof(G1.GameScr),"update")]
public static class NoelWeatherUpdate { static void Postfix() { WeatherView.Update(); } }
[HarmonyPatch(typeof(G1.GameScr),"paint")]
public static class NoelWeatherPaint
{
    static void Postfix(G1.mGraphics __0)
    {
        WeatherView.Paint(__0);
        MountRideControl.PaintButton(__0);
    }
}

public static class MountRideControl
{
    private enum RidePhase { Seated, Parked, Approaching }
    private static RidePhase phase;
    private static bool manualPark;
    private static bool manualCall;
    private static float lastAttack = float.NegativeInfinity;
    private static int parkedMap = -1, parkedZone = -1, riderId, mountItem = -1;
    private static float parkedX, parkedY;
    private static int parkedDirection;
    private static bool buttonPressed;
    private static int lastToggleFrame=-1;
    private static int releasedFrame=-1;
    private const int ButtonSize = 30;
    public static int ButtonX => G1.Main.isPC ? G1.GameScr.xHP + 5 : mobileButtonX;
    public static int ButtonY => G1.Main.isPC ? G1.GameScr.yHP - 36 : mobileButtonY;
    private static int mobileButtonX=G1.GameCanvas.w-126, mobileButtonY=G1.GameCanvas.h-92;
    public static void SetMobileAnchor(int x,int y) { mobileButtonX=x-81;mobileButtonY=y+11; }
    private static Texture2D buttonIcon;

    public static int PhaseCode => (int)phase;
    public static bool IsSeated(G1.Char actor)
    {
        if (actor.me) return phase == RidePhase.Seated;
        return !NoelRemoteMount.TryGet(actor, out int remotePhase, out _, out _, out _) || remotePhase == 0;
    }

    public static void Update(G1.Char actor)
    {
        int equipped = NoelActors.Riding(actor) ? actor.arrItemBody[9].template.id : -1;
        if (equipped != mountItem || riderId != actor.charID)
        {
            mountItem = equipped;
            riderId = actor.charID;
            phase = RidePhase.Seated;
            manualPark = false;
            manualCall = false;
            lastAttack = float.NegativeInfinity;
            parkedMap = parkedZone = -1;
        }
        if (equipped < 0) return;

        float now = Time.realtimeSinceStartup;
        if (NoelActors.Fighting(actor))
        {
            manualCall=false;
            lastAttack = now;
            if (phase != RidePhase.Parked) Park(actor);
            StopNativeMount(actor);
        }
        if (UnityEngine.Input.GetKeyDown(KeyCode.Z) && InputAvailable())
            Toggle(actor);
        if (manualPark || (!manualCall && now - lastAttack < 10) || phase == RidePhase.Seated) return;

        if (!ParkedHere())
        {
            parkedMap = G1.TileMap.mapID;
            parkedZone = G1.TileMap.zoneID;
            parkedX = actor.cx - actor.cdir * Math.Max(120, G1.GameCanvas.w / 2);
            parkedY = actor.cy + actor.fy;
        }
        phase = RidePhase.Approaching;
        float travel = 275 * Math.Min(.05f, Time.unscaledDeltaTime);
        float distance = actor.cx - parkedX;
        if (Math.Abs(distance) > 1) parkedDirection = distance > 0 ? 1 : -1;
        parkedX = Mathf.MoveTowards(parkedX, actor.cx, travel);
        parkedY = Mathf.MoveTowards(parkedY, actor.cy + actor.fy, travel);
        if (Math.Abs(parkedX - actor.cx) <= 3 && Math.Abs(parkedY - actor.cy - actor.fy) <= 3)
        {
            phase = RidePhase.Seated;
            manualCall = false;
        }
    }

    private static bool InputAvailable() => !G1.GameCanvas.panel.isShow && G1.GameCanvas.currentDialog == null
        && (G1.ChatTextField.gI() == null || !G1.ChatTextField.gI().isShow);

    private static void Park(G1.Char actor)
    {
        phase = RidePhase.Parked;
        parkedMap = G1.TileMap.mapID;
        parkedZone = G1.TileMap.zoneID;
        parkedX = actor.cx - actor.cdir * 76;
        parkedY = actor.cy + actor.fy;
        parkedDirection = actor.cdir;
    }

    private static bool ParkedHere() => parkedMap == G1.TileMap.mapID && parkedZone == G1.TileMap.zoneID;
    public static bool VehicleVisible(G1.Char actor)
    {
        if (actor.isTeleport || actor.isUsePlane) return false;
        if (!actor.me && NoelRemoteMount.TryGet(actor, out int remotePhase, out _, out _, out _))
            return remotePhase != 3;
        return IsSeated(actor) || actor.me && ParkedHere();
    }

    private static void Toggle(G1.Char actor)
    {
        if(lastToggleFrame==Time.frameCount) return;
        lastToggleFrame=Time.frameCount;
        if (phase == RidePhase.Seated)
        {
            manualPark = true;
            manualCall = false;
            Park(actor);
            StopNativeMount(actor);
        }
        else
        {
            manualPark = false;
            manualCall = true;
            if (ParkedHere())
                phase = RidePhase.Approaching;
        }
    }

    public static void BeforeCapsule()
    {
        var actor=G1.Char.myCharz();
        if(actor==null || !NoelActors.Riding(actor)) return;
        manualPark=true;
        manualCall=false;
        Park(actor);
        StopNativeMount(actor);
        Debug.Log("NOEL_CAPSULE_DISMOUNT");
    }

    private static void StopNativeMount(G1.Char actor)
    {
        actor.isMount = false;
        actor.isStartMount = false;
        actor.isEndMount = false;
        actor.isHaveMount = false;
    }

    private static bool ButtonContains(int x, int y)
    {
        int dx = x - ButtonX - ButtonSize / 2;
        int dy = y - ButtonY - ButtonSize / 2;
        return dx * dx + dy * dy <= ButtonSize * ButtonSize / 4;
    }

    internal static bool Press(int x, int y)
    {
        buttonPressed = (G1.Main.isPC || G1.GameCanvas.isTouch) && mountItem >= 0 && InputAvailable() && ButtonContains(x, y);
        if (buttonPressed)
        {
            ClearPointer();
            var actor=G1.Char.myCharz();
            actor.currentMovePoint=null;
            actor.cvx=0;
        }
        return buttonPressed;
    }

    internal static bool Drag()
    {
        if(!buttonPressed && releasedFrame!=Time.frameCount) return false;
        ClearPointer();
        return true;
    }

    private static void ClearPointer()
    {
        G1.GameCanvas.isPointerDown=false;
        G1.GameCanvas.isPointerJustDown=false;
        G1.GameCanvas.isPointerJustRelease=false;
        G1.GameCanvas.isPointerClick=false;
        G1.GameCanvas.isPointerMove=false;
    }

    internal static bool Release(int x, int y)
    {
        if (!buttonPressed)
        {
            if(releasedFrame!=Time.frameCount) return false;
            ClearPointer();
            return true;
        }
        buttonPressed = false;
        releasedFrame=Time.frameCount;
        ClearPointer();
        if (mountItem >= 0 && InputAvailable() && ButtonContains(x, y)) Toggle(G1.Char.myCharz());
        return true;
    }

    public static void Position(G1.Char actor, out float x, out float y, out int direction, out bool moving)
    {
        if (!actor.me && NoelRemoteMount.TryGet(actor, out int remotePhase, out x, out y, out direction))
        {
            if (remotePhase == 0) { x=actor.cx; y=actor.cy+actor.fy; direction=actor.cdir; }
            moving=remotePhase==2 || remotePhase==0 && NoelActors.Moving(actor);
            return;
        }
        if (IsSeated(actor))
        {
            x = actor.cx; y = actor.cy + actor.fy; direction = actor.cdir;
            moving = NoelActors.Moving(actor) || NoelActors.airborne(actor);
            return;
        }
        x = parkedX; y = parkedY; direction = parkedDirection;
        moving = phase == RidePhase.Approaching;
    }

    public static void PaintButton(G1.mGraphics graphics)
    {
        if ((!G1.Main.isPC && !G1.GameCanvas.isTouch) || mountItem < 0 || !InputAvailable() || G1.GameScr.imgNut == null) return;
        int translateX = graphics.getTranslateX();
        int translateY = graphics.getTranslateY();
        graphics.translate(-translateX, -translateY);
        try
        {
            PaintButtonAtScreenPosition(graphics);
        }
        finally
        {
            graphics.translate(translateX, translateY);
        }
    }

    private static void PaintButtonAtScreenPosition(G1.mGraphics graphics)
    {
        int x = ButtonX + ButtonSize / 2;
        int y = ButtonY + ButtonSize / 2;
        graphics.drawImage(G1.GameScr.imgNut, x, y, 3);
        if(buttonIcon==null) buttonIcon=WeatherView.Load("vehicle-button.png");
        if(buttonIcon!=null) {WeatherView.Draw(buttonIcon,x-10,y-10,20,20,graphics,false);return;}
        graphics.setColor(buttonPressed ? 0xffdf87 : 0xfff3c2);
        graphics.fillRect(x - 5, y - 6, 10, 5);
        graphics.fillRect(x - 8, y - 2, 16, 6);
        graphics.fillRect(x - 6, y + 4, 3, 3);
        graphics.fillRect(x + 3, y + 4, 3, 3);
        graphics.setColor(0xd58222);
        graphics.fillRect(x - 3, y - 5, 6, 3);
    }
}

[HarmonyPatch(typeof(G1.ModFunc),"PaintButton")]
internal static class MountMobileButtonAnchor
{
    static void Prefix(int __1,int __2) => MountRideControl.SetMobileAnchor(__1,__2);
}

[HarmonyPatch(typeof(G1.GameCanvas), "pointerPressed", new Type[] { typeof(int), typeof(int) })]
internal static class MountButtonPress
{
    static bool Prefix(int __0, int __1) => !MountRideControl.Press(__0, __1);
}

[HarmonyPatch(typeof(G1.GameCanvas), "pointerReleased", new Type[] { typeof(int), typeof(int) })]
internal static class MountButtonRelease
{
    static bool Prefix(int __0, int __1) => !MountRideControl.Release(__0, __1);
}

[HarmonyPatch(typeof(G1.GameCanvas), "pointerDragged", new Type[] { typeof(int), typeof(int) })]
internal static class MountButtonDrag
{
    static bool Prefix() => !MountRideControl.Drag();
}

[HarmonyPatch(typeof(G1.Char), "checkHaveMount")]
internal static class SuppressNativeMountWhileParked
{
    static bool Prefix(G1.Char __instance, ref bool __result)
    {
        if (!__instance.me || MountRideControl.IsSeated(__instance) || !NoelActors.Riding(__instance)) return true;
        __result = false;
        return false;
    }
}

public static class NoelSlow
{
    public struct SpeedState { public int before,applied; public bool changed; }
    internal static SpeedState Apply(G1.Char player)
    {
        if(player==null || !player.me || !WeatherView.Current || !WeatherView.Storm ||
            NoelActors.Riding(player) || NoelItemArt.WearingCostume(player)) return default;
        int speed=player.cspeed;
        int movement=G1.GameCanvas.keyHold[23]==G1.GameCanvas.keyHold[24]?0:G1.GameCanvas.keyHold[24]?1:-1;
        int alignment=movement*Math.Sign(WeatherView.Direction);
        int scale=alignment<0?65:alignment>0?125:100;
        int reduced=Math.Max(1,(speed*scale+50)/100);
        player.cspeed=reduced;
        return new SpeedState {before=speed,applied=reduced,changed=true};
    }
    internal static void Restore(G1.Char player,SpeedState state)
    {
        if(state.changed && player.cspeed==state.applied) player.cspeed=state.before;
    }
}

[HarmonyPatch(typeof(G1.GameScr),"updateKey")]
public static class NoelSlowInput
{
    static void Prefix(out NoelSlow.SpeedState __state) { __state=NoelSlow.Apply(G1.Char.myCharz()); }
    static Exception Finalizer(NoelSlow.SpeedState __state,Exception __exception)
    {
        if(__state.changed) NoelSlow.Restore(G1.Char.myCharz(),__state);
        return __exception;
    }
}


[HarmonyPatch(typeof(G1.TileMap),"paintTilemap")]
public static class NoelGround
{
    private static Texture2D snow;

    static void Postfix(G1.mGraphics __0)
    {
        if(!WeatherView.Enabled || G1.Char.isLoadingMap ||
            Event.current==null || Event.current.type!=EventType.Repaint) return;
        if(snow==null) snow=WeatherView.Load("ground-snow.png");
        int size=G1.TileMap.size;
        int left=Math.Max(0,G1.GameScr.cmx/size);
        int right=Math.Min(G1.TileMap.tmw,(G1.GameScr.cmx+G1.GameCanvas.w)/size+2);
        int top=Math.Max(1,G1.GameScr.cmy/size);
        int bottom=Math.Min(G1.TileMap.tmh,(G1.GameScr.cmy+G1.GameCanvas.h)/size+2);
        for(int y=top;y<bottom;y++)
            for(int x=left;x<right;x++)
            {
                int flags=G1.TileMap.tileTypeAt(x,y);
                if((flags&2)==0 || (flags&0x1a0)!=0 || G1.TileMap.maps[y*G1.TileMap.tmw+x]<=0 ||
                    (G1.TileMap.tileTypeAt(x,y-1)&2)!=0) continue;
                WeatherView.Draw(snow,x*size,y*size,size,7,__0,(x&1)!=0);
            }
    }
}

[HarmonyPatch(typeof(G1.BgItem),"paint")]
public static class NoelFoliage
{
    private static readonly Dictionary<int,Texture2D> cached=new Dictionary<int,Texture2D>();
    private static readonly HashSet<int> failed=new HashSet<int>();
    private static int usedPixels;
    private static int cachedMap=-1;
    private static int generatedFrame=-1;
    static void Postfix(G1.BgItem __instance,G1.mGraphics __0)
    {
        if(!WeatherView.Enabled || G1.Char.isLoadingMap || __instance.idImage==96 ||
            (__instance.layer!=2 && __instance.layer!=3) ||
            Event.current==null || Event.current.type!=EventType.Repaint) return;
        if(cachedMap!=G1.TileMap.mapID) {
            foreach(var texture in cached.Values) UnityEngine.Object.Destroy(texture);
            cached.Clear();failed.Clear();usedPixels=0;
            cachedMap=G1.TileMap.mapID;generatedFrame=-1;
        }
        var original=G1.BgItem.imgNew.get(__instance.idImage.ToString());
        var image=original==null?null:original.TryCast<G1.Image>();
        if(image==null || image.texture==null) return;
        var source=image.texture;
        float w=image.getWidth(),h=image.getHeight();
        float left=__instance.x+__instance.dx+__instance.transX,top=__instance.y+__instance.dy+__instance.transY;
        if(left+w<G1.GameScr.cmx || left>G1.GameScr.cmx+G1.GameCanvas.w ||
            top+h<G1.GameScr.cmy || top>G1.GameScr.cmy+G1.GameCanvas.h) return;
        int id=source.GetInstanceID();
        if(failed.Contains(id)) return;
        Texture2D snow;
        if(!cached.TryGetValue(id,out snow))
        {
            if(generatedFrame==Time.frameCount) return;
            generatedFrame=Time.frameCount;
            if(source.width*source.height>1024*1024 || usedPixels+source.width*source.height>2097152) {failed.Add(id);return;}
            try
            {
                int width=source.width,height=source.height;
                var pixels=ReadPixels(source);
                var overlay=new Color32[pixels.Length];
                bool hasSnow=false;
                for(int y=0;y<height;y++)
                    for(int x=0;x<width;x++)
                    {
                        int index=y*width+x;
                        var leaf=pixels[index];
                        if(!IsLeaf(leaf)) continue;
                        int depth=1;
                        int thickness=Math.Max(3,G1.mGraphics.zoomLevel*3)+(x/7+x/17)%5;
                        while(depth<=thickness && y+depth<height && IsLeaf(pixels[index+width*depth])) depth++;
                        if(depth>thickness) continue;
                        int alpha=245-125*(depth-1)/thickness;
                        overlay[index]=new Color32((byte)(237-depth),(byte)(249-depth/2),255,(byte)(alpha*leaf.a/255));
                        hasSnow=true;
                    }
                if(!hasSnow) { failed.Add(id); return; }
                snow=new Texture2D(source.width,source.height,TextureFormat.RGBA32,false);
                snow.hideFlags=HideFlags.HideAndDontSave;
                snow.SetPixels32((Il2CppInterop.Runtime.InteropTypes.Arrays.Il2CppStructArray<Color32>)overlay);
                snow.Apply(false,true);
                cached[id]=snow;usedPixels+=source.width*source.height;
            }
            catch {failed.Add(id);return;}
        }
        WeatherView.Draw(snow,left,top,w,h,__0,__instance.trans==2);
    }

    private static bool IsLeaf(Color32 color)
    {
        return color.a>140 && color.g>color.r+15 && color.g>color.b+15;
    }

    private static Color32[] ReadPixels(Texture2D source)
    {
        if(source.isReadable) return source.GetPixels32();
        var active=RenderTexture.active;
        var target=RenderTexture.GetTemporary(source.width,source.height,0);
        Texture2D readable=null;
        try
        {
            Graphics.Blit(source,target);
            RenderTexture.active=target;
            readable=new Texture2D(source.width,source.height,TextureFormat.RGBA32,false);
            readable.ReadPixels(new Rect(0,0,source.width,source.height),0,0);
            return readable.GetPixels32();
        }
        finally
        {
            RenderTexture.active=active;
            RenderTexture.ReleaseTemporary(target);
            if(readable!=null) UnityEngine.Object.Destroy(readable);
        }
    }
}

public static class NoelActors
{
    // Small cached flame textures: no per-frame allocation or changes to actor sprites.
    private static Texture2D redAura;
    private static void PaintRedAura(G1.mGraphics graphics,float cx,float bottom,float width,float height)
    {
        if(redAura==null) redAura=WeatherView.Load("catalog-red-aura.png");
        int frame=G1.GameCanvas.gameTick/4%4;
        WeatherView.Draw(redAura,cx-width/2,bottom-height,width,height,graphics,false,new Rect(0,(3-frame)/4f,1,.25f));
    }
    private sealed class Motion { public int x,y; public float last; }
    private static readonly Dictionary<int,Motion> motion = new Dictionary<int,Motion>();
    private static readonly int[] runFrames = {6,7,8,9};
    private static readonly int[] vehicleFrames = {4,10,6,7,5};
    public static bool Fighting(G1.Char actor) { return actor.skillPaint!=null || actor.isCharge; }
    public static bool Moving(G1.Char actor)
    {
        if (!motion.TryGetValue(actor.charID,out var state))
        {
            state=new Motion {x=actor.cx,y=actor.cy};
            motion[actor.charID]=state;
        }
        if (state.x!=actor.cx || state.y!=actor.cy)
        {
            state.x=actor.cx;state.y=actor.cy;state.last=Time.realtimeSinceStartup;
        }
        return actor.statusMe==2 || actor.cvx!=0 || Time.realtimeSinceStartup-state.last<.18f;
    }
    private static Texture2D[] walking,combat,vehicle,mounted;
    private static JsonDocument mountMeta;
    private static readonly int[] mountedFrames={2,5,4,3};
    private static JsonDocument walkMeta,combatMeta;
    private static Texture2D[] smoke;
    private static JsonDocument smokeMeta;
    public static bool Body(G1.Char c) {return c!=null && (c.charID==-1007230 || (c.head==657 && c.charID!=-1007227) || NoelItemArt.WearingCostume(c));}
    public static bool Riding(G1.Char c)
    {
        if(c==null) return false;
        if(!c.me) return c.idMount==30096;
        if(c.arrItemBody==null || c.arrItemBody.Length<=9) return false;
        var mount=c.arrItemBody[9]?.template;
        return mount!=null && mount.type==23 && (mount.part==96 || mount.id==2180 || mount.id==746);
    }
    private static void Load()
    {
        if(walking!=null) return;
        walking=Read("noel-pose-ve-lai/pose-",16,2);
        combat=Read("noel-chien-dau/pose-",20,2);
        vehicle=Read("tuan-loc-pose/tuan-loc-",12,2);
        mounted=Read("mounted-",6,1);
        mountMeta=JsonDocument.Parse(File.ReadAllText(Path.Combine(WeatherView.Root,"mounted.json")));
        smoke=Read("noel-pose-ve-lai/smoke-",5,2,2);
        walkMeta=JsonDocument.Parse(File.ReadAllText(Path.Combine(WeatherView.Root,"noel-pose-ve-lai/manifest.json")));
        combatMeta=JsonDocument.Parse(File.ReadAllText(Path.Combine(WeatherView.Root,"noel-chien-dau/manifest.json")));
        smokeMeta=walkMeta;
    }
    private static Texture2D[] Read(string prefix,int count,int digits,int start=0)
    {
        var result=new Texture2D[count];
        for(int i=0;i<count;i++) result[i]=WeatherView.Load(prefix+(i+start).ToString("D"+digits)+".png");
        return result;
    }
    public static bool Paint(G1.Char c,G1.mGraphics g,int x,int y,int dir,int cf)
    {
        if(!Body(c)) return false;
        if(c.isTeleport || c.isUsePlane || c.isInvisiblez || c.isMafuba || c.cHP <= 0 || c.statusMe == 14) return false;
        Load();
        if(Event.current==null || Event.current.type!=EventType.Repaint) return false;
        int step=(int)(Time.realtimeSinceStartup*9);
        bool riding=c.charID==-1007230 || Riding(c) && MountRideControl.IsSeated(c) && !Fighting(c);
        Texture2D sprite;
        float emberX,emberY;
        float left,top,width,height,scale;
        if(riding)
        {
            int frame=Moving(c) || airborne(c)?mountedFrames[step%mountedFrames.Length]:0;
            sprite=mounted[frame];
            scale=112f/sprite.width;
            left=c.cx-(dir==1?47:65);
            top=c.cy+c.fy-sprite.height*scale;
            var tip=mountMeta.RootElement[frame].GetProperty("ember");
            emberX=tip[0].GetSingle();emberY=tip[1].GetSingle();
        }
        else
        {
            bool fighting=Fighting(c) || (cf>=13 && cf<=24);
            int frame;
            JsonElement record;
            if(fighting)
            {
                int skill=cf>=13 && cf<=24 ? cf-13 : step%4;
                bool projectile=c.myskill!=null && (c.myskill.template.id==1 || c.myskill.template.id==3 || c.myskill.template.id==5);
                frame=c.isCharge ? 16+step%4 : projectile ? 8+step%4 :
                    c.statusMe==13 ? 12+step%2 : skill>=4 && skill<8 ? 4+step%4 : step%4;
                sprite=combat[frame];
                record=combatMeta.RootElement.GetProperty("poses")[frame];
            }
            else
            {
                if(c.statusMe==10 || c.isMount || (c.statusMe==3 && c.ySd-c.cy>48))
                {
                    frame=14;
                }
                else if(c.statusMe==3 || c.statusMe==4 || c.statusMe==9 || c.statusMe==13) frame=11;
                else if(Moving(c)) frame=runFrames[step%4];
                else frame=(step/6)%2;
                sprite=walking[frame];
                record=walkMeta.RootElement.GetProperty("poses")[frame];
            }
            scale=50f/292;
            left=x-sprite.width*scale/2;
            top=y-sprite.height*scale;
            if(frame==14 && !fighting)
                top-=6f+1.2f*Mathf.Sin(Time.realtimeSinceStartup*8f);
            var tip=record.GetProperty("ember");
            emberX=tip[0].GetSingle();emberY=tip[1].GetSingle();
        }
        width=sprite.width*scale;height=sprite.height*scale;
        bool flip=dir!=1;
        if(!WeatherView.Draw(sprite,left,top,width,height,g,flip)) return false;
        if(riding)
            PaintRedAura(g,left+width*(flip?.59f:.41f),top+height*.94f,100,112);
        else
            PaintRedAura(g,left+width*.5f,top+height+8,Mathf.Max(96,width+42),Mathf.Min(116,height+58));
        int smokeFrame=(int)(Time.realtimeSinceStartup*6)%8;
        if(smokeFrame>0 && smokeFrame<=smoke.Length)
        {
            var puff=smoke[smokeFrame-1];
            var anchor=smokeMeta.RootElement.GetProperty("smoke_anchors")[smokeFrame-1];
            float ex=emberX*scale,ey=emberY*scale;
            float sx=left+(flip?width-ex:ex),sy=top+ey;
            float puffScale=.38f;
            WeatherView.Draw(puff,sx-(flip?puff.width-anchor[0].GetSingle():anchor[0].GetSingle())*puffScale,
                sy-anchor[1].GetSingle()*puffScale,puff.width*puffScale,puff.height*puffScale,g,flip);
        }
        return true;
    }

    public static bool airborne(G1.Char c) { return c.isMount || c.isStartMount && c.ySd-c.cy>24; }

    public static void Mount(G1.Char c,G1.mGraphics g)
    {
        if(!MountRideControl.VehicleVisible(c)) return;
        Load();
        MountRideControl.Position(c,out float x,out float y,out int direction,out bool moving);
        if(!c.me && Fighting(c)) {x-=c.cdir*75;moving=false;}
        int frame=moving?vehicleFrames[(int)(Time.realtimeSinceStartup*11)%5]:0;
        var sprite=vehicle[frame];
        float w=105,h=w*sprite.height/sprite.width;
        bool flip=direction!=1;
        WeatherView.Draw(sprite,x-(flip?65:40),y-h,w,h,g,flip);
    }
}

[HarmonyPatch(typeof(G1.Char),"paintCharWithoutSkill")]
public static class NoelBossBody
{
    static bool Prefix(G1.Char __instance,G1.mGraphics __0)
    {
        if(!NoelActors.Body(__instance) || __instance.isInvisiblez || __instance.isMafuba) return true;
        if(__instance.isTeleport || __instance.isUsePlane || __instance.cHP <= 0 || __instance.statusMe == 14) return true;
        if(__instance.charID!=-1007230 && NoelActors.Riding(__instance)) return false;
        return !NoelActors.Paint(__instance,__0,__instance.cx,__instance.cy+__instance.fy,__instance.cdir,__instance.cf);
    }
}

[HarmonyPatch(typeof(G1.Char),"paintCharBody",new Type[]{
    typeof(G1.mGraphics),typeof(int),typeof(int),typeof(int),typeof(int),typeof(bool)})]
public static class NoelActorBody
{
    [HarmonyPriority(Priority.First)]
    static bool Prefix(G1.Char __instance,G1.mGraphics __0,int __1,ref int __2,int __3,int __4)
    {
        if(__instance.charID==-1007230 || __4==25 || __instance.isInvisiblez || __instance.isMafuba) return true;
        if(__instance.isTeleport || __instance.isUsePlane || __instance.cHP <= 0 || __instance.statusMe == 14) return true;
        if(NoelActors.Body(__instance) && NoelActors.Riding(__instance)) return false;
        if(!NoelActors.Body(__instance) && NoelActors.Riding(__instance) && MountRideControl.IsSeated(__instance))
        {
            __2-=NoelActors.airborne(__instance)?12:24;
            return true;
        }
        return !NoelActors.Paint(__instance,__0,__1,__2,__3,__4);
    }
}
[HarmonyPatch(typeof(G1.Char),"paintMount1")]
public static class NoelVehicleBack
{
    private static int loggedMap=-1;
    static bool Prefix(G1.Char __instance,G1.mGraphics __0)
    {
        if(__instance.charID==-1007230) return false;
        if(!NoelActors.Riding(__instance)) return true;
        if(__instance.me && loggedMap!=G1.TileMap.mapID) {
            loggedMap=G1.TileMap.mapID;
            BepInEx.Logging.Logger.CreateLogSource("Noel rider").LogInfo("RIDER_RENDER map="+loggedMap+
                " body="+NoelActors.Body(__instance)+" parts="+__instance.head+":"+__instance.body+":"+__instance.leg+
                " xy="+__instance.cx+","+__instance.cy+" mountXY="+__instance.xMount+","+__instance.yMount);
        }
        if(!NoelActors.Body(__instance) || !MountRideControl.IsSeated(__instance) || NoelActors.Fighting(__instance))
            NoelActors.Mount(__instance,__0);
        if(NoelActors.Body(__instance) && !__instance.isInvisiblez && !__instance.isMafuba)
            NoelActors.Paint(__instance,__0,__instance.cx,__instance.cy+__instance.fy,__instance.cdir,__instance.cf);
        return false;
    }
}
[HarmonyPatch(typeof(G1.Char),"paintMount2")]
public static class NoelVehicleFront
{
    static bool Prefix(G1.Char __instance)
    {return __instance.charID!=-1007230 && !NoelActors.Riding(__instance);}
}


[HarmonyPatch(typeof(G1.Service),"requestMapSelect")]
internal static class NoelCapsuleDismount
{
    static void Prefix() => MountRideControl.BeforeCapsule();
}


[HarmonyPatch(typeof(G1.Controller), "onMessage")]
internal static class NoelInventoryAudit
{
    private static readonly BepInEx.Logging.ManualLogSource Log =
        BepInEx.Logging.Logger.CreateLogSource("NERO Inventory Audit");

    static void Prefix(G1.Message __0, out bool __state)
    {
        __state = false;
        if (__0 == null || __0.command != -36) return;
        var bytes = __0.reader().buffer;
        __state = bytes != null && bytes.Length > 1 && bytes[0] == 0;
        if (__state) Log.LogInfo("BAG_PACKET slots=" + (byte)bytes[1] + " bytes=" + bytes.Length);
    }

    static void Postfix(bool __state)
    {
        if (!__state) return;
        var bag = G1.Char.myCharz()?.arrItemBag;
        if (bag == null)
        {
            Log.LogWarning("BAG_RESULT no array");
            return;
        }
        int items = 0;
        int missingTemplates = 0;
        for (int i = 0; i < bag.Length; i++)
        {
            var item = bag[i];
            if (item == null) continue;
            items++;
            var template = item.template;
            if (template == null)
            {
                missingTemplates++;
                Log.LogWarning("BAG_SLOT zeroBased=" + i + " missing template");
            }
            else if (template.id == 2179 || template.id == 2180)
            {
                Log.LogInfo("BAG_NOEL zeroBased=" + i + " id=" + template.id + " icon=" + template.iconID);
            }
        }
        Log.LogInfo("BAG_RESULT slots=" + bag.Length + " entries=" + items + " missingTemplates=" + missingTemplates);
    }
}


internal static class NoelRemoteMount
{
    private sealed class Frame
    {
        internal int Phase, X, Y, Direction;
        internal float Time, FromX, FromY;
    }
    private static readonly Dictionary<int, Frame> states = new Dictionary<int, Frame>();
    private static int map = -1, zone = -1;
    private static float nextReport;

    internal static void Read(G1.myReader input)
    {
        int incomingMap = input.readShort(), incomingZone = input.readUnsignedByte();
        int count = input.readUnsignedShort();
        if (count > 512) throw new IOException("Too many mount states");
        var updated = new Dictionary<int, Frame>();
        for (int index = 0; index < count; index++)
        {
            int id = input.readInt(), phase = input.readUnsignedByte();
            int x = input.readInt(), y = input.readInt(), direction = input.readByte();
            if (phase > 3 || (direction != -1 && direction != 1))
                throw new IOException("Invalid mount state");
            float fromX=x, fromY=y;
            if (phase==2 && states.TryGetValue(id, out var previous) && previous.Phase==2)
            {
                float blend=Mathf.Clamp01((Time.realtimeSinceStartup-previous.Time)/.25f);
                fromX=Mathf.Lerp(previous.FromX,previous.X,blend);
                fromY=Mathf.Lerp(previous.FromY,previous.Y,blend);
            }
            updated[id] = new Frame {Phase=phase, X=x, Y=y, Direction=direction,
                FromX=fromX, FromY=fromY, Time=Time.realtimeSinceStartup};
        }
        states.Clear();
        map=incomingMap;
        zone=incomingZone;
        foreach (var entry in updated) states[entry.Key]=entry.Value;
    }

    internal static bool TryGet(G1.Char actor, out int phase, out float x, out float y, out int direction)
    {
        phase=0; x=actor.cx; y=actor.cy+actor.fy; direction=actor.cdir;
        if (actor.me || map!=G1.TileMap.mapID || zone!=G1.TileMap.zoneID ||
            !states.TryGetValue(actor.charID, out var frame) ||
            Time.realtimeSinceStartup-frame.Time>4) return false;
        phase=frame.Phase; x=frame.X; y=frame.Y; direction=frame.Direction;
        if (phase==2)
        {
            float blend=Mathf.Clamp01((Time.realtimeSinceStartup-frame.Time)/.25f);
            x=Mathf.Lerp(frame.FromX,frame.X,blend);
            y=Mathf.Lerp(frame.FromY,frame.Y,blend);
        }
        return true;
    }

    internal static void Report()
    {
        var actor=G1.Char.myCharz();
        if (actor==null || !NoelActors.Riding(actor) || actor.isTeleport || actor.isUsePlane ||
            Time.realtimeSinceStartup<nextReport) return;
        nextReport=Time.realtimeSinceStartup+.25f;
        MountRideControl.Position(actor, out float x, out float y, out int direction, out bool moving);
        var packet=new G1.Message(-128);
        try
        {
            var output=packet.writer();
            output.writeByte(100); output.writeInt(0x4e575831); output.writeByte(1); output.writeByte(1);
            output.writeByte(MountRideControl.VehicleVisible(actor) ? MountRideControl.PhaseCode : 3);
            output.writeShort(G1.TileMap.mapID); output.writeByte(G1.TileMap.zoneID);
            output.writeInt((int)System.Math.Max(0,System.Math.Min(G1.TileMap.pxw-1,x)));
            output.writeInt((int)System.Math.Max(0,System.Math.Min(G1.TileMap.pxh-1,y)));
            output.writeByte(direction);
            G1.Session_ME.gI().sendMessage(packet);
        }
        finally { packet.cleanup(); }
    }
}

