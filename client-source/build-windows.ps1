param([Parameter(Mandatory=$true)][string]$UnityEditor)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath $UnityEditor)) { throw 'Unity Editor 2022.3.26f1 executable not found.' }
$project = Join-Path $repo ('build/source-client-' + [Guid]::NewGuid().ToString('N'))
& python (Join-Path $PSScriptRoot 'prepare.py') --cache (Join-Path $repo 'cache/clone-client') --output $project
if ($LASTEXITCODE) { throw 'Source preparation failed' }
$out = Join-Path $project 'Build/NgocRongLive.exe'
$env:NRO_CLIENT_OUTPUT = $out
try {
    $log = Join-Path $project 'unity-build.log'
    $process = Start-Process -FilePath $UnityEditor -Wait -PassThru -ArgumentList @('-batchmode','-quit','-projectPath', ('"' + $project + '"'),'-executeMethod','NroBuild.Windows','-logFile', ('"' + $log + '"'))
    if ($process.ExitCode -ne 0 -or !(Test-Path $out)) { throw "Unity build failed. See $log" }
    Write-Host "SOURCE CLIENT BUILT: $out"
    Write-Host 'Preview only. Start the existing local SERVER.cmd first. This client has not passed graphical compatibility testing.'
} finally { Remove-Item Env:NRO_CLIENT_OUTPUT -ErrorAction SilentlyContinue }
