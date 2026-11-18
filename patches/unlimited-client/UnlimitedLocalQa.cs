using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using Il2CppInterop.Runtime.Injection;
using UnityEngine;
using HarmonyLib;

[BepInPlugin("nero.qa.unlimited.local", "Unlimited isolated local QA", "0.1.3")]
public sealed class UnlimitedLocalQa : BasePlugin {
 public override void Load() {
  if(!Environment.GetCommandLineArgs().Contains("-unlimitedLocalQa"))return;
  Application.runInBackground=true;
  ClassInjector.RegisterTypeInIl2Cpp<UnlimitedLocalQaStep>();AddComponent<UnlimitedLocalQaStep>();
 }
}
public sealed class UnlimitedLocalQaStep : MonoBehaviour {
 public UnlimitedLocalQaStep(IntPtr ptr):base(ptr){}
 private float next=8;private bool sent,done;
 private int phase;
 private int gearIndex;private short gearId;
 private static readonly string Lab=Path.GetFullPath(Path.Combine(Path.GetDirectoryName(System.Diagnostics.Process.GetCurrentProcess().MainModule.FileName),".."));
 public void Update() {
  Application.runInBackground=true;
  if(phase==4 && G1.GameCanvas.currentScreen!=null && G1.GameCanvas.currentScreen.GetIl2CppType().Name=="GameScr") {
   var moving=G1.Char.myCharz();G1.GameCanvas.keyHold[23]=moving.cx>128;G1.GameCanvas.keyHold[24]=moving.cx<80;
  }
  if(done || Time.realtimeSinceStartup<next)return;
  next=Time.realtimeSinceStartup+3;
  try {
   var screen=G1.GameCanvas.currentScreen;
   if(screen==null)return;
   File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"STEP phase="+phase+" screen="+screen.GetIl2CppType().Name+" loading="+G1.Char.isLoadingMap+Environment.NewLine);
   if(phase>0 && (screen.GetIl2CppType().Name!="GameScr" || G1.Char.isLoadingMap))
    File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"WAIT phase="+phase+" screen="+screen.GetIl2CppType().Name+" loading="+G1.Char.isLoadingMap+Environment.NewLine);
   if(screen.GetIl2CppType().Name=="GameScr" && !G1.Char.isLoadingMap) {
    var actor=G1.Char.myCharz();
    if(actor==null)return;
    File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"IN_GAME actor="+actor.charID+" map="+G1.TileMap.mapID+" bodySlots="+(actor.arrItemBody==null?0:actor.arrItemBody.Length)+Environment.NewLine);
    if(phase==0) {phase=1;next=Time.realtimeSinceStartup+5;return;}
    if(phase==1) {
     for(int i=13;i<20;i++)if(actor.arrItemBody.Length<=i || actor.arrItemBody[i]==null)throw new InvalidOperationException("Missing equipped slot "+i);
     var panel=G1.GameCanvas.panel;panel.setTypeMain();panel.currentTabIndex=1;
     AccessTools.Method(typeof(G1.Panel),"setTabInventory").Invoke(panel,new object[]{true});panel.show();phase=2;return;
    }
    if(phase==2) {var panel=G1.GameCanvas.panel;panel.cmy=panel.cmtoY=Math.Min(13*24,panel.cmyLim);ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-seven-equipped.png"));phase=3;return;}
    if(phase==3) {
     G1.GameCanvas.panel.hideNow();
     ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-world-hud-kaizen.png"));phase=4;return;
    }
    if(phase==4) {
     if(actor.cx<80 || actor.cx>128)return;
     G1.GameCanvas.keyHold[23]=G1.GameCanvas.keyHold[24]=false;
     File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"OPEN_MENU_BEGIN x="+actor.cx+Environment.NewLine);G1.Service.gI().openMenu(81);phase=5;File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"OPEN_MENU_SENT"+Environment.NewLine);return;
    }
    if(phase==5) {
     if(!G1.GameCanvas.menu.showMenu || G1.GameCanvas.menu.menuItems.size()!=5)throw new InvalidOperationException("Kaizen native root menu did not open with five entries");
     ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-kaizen-root-menu.png"));phase=6;return;
    }
    if(phase==6){G1.GameCanvas.menu.showMenu=false;G1.Service.gI().confirmMenu(81,2);phase=7;return;}
    if(phase==7) {
     if(!G1.GameCanvas.panel.isShow)throw new InvalidOperationException("Kaizen native combine panel did not open");
     ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-kaizen-upgrade-menu.png"));File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"KAIZEN_NATIVE_ROOT_AND_UPGRADE_PANEL_PASS"+Environment.NewLine);phase=8;return;
    }
    if(phase==8) {
     G1.GameCanvas.panel.hideNow();G1.GameCanvas.menu.showMenu=false;
     gearId=actor.arrItemBody[13+gearIndex].template.id;G1.Service.gI().getItem(5,(sbyte)(13+gearIndex));phase=9;return;
    }
    if(phase==9) {
     if(actor.arrItemBody[13+gearIndex]!=null)throw new InvalidOperationException("Unequip failed at slot "+(13+gearIndex));
     for(int i=0;i<actor.arrItemBag.Length;i++)if(actor.arrItemBag[i]?.template?.id==gearId) {
      File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"KAIZEN_CLIENT_UNEQUIP_PASS id="+gearId+" slot="+(13+gearIndex)+Environment.NewLine);
      G1.Service.gI().getItem(4,(sbyte)i);phase=10;return;
     }
     throw new InvalidOperationException("Unequipped gear missing from inventory "+gearId);
    }
    if(phase==10) {
     if(actor.arrItemBody[13+gearIndex]?.template?.id!=gearId)throw new InvalidOperationException("Re-equip failed "+gearId);
     File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"KAIZEN_CLIENT_REEQUIP_PASS id="+gearId+" slot="+(13+gearIndex)+Environment.NewLine);
     if(++gearIndex<7){phase=8;return;}
     File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"KAIZEN_ALL_SEVEN_NETWORK_EQUIP_PASS"+Environment.NewLine);done=true;return;
    }
   }
   if(sent || screen.GetIl2CppType().Name!="ServerListScreen")return;
   G1.GameMidlet.IP="127.0.0.1";G1.GameMidlet.PORT=14615;
   if(!G1.Session_ME.gI().isConnected()){G1.GameCanvas.connect();return;}
   using(var credentials=JsonDocument.Parse(File.ReadAllText(Path.Combine(Lab,"local-data/credentials.json")))) {
    G1.Rms.saveRMSString("acc","test");G1.Rms.saveRMSString("pass",credentials.RootElement.GetProperty("player").GetString());
   }
   G1.GameCanvas.serverScreen.perform(3,null);sent=true;
   File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"LOCAL_LOGIN_SENT"+Environment.NewLine);
  }catch(Exception error){File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"FAILED "+error+Environment.NewLine);done=true;}
 }
}
