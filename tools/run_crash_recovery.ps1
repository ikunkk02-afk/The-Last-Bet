$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$points = @('PREPARE_BEFORE', 'PREPARE_AFTER', 'PLAYER_BEFORE', 'PLAYER_AFTER', 'BANK_BEFORE', 'BANK_AFTER', 'COMPLETE_BEFORE', 'COMPLETE_AFTER')
$results = @()
foreach ($point in $points) {
    $caseDirectory = Join-Path $projectRoot "build/crash-$point"
    if (Test-Path -LiteralPath (Join-Path $caseDirectory 'world')) {
        throw "Existing crash evidence retained at $caseDirectory. Archive it explicitly before rerunning this fresh-world suite."
    }
    New-Item -ItemType Directory -Force $caseDirectory | Out-Null
    @'
server-ip=127.0.0.1
server-port=25590
online-mode=false
max-players=1
view-distance=2
simulation-distance=2
level-type=minecraft:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
generate-structures=false
'@ | Set-Content -LiteralPath (Join-Path $caseDirectory 'server.properties') -Encoding ascii
    'eula=true' | Set-Content -LiteralPath (Join-Path $caseDirectory 'eula.txt') -Encoding ascii
    $crashLog = Join-Path $caseDirectory 'launch-crash.local.log'
    & .\gradlew.bat runCrashServer "-PbankCrashPoint=$point" '-PbankCrashMode=crash' --no-configuration-cache --console=plain *> $crashLog
    if ($LASTEXITCODE -eq 0 -or -not (Select-String -LiteralPath $crashLog -Pattern "LASTBET_CONTROLLED_HALT $point" -Quiet) -or -not (Select-String -LiteralPath $crashLog -Pattern 'exit value 86' -Quiet)) {
        throw "Expected deliberate JVM halt was not observed: $crashLog"
    }
    $recoverLog = Join-Path $caseDirectory 'launch-recover.local.log'
    & .\gradlew.bat runCrashServer "-PbankCrashPoint=$point" '-PbankCrashMode=recover' --no-configuration-cache --console=plain *> $recoverLog
    if ($LASTEXITCODE -ne 0) { throw "Fresh-JVM recovery failed: $recoverLog" }
    $result = Get-Content -LiteralPath (Join-Path $caseDirectory 'crash-recovered.json') -Raw | ConvertFrom-Json
    if (-not $result.passed) { throw "Crash recovery assertion failed for $point" }
    $results += $result
    Write-Output "PASS $point : balance=$($result.balance), emeralds=$($result.emeralds), history=$($result.history)"
}
$results | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $projectRoot 'build/crash-recovery-results.json') -Encoding utf8
