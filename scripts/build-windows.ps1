$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (!(Test-Path upstream)) {
    git clone https://github.com/nvt25/Online_nro.git upstream
    if ($LASTEXITCODE) { throw 'Clone failed' }
}
git -C upstream checkout 1732e1f258b69dd812de96d35fd223624b950128
if ($LASTEXITCODE) { throw 'Pinned checkout failed' }
python scripts/prepare.py upstream build/NgocRongLive-PC
if ($LASTEXITCODE) { throw 'Preparation failed' }
$out = Join-Path $root 'build/NgocRongLive-PC'
$server = Join-Path $out 'server'
Push-Location $server
try {
    $sources = @(Get-ChildItem src -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
    $sources | Set-Content sources.txt -Encoding utf8
    New-Item classes -ItemType Directory -Force | Out-Null
    & javac --release 8 -encoding UTF-8 -cp 'lib/*' -d classes '@sources.txt'
    if ($LASTEXITCODE) { throw 'Java compilation failed' }
} finally { Pop-Location }
$assembly = Join-Path $out 'client/Dragonboy_vn_v230_Data/Managed/Assembly-CSharp.dll'
& dotnet run --project tools/ClientPatcher -c Release -- $assembly "$assembly.patched"
if ($LASTEXITCODE) { throw 'Client patch failed' }
Move-Item "$assembly.patched" $assembly -Force
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
Copy-Item scripts/launch.ps1 "$out/launch.ps1"
Copy-Item scripts/PLAY.cmd "$out/PLAY.cmd"
Copy-Item README.md "$out/README.md"
Copy-Item docs/STATUS.md "$out/STATUS.md"
@{ upstream = '1732e1f258b69dd812de96d35fd223624b950128'; javaSHA256 = (Get-FileHash $jdkZip).Hash; mariaDBSHA256 = $expected; builtUTC = [DateTime]::UtcNow.ToString('o') } | ConvertTo-Json | Set-Content "$out/build-info.json"
Write-Host "Built $out"
