param([switch]$SmokeTest)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = $PSScriptRoot
Set-Location $root
function Wait-Port([int]$Port, $Process) {
    for ($i=0; $i -lt 60; $i++) {
        if ($Process.HasExited) { throw "Process exited before port $Port opened. See logs." }
        $tcp = New-Object Net.Sockets.TcpClient
        try { $tcp.Connect('127.0.0.1', $Port); return } catch {} finally { $tcp.Dispose() }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for port $Port. See logs."
}
# Refuse occupied ports. Never attach to or change another installed database/server.
foreach ($port in @(13306,14445)) {
    $test = New-Object Net.Sockets.TcpClient
    try { $test.Connect('127.0.0.1', $port); throw "Port $port is in use. Close the previous local test first." }
    catch [Net.Sockets.SocketException] {} finally { $test.Dispose() }
}
New-Item logs -ItemType Directory -Force | Out-Null
New-Item data -ItemType Directory -Force | Out-Null
$lock = $null
$dbProcess = $null
$gameServer = $null
try {
    $lock = [IO.File]::Open((Join-Path $root 'data/run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    if (!(Test-Path data/credentials.json)) {
        $credentials = @{ root = [Guid]::NewGuid().ToString('N'); database = [Guid]::NewGuid().ToString('N'); player = [Guid]::NewGuid().ToString('N').Substring(0,12) }
        $credentials | ConvertTo-Json | Set-Content data/credentials.json -Encoding ascii
    }
    $credentials = Get-Content data/credentials.json -Raw | ConvertFrom-Json
    $dbHome = Join-Path $root 'runtime/mariadb'
    $bin = Join-Path $dbHome 'bin'
    $data = Join-Path $root 'data/mysql'
    if (!(Test-Path "$data/mysql")) {
        if (Test-Path $data) { throw 'Incomplete database initialization. Preserve data and inspect logs before recovery.' }
        $install = Join-Path $bin 'mariadb-install-db.exe'
        if (!(Test-Path $install)) { $install = Join-Path $bin 'mysql_install_db.exe' }
        & $install "--datadir=$data" '--port=13306' "--password=$($credentials.root)" '--silent' *> logs/db-init.log
        if ($LASTEXITCODE) { throw 'Database initialization failed; see logs/db-init.log' }
    }
    $mysqld = Join-Path $bin 'mariadbd.exe'
    if (!(Test-Path $mysqld)) { $mysqld = Join-Path $bin 'mysqld.exe' }
    $dbArgs = @('--no-defaults', "--basedir=`"$dbHome`"", "--datadir=`"$data`"", '--port=13306', '--bind-address=127.0.0.1', '--sql-mode=', '--console')
    $dbProcess = Start-Process $mysqld -ArgumentList $dbArgs -PassThru -WindowStyle Hidden -RedirectStandardOutput logs/db.log -RedirectStandardError logs/db-error.log
    Wait-Port 13306 $dbProcess
    $cnf = "[client]`nuser=root`npassword=$($credentials.root)`nhost=127.0.0.1`nport=13306`nprotocol=tcp`n"
    [IO.File]::WriteAllText((Join-Path $root 'data/client.ini'), $cnf)
    $mysql = Join-Path $bin 'mariadb.exe'
    if (!(Test-Path $mysql)) { $mysql = Join-Path $bin 'mysql.exe' }
    $defaults = '--defaults-file=' + (Join-Path $root 'data/client.ini')
    if (!(Test-Path data/initialized)) {
        $setup = "CREATE DATABASE IF NOT EXISTS server CHARACTER SET utf8mb4; CREATE USER IF NOT EXISTS 'nro'@'localhost' IDENTIFIED BY '$($credentials.database)'; GRANT ALL ON server.* TO 'nro'@'localhost';"
        & $mysql $defaults --execute=$setup
        if ($LASTEXITCODE) { throw 'Database/user setup failed' }
        $seed = (Join-Path $root 'server/seed.sql').Replace('\','/')
        & $mysql $defaults '--default-character-set=utf8mb4' 'server' "--execute=source $seed"
        if ($LASTEXITCODE) { throw 'Seed import failed. No completion marker written; preserve data for diagnosis.' }
        $account = "INSERT INTO account(username,password,nhanvat) VALUES ('tester','$($credentials.player)','');"
        & $mysql $defaults server --execute=$account
        if ($LASTEXITCODE) { throw 'Test account creation failed' }
        'ready' | Set-Content data/initialized
    }
    $env:NRO_DB_PASSWORD = $credentials.database
    $env:NRO_DB_URL = 'jdbc:mysql://127.0.0.1:13306/server'
    $java = Join-Path $root 'runtime/java/bin/java.exe'
    if ($SmokeTest) {
        Push-Location (Join-Path $root 'server')
        try {
            & $java '-Dfile.encoding=UTF-8' '-cp' 'classes;lib/*' 'real.player.LocalPlayerStore' '--test-isolated-db'
            if ($LASTEXITCODE) { throw 'Persistence integration test failed' }
        } finally { Pop-Location }
    }
    $gameServer = Start-Process $java -WorkingDirectory (Join-Path $root 'server') -ArgumentList @('-Dfile.encoding=UTF-8','-Xms128m','-Xmx768m','-cp','"classes;lib/*"','server.Server') -PassThru -WindowStyle Hidden -RedirectStandardOutput logs/server.log -RedirectStandardError logs/server-error.log
    Wait-Port 14445 $gameServer
    if ($SmokeTest) {
        & python (Join-Path $root 'protocol_smoke.py') (Join-Path $root 'data/credentials.json')
        if ($LASTEXITCODE) { throw 'Socket authentication test failed' }
        Write-Host 'PASS: isolated database seeded; game server listening on 127.0.0.1:14445.'
        Write-Host 'This does not verify graphical login, character creation or gameplay.'
    } else {
        "Tai khoan: tester`r`nMat khau: $($credentials.player)" | Set-Content TAI-KHOAN.txt -Encoding utf8
        Write-Host 'Tai khoan nam trong TAI-KHOAN.txt. Thoat game binh thuong truoc khi dong cua so nay.'
        $client = Start-Process (Join-Path $root 'client/Dragonboy_vn_v230.exe') -PassThru
        $client.WaitForExit()
        Start-Sleep -Seconds 3
    }
} finally {
    if ($null -ne $gameServer -and !$gameServer.HasExited) { Stop-Process -Id $gameServer.Id }
    if ($null -ne $dbProcess -and !$dbProcess.HasExited) {
        $admin = Join-Path $root 'runtime/mariadb/bin/mariadb-admin.exe'
        if (!(Test-Path $admin)) { $admin = Join-Path $root 'runtime/mariadb/bin/mysqladmin.exe' }
        & $admin ('--defaults-file=' + (Join-Path $root 'data/client.ini')) shutdown 2>$null
        $dbProcess.WaitForExit(10000) | Out-Null
    }
    Remove-Item Env:NRO_DB_PASSWORD -ErrorAction SilentlyContinue
    if ($null -ne $lock) { $lock.Dispose() }
}
