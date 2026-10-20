param([string]$ClientDirectory,[string]$OutputDirectory)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class PreviewWindow {
 [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left,Top,Right,Bottom; }
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hwnd, out RECT rect);
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
 [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
 [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint x, uint y, uint data, UIntPtr extra);
}
'@
$ClientDirectory=(Resolve-Path $ClientDirectory).Path
New-Item $OutputDirectory -ItemType Directory -Force | Out-Null
$OutputDirectory=(Resolve-Path $OutputDirectory).Path
$log=Join-Path $OutputDirectory 'client.log'
$p=Start-Process (Join-Path $ClientDirectory 'NroYanii.exe') -WorkingDirectory $ClientDirectory -ArgumentList @('-screen-fullscreen','0','-screen-width','960','-screen-height','540','-logFile',('"'+$log+'"')) -PassThru
try {
 Start-Sleep -Seconds 20
 $p.Refresh()
 if ($p.HasExited) { throw "Client exited: $($p.ExitCode)" }
 if (!$p.MainWindowHandle) { throw 'Client has no window' }
 [PreviewWindow]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
 Start-Sleep -Seconds 2
 $rect=New-Object PreviewWindow+RECT
 [PreviewWindow]::GetWindowRect($p.MainWindowHandle,[ref]$rect) | Out-Null
 $width=$rect.Right-$rect.Left; $height=$rect.Bottom-$rect.Top
 # The first fresh-run screen offers one local server button at this location.
 [PreviewWindow]::SetCursorPos(($rect.Left+[int]($width*0.417)),($rect.Top+[int]($height*0.51))) | Out-Null
 [PreviewWindow]::mouse_event(2,0,0,0,[UIntPtr]::Zero)
 [PreviewWindow]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
 Start-Sleep -Seconds 20
 $bmp=New-Object Drawing.Bitmap $width,$height
 $g=[Drawing.Graphics]::FromImage($bmp)
 try { $g.CopyFromScreen($rect.Left,$rect.Top,0,0,$bmp.Size) } finally { $g.Dispose() }
 $small=New-Object Drawing.Bitmap 640,400
 $g=[Drawing.Graphics]::FromImage($small)
 try { $g.DrawImage($bmp,0,0,640,400) } finally { $g.Dispose(); $bmp.Dispose() }
 $path=Join-Path $OutputDirectory 'client-preview.jpg'
 $small.Save($path,[Drawing.Imaging.ImageFormat]::Jpeg); $small.Dispose()
 Write-Host 'PREVIEW_BASE64_BEGIN'
 Write-Host ([Convert]::ToBase64String([IO.File]::ReadAllBytes($path)))
 Write-Host 'PREVIEW_BASE64_END'
 if (Test-Path $log) {
  $content=Get-Content $log -Raw
  Get-Content $log -Tail 80
  if ($content -match 'NullReferenceException|ArgumentNullException|Failed to load') { throw 'Client startup exception: see captured preview and client.log. Package is not playable.' }
 }
 Write-Host ('Client window: '+$p.MainWindowTitle)
} finally { if (!$p.HasExited) { $p.Kill(); $p.WaitForExit() } }
