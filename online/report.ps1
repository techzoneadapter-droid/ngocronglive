$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$reportRoot = Join-Path $root ('reports/report-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -Path $reportRoot -ItemType Directory -Force | Out-Null
$source = Join-Path $root 'logs/client.log'
if (Test-Path $source) {
    $content = (Get-Content -LiteralPath $source -Tail 1500 -Encoding UTF8) -join "`r`n"
    $content = $content -replace '(?im)^.*(password|passwd|authorization|access.token|mat khau|mật khẩu).*$','[redacted credential line]'
    $content | Set-Content -LiteralPath (Join-Path $reportRoot 'client.log') -Encoding UTF8
}
if (Test-Path (Join-Path $root 'build-info.json')) { Copy-Item (Join-Path $root 'build-info.json') $reportRoot }
@"
Thoi gian may nguoi choi: $(Get-Date -Format o)
Windows: $([Environment]::OSVersion.VersionString)
Buoc tai hien:
1.
2.
Ket qua mong doi:
Ket qua thuc te:
Map / NPC / nhiem vu / phim lien quan:
Khong gui mat khau, ma nap, local-data hoac cau hinh server.
"@ | Set-Content (Join-Path $reportRoot 'MO-TA-LOI.txt') -Encoding UTF8
$zip = $reportRoot + '.zip'
Compress-Archive -Path (Join-Path $reportRoot '*') -DestinationPath $zip
Write-Host "Da gom log: $zip"
Write-Host 'Xem lai noi dung truoc khi dinh kem vao GitHub. Chua tu dong gui file.'
Start-Process explorer.exe -ArgumentList @('/select,', ('"' + $zip + '"'))
Start-Process 'https://github.com/techzoneadapter-droid/ngocronglive/issues/new?template=beta-test.yml'
