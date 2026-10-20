param([switch]$SmokeTest, [switch]$ClientPreview)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = $PSScriptRoot
# Use Windows' short path where available: older native launchers may reject Unicode.
Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class NroNativePath {
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    public static extern uint GetShortPathName(string path, StringBuilder result, uint size);
}
'@
$shortRoot = New-Object Text.StringBuilder 32768
$shortLength = [NroNativePath]::GetShortPathName($root, $shortRoot, 32768)
if ($shortLength -gt 0 -and $shortLength -lt 32768) { $root = $shortRoot.ToString() }
Set-Location -LiteralPath $root
function Wait-Port([int]$Port, $Process, [string]$ErrorLog) {
    for ($i=0; $i -lt 60; $i++) {
        if ($Process.HasExited) {
            $detail = ''
            if ($ErrorLog -and (Test-Path $ErrorLog)) {
                $detail = (Get-Content -LiteralPath $ErrorLog -Encoding UTF8 -Tail 35 -ErrorAction SilentlyContinue) -join "`n"
            }
            if (!$detail -and $ErrorLog) {
                $standardLog = $ErrorLog.Replace('-error.log', '.log')
                if (Test-Path $standardLog) {
                    $detail = (Get-Content -LiteralPath $standardLog -Encoding UTF8 -Tail 35 -ErrorAction SilentlyContinue) -join "`n"
                }
            }
            throw "Process exited before port $Port opened (exit $($Process.ExitCode)). Log: $ErrorLog`n$detail"
        }
        $tcp = New-Object Net.Sockets.TcpClient
        try { $tcp.Connect('127.0.0.1', $Port); return } catch {} finally { $tcp.Dispose() }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for port $Port. See logs."
}
# Refuse occupied ports. Never attach to or change another installed database/server.
foreach ($port in @(13307,3105,14445,14446)) {
    $test = New-Object Net.Sockets.TcpClient
    try { $test.Connect('127.0.0.1', $port); throw "Port $port is in use. Close the previous local test first." }
    catch [Net.Sockets.SocketException] {} finally { $test.Dispose() }
}
New-Item logs -ItemType Directory -Force | Out-Null
New-Item local-data -ItemType Directory -Force | Out-Null

$lock = $null
$dbProcess = $null
$gameServer = $null
$loginServer = $null
$temporaryJava = $null
try {
    $java = Join-Path $root 'runtime/java/bin/java.exe'
    foreach ($relative in @('bin/java.exe', 'bin/java.dll', 'bin/server/jvm.dll', 'lib/modules')) {
        if (!(Test-Path -LiteralPath (Join-Path "$root/runtime/java" $relative))) {
            throw "Bundled Java is incomplete ($relative missing). Extract the entire ZIP again into C:\NgocRongLive; do not copy java.exe alone."
        }
    }
    if ($java -match '[^\x00-\x7F]') {
        # The bundled native Java launcher cannot locate java.dll under Unicode paths.
        $candidates = @($env:TEMP, (Join-Path $env:PUBLIC 'Documents'))
        foreach ($candidate in $candidates) {
            if (!$candidate -or $candidate -match '[^\x00-\x7F]') { continue }
            $destination = Join-Path $candidate ('NgocRongLive-Java-' + [Guid]::NewGuid().ToString('N'))
            try {
                New-Item -Path $destination -ItemType Directory -ErrorAction Stop | Out-Null
            } catch { continue }
            $temporaryJava = $destination
            Copy-Item -LiteralPath (Join-Path $root 'runtime/java') -Destination (Join-Path $destination 'java') -Recurse
            $java = Join-Path $destination 'java/bin/java.exe'
            Write-Host 'Using temporary ASCII path for bundled Java.'
            break
        }
        if ($java -match '[^\x00-\x7F]') { throw 'No writable ASCII temporary path. Extract the entire ZIP into C:\NgocRongLive.' }
    }
    $javaCheck = Start-Process $java -ArgumentList '-version' -Wait -PassThru -WindowStyle Hidden -RedirectStandardOutput logs/java.log -RedirectStandardError logs/java-error.log
    if ($javaCheck.ExitCode -ne 0) {
        $detail = (Get-Content -LiteralPath logs/java-error.log -Tail 35 -ErrorAction SilentlyContinue) -join "`n"
        throw "Bundled Java failed before database startup (exit $($javaCheck.ExitCode)). Extract the entire ZIP into C:\NgocRongLive and retry. Log: logs/java-error.log`n$detail"
    }
    $lock = [IO.File]::Open((Join-Path $root 'local-data/run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    if (!(Test-Path local-data/credentials.json)) {
        $credentials = @{ root = [Guid]::NewGuid().ToString('N'); database = [Guid]::NewGuid().ToString('N'); player = [Guid]::NewGuid().ToString('N').Substring(0,12) }
        $credentials | ConvertTo-Json | Set-Content local-data/credentials.json -Encoding ascii
    }
    $credentials = Get-Content local-data/credentials.json -Raw | ConvertFrom-Json
    $dbHome = Join-Path $root 'runtime/mariadb'
    $bin = Join-Path $dbHome 'bin'
    $data = Join-Path $root 'local-data/mysql'
    if (!(Test-Path "$data/mysql")) {
        if (Test-Path $data) { throw 'Incomplete database initialization. Preserve data and inspect logs before recovery.' }
        $install = Join-Path $bin 'mariadb-install-db.exe'
        if (!(Test-Path $install)) { $install = Join-Path $bin 'mysql_install_db.exe' }
        & $install "--datadir=$data" '--port=13307' "--password=$($credentials.root)" '--silent' *> logs/db-init.log
        if ($LASTEXITCODE) { throw 'Database initialization failed; see logs/db-init.log' }
    }
    $mysqld = Join-Path $bin 'mariadbd.exe'
    if (!(Test-Path $mysqld)) { $mysqld = Join-Path $bin 'mysqld.exe' }
    $dbArgs = @('--no-defaults', "--basedir=`"$dbHome`"", "--datadir=`"$data`"", '--port=13307', '--bind-address=127.0.0.1', '--sql-mode=', '--console')
    $dbProcess = Start-Process $mysqld -ArgumentList $dbArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput logs/db.log -RedirectStandardError logs/db-error.log
    Wait-Port 13307 $dbProcess (Join-Path $root 'logs/db-error.log')
    $cnf = "[client]`nuser=root`npassword=$($credentials.root)`nhost=127.0.0.1`nport=13307`nprotocol=tcp`n"
    [IO.File]::WriteAllText((Join-Path $root 'local-data/client.ini'), $cnf)
    $mysql = Join-Path $bin 'mariadb.exe'
    if (!(Test-Path $mysql)) { $mysql = Join-Path $bin 'mysql.exe' }
    $defaults = '--defaults-file=' + (Join-Path $root 'local-data/client.ini')
    if (!(Test-Path local-data/initialized)) {
        $setup = "CREATE DATABASE IF NOT EXISTS ngocrong CHARACTER SET utf8mb4; CREATE USER IF NOT EXISTS 'nro'@'localhost' IDENTIFIED BY '$($credentials.database)'; GRANT ALL ON ngocrong.* TO 'nro'@'localhost';"
        & $mysql $defaults --execute=$setup
        if ($LASTEXITCODE) { throw 'Database/user setup failed' }
        $seed = (Join-Path $root 'server/seed.sql').Replace('\','/')
        & $mysql $defaults '--default-character-set=utf8mb4' 'ngocrong' "--execute=source $seed"
        if ($LASTEXITCODE) { throw 'Seed import failed. No completion marker written; preserve data for diagnosis.' }
        $account = "INSERT INTO account(username,password,active,server_login) VALUES ('tester','$($credentials.player)',1,1);"
        & $mysql $defaults ngocrong --execute=$account
        if ($LASTEXITCODE) { throw 'Test account creation failed' }
        'ready' | Set-Content local-data/initialized
    }
    $serverConfig = @"
server.db.ip=127.0.0.1
server.db.port=13307
server.db.name=ngocrong
server.db.us=nro
server.db.pw=$($credentials.database)
server.db.max=20
server.sv=1
server.sv1=NgocRongLive:127.0.0.1:14445:0,0,0
server.port=14445
server.debug=false
server.waitlogin=1
server.maxperip=10
server.maxplayer=20
server.expserver=3
server.name=NgocRongLive
server.domain=localhost
login.host=127.0.0.1
server.event=0
server.activeKey=false
api.port=8181
api.key=local-only
"@
    $serverConfig | Set-Content server/resources/config/server.properties -Encoding ascii
    @"
server.port=3105
db.port=13307
db.host=127.0.0.1
db.user=nro
db.password=$($credentials.database)
db.name=ngocrong
db.driver=com.mysql.cj.jdbc.Driver
admin.mode=0
wait.login=0
"@ | Set-Content login/server.ini -Encoding ascii
    $loginServer = Start-Process $java -WorkingDirectory (Join-Path $root 'login') -ArgumentList @('-Dfile.encoding=UTF-8','-Xms64m','-Xmx256m','-jar','app.jar') -PassThru -WindowStyle Hidden -RedirectStandardOutput logs/login.log -RedirectStandardError logs/login-error.log
    Wait-Port 3105 $loginServer (Join-Path $root 'logs/login-error.log')
    $gameServer = Start-Process $java -WorkingDirectory (Join-Path $root 'server') -ArgumentList @('-Dfile.encoding=UTF-8','-Xms128m','-Xmx1024m','-jar','app.jar') -PassThru -WindowStyle Hidden -RedirectStandardOutput logs/server.log -RedirectStandardError logs/server-error.log
    Wait-Port 14445 $gameServer (Join-Path $root 'logs/server-error.log')
    if ($SmokeTest) {
        & python (Join-Path $root 'protocol_smoke.py') (Join-Path $root 'local-data/credentials.json')
        if ($LASTEXITCODE) { throw 'Yanii protocol test failed' }
        Write-Host 'PASS: Yanii login and game protocol checks completed.'
        if ($ClientPreview) { & (Join-Path $root 'client-preview.ps1') -ClientDirectory (Join-Path $root 'client') -OutputDirectory (Join-Path $root 'logs/preview') }
        Write-Host 'Protocol tests do not verify graphical login, character creation or gameplay.'
    } else {
        "Tai khoan: tester`r`nMat khau: $($credentials.player)" | Set-Content TAI-KHOAN.txt -Encoding utf8
        Write-Host 'Tai khoan nam trong TAI-KHOAN.txt. Thoat game binh thuong truoc khi dong cua so nay.'
        $client = Start-Process (Join-Path $root 'client/NroYanii.exe') -WorkingDirectory (Join-Path $root 'client') -ArgumentList @('-logFile', ('"' + (Join-Path $root 'logs/client.log') + '"')) -PassThru
        $client.WaitForExit()
        Start-Sleep -Seconds 3
    }
} finally {
    if ($null -ne $gameServer -and !$gameServer.HasExited) { Stop-Process -Id $gameServer.Id }
    if ($null -ne $loginServer -and !$loginServer.HasExited) { Stop-Process -Id $loginServer.Id }
    if ($null -ne $dbProcess -and !$dbProcess.HasExited) {
        $admin = Join-Path $root 'runtime/mariadb/bin/mariadb-admin.exe'
        if (!(Test-Path $admin)) { $admin = Join-Path $root 'runtime/mariadb/bin/mysqladmin.exe' }
        & $admin ('--defaults-file=' + (Join-Path $root 'local-data/client.ini')) shutdown 2>$null
        $dbProcess.WaitForExit(10000) | Out-Null
    }
    if ($null -ne $temporaryJava) { Remove-Item -LiteralPath $temporaryJava -Recurse -Force -ErrorAction SilentlyContinue }
    Remove-Item Env:NRO_DB_PASSWORD -ErrorAction SilentlyContinue
    if ($null -ne $lock) { $lock.Dispose() }
}
