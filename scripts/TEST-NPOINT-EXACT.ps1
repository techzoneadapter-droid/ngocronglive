param([Parameter(Mandatory=$true)][string]$RuntimeJar)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$runtime=(Resolve-Path $RuntimeJar).Path
$output=Join-Path $root ('build/npoint-exact-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
$src=Join-Path $root 'game-source/server/src/main/java/nro'
$production=@(Get-ChildItem (Join-Path $src 'progression') -Filter '*.java' | ForEach-Object FullName)
$production+=@(Join-Path $src 'services/RewardService.java')
$production+=@(Join-Path $src 'models/player/NPoint.java';Join-Path $src 'models/player/ProgressionMath.java';Join-Path $src 'models/boss/Boss.java')
& javac -proc:none -encoding UTF-8 -cp $runtime -d $output @production (Join-Path $root 'tests/NPointExactStatsTest.java')
if($LASTEXITCODE){throw 'NPoint exact compilation failed'}
& java -cp "$output;$runtime" nro.models.player.NPointExactStatsTest
if($LASTEXITCODE){throw 'Real NPoint exact tests failed'}
Write-Host "PASS NPOINT_EXACT_TESTS CLASSES=$output"
