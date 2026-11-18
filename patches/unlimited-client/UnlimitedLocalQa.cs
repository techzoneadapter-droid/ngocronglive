using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using Il2CppInterop.Runtime.Injection;
using UnityEngine;
using HarmonyLib;

[BepInPlugin("nero.qa.unlimited.local", "Unlimited isolated local QA", "0.1.0")]
public sealed class UnlimitedLocalQa : BasePlugin {
 public override void Load() {
  if(!Environment.GetCommandLineArgs().Contains("-unlimitedLocalQa"))return;
  ClassInjector.RegisterTypeInIl2Cpp<UnlimitedLocalQaStep>();AddComponent<UnlimitedLocalQaStep>();
 }
}
public sealed class UnlimitedLocalQaStep : MonoBehaviour {
 public UnlimitedLocalQaStep(IntPtr ptr):base(ptr){}
 private float next=8;private bool sent,done;
 private int phase;
 private static readonly string Lab=Path.GetFullPath(Path.Combine(Path.GetDirectoryName(System.Diagnostics.Process.GetCurrentProcess().MainModule.FileName),".."));
 public void Update() {
  if(done || Time.realtimeSinceStartup<next)return;
  next=Time.realtimeSinceStartup+3;
  try {
   var screen=G1.GameCanvas.currentScreen;
   if(screen==null)return;
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
    if(phase==2) {var panel=G1.GameCanvas.panel;panel.cmy=panel.cmtoY=Math.Min(13*48,panel.cmyLim);ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-seven-equipped.png"));phase=3;return;}
    if(phase==3) {
     G1.GameCanvas.panel.hideNow();
     for(int i=0;i<G1.GameScr.vCharInMap.size();i++) {
      var target=G1.GameScr.vCharInMap.elementAt(i).TryCast<G1.Char>();
      if(target==null || target.charID!=-100023)continue;
      actor.charFocus=target;actor.mobFocus=null;
      actor.myskill=actor.vSkillFight.elementAt(0).TryCast<G1.Skill>();G1.Service.gI().selectSkill(actor.myskill.template.id);
      G1.GameScr.gI().auto=1;G1.GameScr.gI().doFire(false,true);
      File.AppendAllText(Path.Combine(Lab,"logs/client-qa.log"),"KUKU_ATTACK_SENT"+Environment.NewLine);phase=4;next=Time.realtimeSinceStartup+8;return;
     }
     ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-world-hud-kaizen.png"));done=true;return;
    }
    ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-boss-test.png"));G1.GameScr.gI().auto=0;done=true;return;
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
