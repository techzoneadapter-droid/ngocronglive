param(
    [Parameter(Mandatory=$true)][string]$MariaDbBin,
    [Parameter(Mandatory=$true)][string]$RuntimeJar,
    [int]$Port = 15483
)
$ErrorActionPreference = 'Stop'
if(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue){throw "Test port $Port is already in use"}
$root = Split-Path $PSScriptRoot -Parent
$runtime = (Resolve-Path $RuntimeJar).Path
$bin = (Resolve-Path $MariaDbBin).Path
$stage = Join-Path $root ('build/progression-db-' + [guid]::NewGuid().ToString('N'))
$data = Join-Path $stage 'data'
$classes = Join-Path $stage 'classes'
New-Item -ItemType Directory -Path $stage,$classes | Out-Null
& (Join-Path $bin 'mariadb-install-db.exe') "--datadir=$data" "--port=$Port" --silent
if($LASTEXITCODE){throw 'Disposable database initialization failed'}
function Start-TestDatabase {
    $script:databaseProcess = Start-Process -FilePath (Join-Path $bin 'mariadbd.exe') -ArgumentList @(
        "--defaults-file=`"$(Join-Path $data 'my.ini')`"",'--bind-address=127.0.0.1',"--port=$Port",'--skip-name-resolve','--console'
    ) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stage 'database-out.log') -RedirectStandardError (Join-Path $stage 'database-error.log')
    for($attempt=0;$attempt -lt 40;$attempt++) {
        & (Join-Path $bin 'mariadb-admin.exe') --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root ping 2>$null | Out-Null
        if($LASTEXITCODE -eq 0){return}
        if($databaseProcess.HasExited){throw 'Disposable database exited before ready'}
        Start-Sleep -Milliseconds 250
    }
    throw 'Disposable database did not become ready'
}
function Stop-TestDatabase {
    if($script:databaseProcess -and !$script:databaseProcess.HasExited) {
        & (Join-Path $bin 'mariadb-admin.exe') --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root shutdown
        if($LASTEXITCODE){throw 'Disposable database graceful shutdown failed'}
        $script:databaseProcess.WaitForExit(10000) | Out-Null
        if(!$script:databaseProcess.HasExited){throw 'Disposable database still shutting down'}
    }
}
try {
    Start-TestDatabase
    & (Join-Path $bin 'mariadb.exe') --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root -e 'CREATE DATABASE nero_progression_test'
    if($LASTEXITCODE){throw 'Cannot create disposable test schema'}
    $src = Join-Path $root 'game-source/server/src/main/java/nro/progression'
    & javac -encoding UTF-8 -cp $runtime -d $classes (Join-Path $src 'UnlimitedProgression.java') (Join-Path $src 'ProgressionRepository.java') (Join-Path $src 'BossProgression.java') (Join-Path $src 'BossProgressionRepository.java') (Join-Path $root 'tests/ProgressionRepositoryTest.java')
    if($LASTEXITCODE){throw 'Repository test compilation failed'}
    $jdbc = "jdbc:mysql://127.0.0.1:$Port/nero_progression_test?useSSL=false&allowPublicKeyRetrieval=true"
    & java -cp "$classes;$runtime" nro.progression.ProgressionRepositoryTest $jdbc seed
    if($LASTEXITCODE){throw 'Repository integration tests failed'}
    Stop-TestDatabase
    Start-TestDatabase
    & java -cp "$classes;$runtime" nro.progression.ProgressionRepositoryTest $jdbc reopen
    if($LASTEXITCODE){throw 'Database restart verification failed'}
    Write-Host "PASS PROGRESSION_DATABASE_TESTS ARTIFACTS=$stage"
} finally { Stop-TestDatabase }
