$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$workspace=Split-Path (Split-Path $root -Parent) -Parent
$lab=Join-Path $workspace 'outputs/NERO-VO-HAN-LOCAL'
$kit='C:/Users/bao/Documents/Codex/2026-10-21/che/outputs/NERO-TEST'
$bin=Join-Path $kit 'runtime/mariadb/bin'
foreach($port in @(15484,3198,14615,14606,15617,14618)) {
 if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw "Local test port already used: $port"}
}
$credentials=Get-Content (Join-Path $lab 'local-data/credentials.json') -Raw | ConvertFrom-Json
$config=@"
server.db.ip=127.0.0.1
server.db.port=15484
server.db.name=ngocrong
server.db.us=nro
server.db.pw=$($credentials.database)
server.db.max=20
server.sv=1
server.sv1=NERO-Local:127.0.0.1:14615:0,0,0
server.port=14615
server.debug=false
server.waitlogin=1
server.maxperip=30
server.maxplayer=30
server.expserver=3
server.name=NERO-Local
server.domain=localhost
login.host=127.0.0.1
server.event=0
server.activeKey=false
api.port=8182
api.key=local-only
"@
[IO.File]::WriteAllText((Join-Path $lab 'server/resources/config/server.properties'),$config,[Text.Encoding]::ASCII)
$login=@"
server.port=3198
db.port=15484
db.host=127.0.0.1
db.user=nro
db.password=$($credentials.database)
db.name=ngocrong
db.driver=com.mysql.cj.jdbc.Driver
admin.mode=0
wait.login=0
"@
[IO.File]::WriteAllText((Join-Path $lab 'login/server.ini'),$login,[Text.Encoding]::ASCII)
$client="[client]`nuser=root`npassword=$($credentials.root)`nhost=127.0.0.1`nport=15484`nprotocol=tcp`n"
[IO.File]::WriteAllText((Join-Path $lab 'local-data/client.ini'),$client,[Text.Encoding]::ASCII)
$db=Start-Process (Join-Path $bin 'mariadbd.exe') -ArgumentList @('--no-defaults',"--basedir=`"$(Split-Path $bin -Parent)`"","--datadir=`"$(Join-Path $lab 'local-data/mysql')`"",'--port=15484','--bind-address=127.0.0.1','--sql-mode=','--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $lab 'logs/db.log') -RedirectStandardError (Join-Path $lab 'logs/db-error.log')
$db.Id | Set-Content (Join-Path $lab 'local-data/db.pid')
for($attempt=0;$attempt -lt 60;$attempt++) {
 if(Get-NetTCPConnection -LocalPort 15484 -State Listen -ErrorAction SilentlyContinue){break}
 if($db.HasExited){throw 'Isolated database failed to start'}
 Start-Sleep -Milliseconds 500
}
Remove-Item -LiteralPath (Join-Path $lab 'local-data/stop.request') -ErrorAction SilentlyContinue
$env:NRO_UNLIMITED_LOCAL='1';$env:NRO_GAME_BIND='127.0.0.1';$env:NRO_BOOTSTRAP_BIND='127.0.0.1'
$env:NRO_PUBLIC_HOST='127.0.0.1';$env:NRO_PUBLIC_PORT='14615';$env:NRO_BOOTSTRAP_PORT='14606'
$env:NRO_ADMIN_PORT='15617';$env:NRO_UPDATE_BIND='127.0.0.1';$env:NRO_UPDATE_PORT='14618'
$env:NERO_NOEL_RELEASE='noel-2026'
$env:NRO_ADMIN_PASSWORD=[guid]::NewGuid().ToString('N')
$java=(Get-Command java.exe).Source
$loginProcess=Start-Process $java -WorkingDirectory (Join-Path $lab 'login') -ArgumentList @('-Dfile.encoding=UTF-8','-Xmx256m','-jar','app.jar') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $lab 'logs/login.log') -RedirectStandardError (Join-Path $lab 'logs/login-error.log')
$loginProcess.Id|Set-Content (Join-Path $lab 'local-data/login.pid')
$classpath=(Join-Path $lab 'classes')+';'+(Join-Path $lab 'server/app.jar')
$game=Start-Process $java -WorkingDirectory (Join-Path $lab 'server') -ArgumentList @('-Dfile.encoding=UTF-8','-Xms128m','-Xmx1024m','-cp',('"'+$classpath+'"'),'nro.progression.UnlimitedLocalBootstrap') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $lab 'logs/server.log') -RedirectStandardError (Join-Path $lab 'logs/server-error.log')
$game.Id|Set-Content (Join-Path $lab 'local-data/game.pid')
Write-Host "UNLIMITED_LAB_STARTING LOGS=$lab/logs"

