using System;
using System.Collections;
using System.IO;
using System.Reflection;
using UnityEditor;
using UnityEngine;
using UnityEngine.Networking;

// Runs the same production coroutine with a workspace output, never installs an APK.
public static class NeroAndroidDownloadChecks {
 static IEnumerator routine;static GameObject host;static double started,lastReport;static bool buildAfter;
 public static void RunAndBuild(){buildAfter=true;Run();}
 public static void Run(){
  started=EditorApplication.timeSinceStartup;routine=Check();EditorApplication.update+=Tick;
 }
 static void Tick(){try{
  if(EditorApplication.timeSinceStartup-started>1000)throw new Exception("Download test deadline");
  if(routine.MoveNext())return;
  EditorApplication.update-=Tick;if(host!=null)UnityEngine.Object.DestroyImmediate(host);
  Debug.Log("PASS ANDROID_UNITY_DOWNLOAD_CHECKS");if(buildAfter)NeroMobileBuild.AndroidRelease();EditorApplication.Exit(0);
 }catch(Exception error){Debug.LogException(error);EditorApplication.update-=Tick;EditorApplication.Exit(1);}}
 static IEnumerator Check(){
  string root=Environment.GetEnvironmentVariable("NERO_ANDROID_QA_OUTPUT");
  if(string.IsNullOrEmpty(root)||!Path.IsPathRooted(root))throw new Exception("Workspace QA output required");
  Directory.CreateDirectory(root);NeroAppUpdate.Release release;
  using(var q=UnityWebRequest.Get("https://nronerovn.website/downloads/android-version.json")){
   q.timeout=30;var op=q.SendWebRequest();while(!op.isDone)yield return null;
   if(q.result!=UnityWebRequest.Result.Success)throw new Exception("Manifest: "+q.error);
   release=JsonUtility.FromJson<NeroAppUpdate.Release>(q.downloadHandler.text);
  }
  if(!NeroAppUpdate.Valid(release))throw new Exception("Manifest validation failed");
  host=new GameObject("Android download isolated check");var updater=host.AddComponent<NeroAppUpdate>();
  var method=typeof(NeroAppUpdate).GetMethod("DownloadRelease",BindingFlags.Instance|BindingFlags.NonPublic);
  var progress=typeof(NeroAppUpdate).GetField("progress",BindingFlags.Instance|BindingFlags.NonPublic);
  bool done=false,success=false;Action<bool> result=ok=>{done=true;success=ok;};
  var download=(IEnumerator)method.Invoke(updater,new object[]{release,Path.Combine(root,"verified-public.apk"),result});
  float maximum=0;
  while(download.MoveNext()){
   maximum=System.Math.Max(maximum,(float)progress.GetValue(updater));
   if(EditorApplication.timeSinceStartup-lastReport>=5){lastReport=EditorApplication.timeSinceStartup;Debug.Log("APK_QA_PROGRESS "+maximum);}
   yield return null;
  }
  if(!done||!success||maximum<=0)throw new Exception("Public APK download/hash/progress failed");
  Debug.Log("PASS APK download bytes="+release.bytes+" sha256="+release.sha256+" progress="+maximum);
  var verify=typeof(NeroAppUpdate).GetMethod("VerifyApkFile",BindingFlags.Static|BindingFlags.NonPublic);
  string tiny=Path.Combine(root,"truncated.apk");File.WriteAllBytes(tiny,new byte[]{80,75,3,4});
  if((bool)verify.Invoke(null,new object[]{tiny,release}))throw new Exception("Truncated APK accepted");
  string correctHash=release.sha256;release.sha256=new string('0',64);
  if((bool)verify.Invoke(null,new object[]{Path.Combine(root,"verified-public.apk"),release}))throw new Exception("Corrupt hash accepted");
  release.sha256=correctHash;Debug.Log("PASS truncated APK and SHA256 mismatch rejected");
  release.url="https://nronerovn.website/downloads/nero-qa-missing-apk-file.apk";
  done=false;success=true;download=(IEnumerator)method.Invoke(updater,new object[]{release,Path.Combine(root,"missing.apk"),result});
  while(download.MoveNext())yield return null;
  if(!done||success||File.Exists(Path.Combine(root,"missing.apk")))throw new Exception("HTTP error falsely accepted");
  release.url="https://example.com/downloads/other.apk";
  if(NeroAppUpdate.Valid(release))throw new Exception("Foreign origin accepted");
  Debug.Log("PASS HTTP failure and untrusted origin rejected");
 }
}
