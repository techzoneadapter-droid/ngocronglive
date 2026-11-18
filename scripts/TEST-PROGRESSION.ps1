$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = Join-Path ([IO.Path]::GetTempPath()) ('nero-progression-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output | Out-Null
$java = Join-Path $root 'game-source/server/src/main/java/nro'
$sources = @((Join-Path $java 'models/player/ProgressionMath.java'),
    (Join-Path $java 'progression/UnlimitedProgression.java'),
    (Join-Path $java 'progression/BossProgression.java'),
    (Join-Path $root 'tests/ProgressionMathTest.java'),
    (Join-Path $root 'tests/UnlimitedProgressionTest.java'),
    (Join-Path $root 'tests/BossProgressionTest.java'))
& javac -encoding UTF-8 -d $output @sources
if($LASTEXITCODE){throw 'Progression test compilation failed'}
foreach($test in @('nro.models.player.ProgressionMathTest','nro.progression.UnlimitedProgressionTest','nro.progression.BossProgressionTest')) {
    & java -cp $output $test
    if($LASTEXITCODE){throw "Failed: $test"}
}
Write-Host 'PASS ALL_PROGRESSION_CORE_TESTS (no database or live server changes)'
