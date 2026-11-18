param([Parameter(Mandatory=$true)][string]$RuntimeJar)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$runtime=(Resolve-Path $RuntimeJar).Path
$out=Join-Path $root ('build/kaizen-combine-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out,(Join-Path $out 'log') | Out-Null
$src=Join-Path $root 'game-source/server/src/main/java/nro'
$files=@(Get-ChildItem (Join-Path $src 'progression') -Filter '*.java' | ForEach-Object FullName)
$files+=@(Join-Path $src 'models/player/NPoint.java';Join-Path $src 'models/player/ProgressionMath.java';Join-Path $src 'models/boss/Boss.java';Join-Path $src 'services/RewardService.java';Join-Path $src 'services/InventoryService.java';Join-Path $src 'services/func/CombineServiceNew.java')
& javac -proc:none -encoding UTF-8 -cp $runtime -d $out @files (Join-Path $root 'tests/KaizenCombineTest.java')
if($LASTEXITCODE){throw 'Kaizen combine compilation failed'}
Push-Location $out
try {
 & java -cp "$out;$runtime" nro.progression.KaizenCombineTest
 if($LASTEXITCODE){throw 'Original combine regression tests failed'}
} finally {Pop-Location}
Write-Host "PASS KAIZEN_COMBINE_TESTS CLASSES=$out"
