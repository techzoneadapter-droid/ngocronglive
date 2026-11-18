param([Parameter(Mandatory=$true)][string]$RuntimeJar)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$runtime=(Resolve-Path $RuntimeJar).Path
$out=Join-Path $root ('build/kaizen-drops-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
$src=Join-Path $root 'game-source/server/src/main/java/nro'
$files=@(Get-ChildItem (Join-Path $src 'progression') -Filter '*.java' | ForEach-Object FullName)
$files+=@(Join-Path $src 'models/player/NPoint.java';Join-Path $src 'models/player/ProgressionMath.java';Join-Path $src 'models/boss/Boss.java';Join-Path $src 'services/RewardService.java')
& javac -proc:none -encoding UTF-8 -cp $runtime -d $out @files (Join-Path $root 'tests/KaizenDropConfigTest.java') (Join-Path $root 'tests/KaizenRewardServiceTest.java')
if($LASTEXITCODE){throw 'Kaizen drop compilation failed'}
& java -cp "$out;$runtime" nro.progression.KaizenDropConfigTest (Join-Path $root 'game-source/server/resources/config/kaizen-drops.json')
if($LASTEXITCODE){throw 'Kaizen rate/configuration tests failed'}
& java -cp "$out;$runtime" nro.progression.KaizenRewardServiceTest
if($LASTEXITCODE){throw 'Native RewardService tests failed'}
Write-Host "PASS KAIZEN_DROP_TESTS CLASSES=$out"
