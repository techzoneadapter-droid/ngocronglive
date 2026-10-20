$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$defaults=Join-Path $root 'local-data/client.ini'
if (!(Test-Path $defaults)) { throw 'Start SERVER.cmd before taking a SQL backup.' }
$dump=Join-Path $root 'runtime/mariadb/bin/mariadb-dump.exe'
if (!(Test-Path $dump)) { $dump=Join-Path $root 'runtime/mariadb/bin/mysqldump.exe' }
New-Item (Join-Path $root 'backups') -ItemType Directory -Force | Out-Null
$file=Join-Path $root ('backups/ngocrong-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')+'.sql')
$temporary=$file+'.partial'
& $dump ('--defaults-file='+$defaults) '--single-transaction' '--quick' '--default-character-set=utf8mb4' ('--result-file='+$temporary) 'ngocrong'
if ($LASTEXITCODE) { Remove-Item $temporary -ErrorAction SilentlyContinue; throw 'Backup failed. Existing backups were preserved.' }
Move-Item -LiteralPath $temporary -Destination $file
(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash | Set-Content ($file+'.sha256') -Encoding ascii
Write-Host ('Backup saved: '+$file)
