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
    & (Join-Path $bin 'mariadb.exe') --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root -e 'CREATE DATABASE nero_progression_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
    if($LASTEXITCODE){throw 'Cannot create disposable test schema'}
    $src = Join-Path $root 'game-source/server/src/main/java/nro/progression'
    $production=@(Get-ChildItem $src -Filter '*.java' | ForEach-Object FullName)
    $production+=@(Join-Path $root 'game-source/server/src/main/java/nro/services/RewardService.java')
    $production+=@(Join-Path $root 'game-source/server/src/main/java/nro/models/player/NPoint.java';Join-Path $root 'game-source/server/src/main/java/nro/models/player/ProgressionMath.java';Join-Path $root 'game-source/server/src/main/java/nro/models/boss/Boss.java')
    & javac -proc:none -encoding UTF-8 -cp $runtime -d $classes @production (Join-Path $root 'tests/ProgressionRepositoryTest.java')
    if($LASTEXITCODE){throw 'Repository test compilation failed'}
    $playerSource=Join-Path $root 'game-source/server/src/main/java/nro/models/player'
    & javac -proc:none -encoding UTF-8 -cp "$classes;$runtime" -d $classes (Join-Path $playerSource 'NPoint.java') (Join-Path $playerSource 'ProgressionMath.java') (Join-Path $src 'ExactCombat.java') (Join-Path $src 'BossEncounter.java') (Join-Path $root 'tests/NPointExactStatsTest.java') (Join-Path $root 'tests/BossEncounterDatabaseTest.java')
    if($LASTEXITCODE){throw 'Boss encounter test compilation failed'}
    & javac -proc:none -encoding UTF-8 -cp "$classes;$runtime" -d $classes (Join-Path $root 'game-source/server/src/main/java/nro/models/boss/Boss.java') (Join-Path $src 'BossProgressionRuntime.java') (Join-Path $src 'ExactSkillDamage.java') (Join-Path $root 'tests/BossRuntimeDatabaseTest.java')
    if($LASTEXITCODE){throw 'Boss runtime compilation failed'}
    & javac -proc:none -encoding UTF-8 -cp "$classes;$runtime" -d $classes (Join-Path $src 'KaizenRecipes.java') (Join-Path $src 'KaizenForge.java') (Join-Path $src 'KaizenTransactions.java') (Join-Path $root 'tests/KaizenTransactionsTest.java')
    if($LASTEXITCODE){throw 'Kaizen compilation failed'}
    & javac -proc:none -encoding UTF-8 -cp "$classes;$runtime" -d $classes (Join-Path $src 'KaizenCatalog.java') (Join-Path $root 'tests/KaizenCatalogTest.java')
    if($LASTEXITCODE){throw 'Kaizen catalog compilation failed'}
    & javac -proc:none -encoding UTF-8 -cp "$classes;$runtime" -d $classes (Join-Path $root 'tests/PlayerProgressionRuntimeTest.java')
    if($LASTEXITCODE){throw 'Player runtime compilation failed'}
    $jdbc = "jdbc:mysql://127.0.0.1:$Port/nero_progression_test?useSSL=false&allowPublicKeyRetrieval=true"
    & java -cp "$classes;$runtime" nro.progression.ProgressionRepositoryTest $jdbc seed
    if($LASTEXITCODE){throw 'Repository integration tests failed'}
    & java -cp "$classes;$runtime" nro.progression.BossEncounterDatabaseTest $jdbc seed
    if($LASTEXITCODE){throw 'Boss encounter integration test failed'}
    & java -cp "$classes;$runtime" nro.progression.BossRuntimeDatabaseTest $jdbc seed
    if($LASTEXITCODE){throw 'Actual boss lifecycle tests failed'}
    & java -cp "$classes;$runtime" nro.progression.KaizenTransactionsTest $jdbc seed
    if($LASTEXITCODE){throw 'Kaizen transaction tests failed'}
    & java -cp "$classes;$runtime" nro.progression.KaizenCatalogTest $jdbc seed
    if($LASTEXITCODE){throw 'Kaizen catalog tests failed'}
    & java -cp "$classes;$runtime" nro.progression.PlayerProgressionRuntimeTest $jdbc seed
    if($LASTEXITCODE){throw 'Player runtime tests failed'}
    Stop-TestDatabase
    Start-TestDatabase
    & java -cp "$classes;$runtime" nro.progression.ProgressionRepositoryTest $jdbc reopen
    if($LASTEXITCODE){throw 'Database restart verification failed'}
    & java -cp "$classes;$runtime" nro.progression.BossEncounterDatabaseTest $jdbc reopen
    if($LASTEXITCODE){throw 'Boss encounter restart verification failed'}
    & java -cp "$classes;$runtime" nro.progression.BossRuntimeDatabaseTest $jdbc reopen
    if($LASTEXITCODE){throw 'Actual boss lifecycle restart failed'}
    & java -cp "$classes;$runtime" nro.progression.KaizenTransactionsTest $jdbc reopen
    if($LASTEXITCODE){throw 'Kaizen restart tests failed'}
    & java -cp "$classes;$runtime" nro.progression.KaizenCatalogTest $jdbc reopen
    if($LASTEXITCODE){throw 'Kaizen catalog restart failed'}
    & java -cp "$classes;$runtime" nro.progression.PlayerProgressionRuntimeTest $jdbc reopen
    if($LASTEXITCODE){throw 'Player runtime reconnect failed'}
    Write-Host "PASS PROGRESSION_DATABASE_TESTS ARTIFACTS=$stage"
} finally { Stop-TestDatabase }
