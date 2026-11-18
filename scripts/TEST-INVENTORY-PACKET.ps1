param([Parameter(Mandatory=$true)][string]$RuntimeJar)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$runtime=(Resolve-Path $RuntimeJar).Path
$out=Join-Path $root ('build/inventory-packet-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out | Out-Null
$src=Join-Path $root 'game-source/server/src/main/java/nro'
$files=@(Get-ChildItem (Join-Path $src 'progression') -Filter '*.java' | ForEach-Object FullName)
$files+=@(Join-Path $src 'models/player/NPoint.java';Join-Path $src 'models/player/ProgressionMath.java';Join-Path $src 'models/boss/Boss.java';Join-Path $src 'services/RewardService.java';Join-Path $src 'services/InventoryService.java')
& javac -proc:none -encoding UTF-8 -cp $runtime -d $out @files (Join-Path $root 'tests/InventoryPacketTest.java')
if($LASTEXITCODE){throw 'Inventory packet test compilation failed'}
& java -cp "$out;$runtime" nro.progression.InventoryPacketTest
if($LASTEXITCODE){throw 'Inventory packet decode failed'}
Write-Host "PASS INVENTORY_PACKET_TESTS CLASSES=$out"
