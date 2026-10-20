param([string]$Package)
$ErrorActionPreference='Stop'
$Package=(Resolve-Path $Package).Path
$process=Start-Process powershell.exe -ArgumentList @('-NoLogo','-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+(Join-Path $Package 'launch.ps1')+'"'),'-ServerOnly') -PassThru -RedirectStandardOutput (Join-Path $Package 'logs/standalone.log') -RedirectStandardError (Join-Path $Package 'logs/standalone-error.log')
try {
    $ready=$false
    for ($i=0;$i -lt 90;$i++) {
        if ($process.HasExited) { throw 'Standalone server exited before becoming ready' }
        try { $null=Invoke-WebRequest 'http://127.0.0.1:14447/' -UseBasicParsing; $ready=$true; break } catch { Start-Sleep -Milliseconds 500 }
    }
    if (!$ready) { throw 'Standalone admin did not become ready' }
    $password=(Get-Content (Join-Path $Package 'local-data/admin-password.txt') -Raw).Trim()
    $login=Invoke-RestMethod 'http://127.0.0.1:14447/api/login' -Method Post -ContentType 'application/json' -Body (@{password=$password}|ConvertTo-Json) -SessionVariable owner
    $state=Invoke-RestMethod 'http://127.0.0.1:14447/api/state' -WebSession $owner
    if (@($state.actions|Where-Object {$_.status -eq 'APPLIED'}).Count -ne 4) { throw 'Queued rewards did not persist across server restart' }
    if ($state.exp_multiplier -ne 1) { throw 'Disabled XP schedule did not persist' }
    Invoke-RestMethod 'http://127.0.0.1:14447/api/server/stop' -Method Post -ContentType 'application/json' -Body '{}' -Headers @{'X-CSRF'=$login.csrf} -WebSession $owner | Out-Null
    if (!$process.WaitForExit(60000)) { throw 'Standalone server did not stop gracefully' }
    if ($process.ExitCode) { throw ('Standalone launcher failed: '+$process.ExitCode) }
    foreach ($port in @(13307,3105,14445,14446,14447)) {
        $tcp=New-Object Net.Sockets.TcpClient
        try { $tcp.Connect('127.0.0.1',$port); throw ('Port still running after stop: '+$port) } catch [Net.Sockets.SocketException] {} finally { $tcp.Dispose() }
    }
    Write-Host 'PASS: standalone server restart, persisted rewards/events, owner shutdown and database cleanup'
} finally { if (!$process.HasExited) { 'stop' | Set-Content (Join-Path $Package 'local-data/stop.request'); $process.WaitForExit(15000) | Out-Null } }
