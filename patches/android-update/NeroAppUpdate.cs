using System;
using System.IO;
using System.Collections;
using System.Security.Cryptography;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.Networking;

// APK updates are explicit and use the Android package installer, never a silent install.
public sealed class NeroAppUpdate : MonoBehaviour {
 const string Manifest="https://nronerovn.website/downloads/android-version.json";
 [Serializable] public sealed class Release {public string package,url,sha256,version;public int versionCode;public long bytes;}
 Release release;string message;bool downloading,prompt,installPending;float progress;
 string FilePath=>Path.Combine(Application.persistentDataPath,"NeroUpdate.apk");
 [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)] static void Init(){
#if UNITY_ANDROID && !UNITY_EDITOR && !DEVELOPMENT_BUILD
  var go=new GameObject("NERO app update");DontDestroyOnLoad(go);go.AddComponent<NeroAppUpdate>();
#endif
 }
 IEnumerator Start(){yield return new WaitForSecondsRealtime(2);using(var q=UnityWebRequest.Get(Manifest)){q.timeout=12;yield return q.SendWebRequest();if(q.result!=UnityWebRequest.Result.Success)yield break;
  Release r=null;try{r=JsonUtility.FromJson<Release>(q.downloadHandler.text);}catch{}if(!Valid(r)||r.versionCode<=InstalledCode())yield break;release=r;prompt=true;message="Đã có NERO "+r.version+". Tải bản mới?";
 }}
 public static bool Valid(Release r){Uri u;return r!=null&&r.package=="com.ngocrongnero.game"&&r.versionCode>0&&r.bytes>0&&r.bytes<700L*1024*1024&&r.sha256!=null&&System.Text.RegularExpressions.Regex.IsMatch(r.sha256,"\\A[a-f0-9]{64}\\z")&&Uri.TryCreate(r.url,UriKind.Absolute,out u)&&u.Scheme=="https"&&u.Host=="nronerovn.website"&&u.AbsolutePath.StartsWith("/downloads/")&&string.IsNullOrEmpty(u.UserInfo);}
 int InstalledCode(){using(var unity=new AndroidJavaClass("com.unity3d.player.UnityPlayer"))using(var a=unity.GetStatic<AndroidJavaObject>("currentActivity"))using(var pm=a.Call<AndroidJavaObject>("getPackageManager"))using(var info=pm.Call<AndroidJavaObject>("getPackageInfo",Application.identifier,0))return info.Get<int>("versionCode");}
 void OnGUI(){if(!prompt||GameCanvas.currentScreen is GameScr)return;float w=Mathf.Min(Screen.width-24,540),h=220;GUI.ModalWindow(8416,new Rect((Screen.width-w)/2,(Screen.height-h)/2,w,h),id=>{GUI.Label(new Rect(20,40,w-40,80),message+(downloading?"\n"+Mathf.RoundToInt(progress*100)+"%":""));if(downloading)return;if(GUI.Button(new Rect(20,140,(w-50)/2,48),installPending?"Cài bản mới":"Cập nhật")){if(installPending)Install();else StartCoroutine(Download());}if(GUI.Button(new Rect(30+(w-50)/2,140,(w-50)/2,48),"Để sau"))prompt=false;},"Ngọc Rồng NERO");}
 IEnumerator Download(){downloading=true;progress=0;message="Đang tải bản cập nhật "+release.version+"…";string temp=FilePath+".part";
  try {Directory.CreateDirectory(Application.persistentDataPath);if(File.Exists(temp))File.Delete(temp);}
  catch(Exception error){Debug.LogWarning("NERO_APK_STORAGE "+error.GetType().Name);message="Không ghi được file cập nhật. Kiểm tra dung lượng trống của máy.";downloading=false;yield break;}
  using(var q=UnityWebRequest.Get(release.url)){q.downloadHandler=new DownloadHandlerFile(temp);q.timeout=900;var request=q.SendWebRequest();while(!request.isDone){progress=Mathf.Clamp01((float)((double)DownloadedBytes(temp)/release.bytes));yield return null;}if(q.result!=UnityWebRequest.Result.Success){Debug.LogWarning("NERO_APK_DOWNLOAD status="+q.responseCode+" result="+q.result+" bytes="+DownloadedBytes(temp)+" error="+q.error);message="Tải cập nhật bị gián đoạn (HTTP "+q.responseCode+"). Kiểm tra mạng rồi thử lại hoặc tải từ website.";downloading=false;yield break;}}
  progress=1;message="Đang kiểm tra file cập nhật…";
  var verify=System.Threading.Tasks.Task.Run(()=>{using(var stream=File.OpenRead(temp))using(var hash=SHA256.Create())return stream.Length==release.bytes&&BitConverter.ToString(hash.ComputeHash(stream)).Replace("-","").ToLowerInvariant()==release.sha256;});while(!verify.IsCompleted)yield return null;
  if(verify.IsFaulted||!verify.Result){if(File.Exists(temp))File.Delete(temp);message="File cập nhật không hợp lệ. Chưa cài đặt.";downloading=false;yield break;}
  if(File.Exists(FilePath))File.Delete(FilePath);File.Move(temp,FilePath);downloading=false;installPending=true;Install();
 }
 static long DownloadedBytes(string path){try{return new FileInfo(path).Length;}catch(IOException){return 0;}}
 void Install(){try{using(var unity=new AndroidJavaClass("com.unity3d.player.UnityPlayer"))using(var a=unity.GetStatic<AndroidJavaObject>("currentActivity"))using(var helper=new AndroidJavaClass("com.ngocrongnero.update.NeroUpdateProvider")){helper.CallStatic("install",a);message="Xác nhận cài đặt trong cửa sổ Android. Nếu được hỏi, cho phép NERO cài bản cập nhật.";}}catch{message="Chưa mở được trình cài đặt. Bấm Cài bản mới để thử lại.";}}
}
