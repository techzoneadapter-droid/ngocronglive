$ErrorActionPreference = 'Stop'
try {
    $project = Join-Path $PSScriptRoot 'Project'
    $editor = Join-Path $env:ProgramFiles 'Unity/Hub/Editor/2022.3.26f1/Editor/Unity.exe'
    if (!(Test-Path -LiteralPath $editor)) {
        Write-Host 'Can cai Unity Editor 2022.3.26f1 qua Unity Hub. Sau khi cai, bam BUILD-F10 lai.'
        Start-Process 'unityhub://2022.3.26f1/ec6cd8118806'
        throw 'Chua tim thay Unity Editor. Dang nhap Unity Hub va kich hoat giay phep hop le neu duoc hoi.'
    }
    $env:NRO_CLIENT_OUTPUT = Join-Path $PSScriptRoot 'Game-F10/NgocRongLive.exe'
    $log = Join-Path $PSScriptRoot 'build-f10.log'
    Write-Host 'Dang build F10. Lan dau co the mat nhieu phut. Vui long cho...'
    $process = Start-Process -FilePath $editor -Wait -PassThru -ArgumentList @('-batchmode','-quit','-projectPath', ('"' + $project + '"'), '-executeMethod','NroBuild.Windows','-logFile', ('"' + $log + '"'))
    if ($process.ExitCode -ne 0 -or !(Test-Path -LiteralPath $env:NRO_CLIENT_OUTPUT)) {
        Start-Process notepad.exe -ArgumentList ('"' + $log + '"')
        throw 'Build chua thanh cong. Gui file build-f10.log de kiem tra. Khong gui mat khau.'
    }
    Write-Host 'Da build xong. Ban nay can kiem thu hinh anh, F10 va ket noi voi server truoc khi phat hanh.'
    Start-Process explorer.exe -ArgumentList ('"' + (Join-Path $PSScriptRoot 'Game-F10') + '"')
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    Remove-Item Env:NRO_CLIENT_OUTPUT -ErrorAction SilentlyContinue
}
