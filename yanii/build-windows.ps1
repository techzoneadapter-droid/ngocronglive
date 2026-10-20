$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (!(Test-Path upstream-yanii)) {
    git clone https://github.com/yanii7905/Nro_Yanii.git upstream-yanii
    if ($LASTEXITCODE) { throw 'Yanii clone failed' }
}
git -C upstream-yanii checkout a443509f203eb21b7f39bb5a5234df4b1ddcfbea
if ($LASTEXITCODE) { throw 'Pinned checkout failed' }
python yanii/prepare.py upstream-yanii build/NgocRongLive-Yanii-PC
if ($LASTEXITCODE) { throw 'Preparation failed' }
$out = Join-Path $root 'build/NgocRongLive-Yanii-PC'
foreach ($component in @('server','login')) {
    Push-Location (Join-Path $out $component)
    try {
        & mvn -B -q package '-DskipTests'
        if ($LASTEXITCODE) { throw "Compilation failed: $component" }
        $jar = @(Get-ChildItem target/*jar-with-dependencies.jar)
        if ($jar.Count -ne 1) { throw 'Unexpected Maven output' }
        Copy-Item $jar[0].FullName app.jar
        Remove-Item target -Recurse -Force
    } finally { Pop-Location }
}
New-Item "$out/runtime" -ItemType Directory -Force | Out-Null
New-Item cache -ItemType Directory -Force | Out-Null
$jdkZip = Join-Path $root 'cache/jdk.zip'
Invoke-WebRequest 'https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip' -OutFile $jdkZip
Expand-Archive $jdkZip "$out/runtime/jdk-unpack"
$jdk = @(Get-ChildItem "$out/runtime/jdk-unpack" -Directory)
if ($jdk.Count -ne 1) { throw 'Unexpected JDK archive layout' }
Move-Item $jdk[0].FullName "$out/runtime/java"
Remove-Item "$out/runtime/jdk-unpack" -Recurse
$dbBase = 'https://archive.mariadb.org/mariadb-10.11.11/winx64-packages/'
$dbName = 'mariadb-10.11.11-winx64.zip'
Invoke-WebRequest ($dbBase + $dbName) -OutFile cache/mariadb.zip
$sums = (Invoke-WebRequest ($dbBase + 'sha256sums.txt')).Content
if ($sums -is [byte[]]) { $sums = [Text.Encoding]::UTF8.GetString($sums) }
$line = @($sums -split "`n" | Where-Object { $_ -match [regex]::Escape($dbName) })
if ($line.Count -ne 1) { throw 'Missing vendor database checksum' }
$expected = [regex]::Match($line[0], '[a-fA-F0-9]{64}').Value
if ($expected.Length -ne 64) { throw 'Invalid vendor database checksum format' }
if ((Get-FileHash cache/mariadb.zip -Algorithm SHA256).Hash -ne $expected) { throw 'Database checksum mismatch' }
Expand-Archive cache/mariadb.zip "$out/runtime/db-unpack"
$db = @(Get-ChildItem "$out/runtime/db-unpack" -Directory)
if ($db.Count -ne 1) { throw 'Unexpected MariaDB archive layout' }
Move-Item $db[0].FullName "$out/runtime/mariadb"
Remove-Item "$out/runtime/db-unpack" -Recurse

Copy-Item yanii/launch.ps1 "$out/launch.ps1"
Copy-Item yanii/client-preview.ps1 "$out/client-preview.ps1"
Copy-Item scripts/PLAY.cmd "$out/PLAY.cmd"
Copy-Item yanii/protocol_smoke.py "$out/protocol_smoke.py"
foreach ($name in @('SERVER.cmd','CLIENT.cmd','ADMIN.cmd','STOP.cmd','BACKUP.cmd','backup.ps1','admin_smoke.py')) { Copy-Item (Join-Path 'yanii' $name) (Join-Path $out $name) }
Copy-Item docs/ADMIN.md "$out/ADMIN-GUIDE.md"
Copy-Item README.md "$out/README.md"
@{ upstream='a443509f203eb21b7f39bb5a5234df4b1ddcfbea'; builtUTC=[DateTime]::UtcNow.ToString('o') } | ConvertTo-Json | Set-Content "$out/build-info.json"
Write-Host "Built Yanii local package: $out"
