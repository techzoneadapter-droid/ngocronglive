using System;
using System.IO;
using System.Linq;
using System.Text.Json;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using Il2CppInterop.Runtime.Injection;
using UnityEngine;

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
    ScreenCapture.CaptureScreenshot(Path.Combine(Lab,"logs/local-login.png"));done=true;return;
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
