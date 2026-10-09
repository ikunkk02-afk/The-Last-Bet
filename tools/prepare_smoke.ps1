$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testRoot = Join-Path $projectRoot 'build'
$serverDir = Join-Path $testRoot 'smoke-server'
$clientDirs = @((Join-Path $testRoot 'client-smoke-a'), (Join-Path $testRoot 'client-smoke-b'))
New-Item -ItemType Directory -Force $serverDir | Out-Null
if (-not (Test-Path (Join-Path $serverDir 'server.properties'))) {
    @'
server-ip=127.0.0.1
server-port=25589
online-mode=false
max-players=4
view-distance=3
simulation-distance=3
spawn-protection=0
level-type=minecraft:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
generate-structures=false
motd=The Last Bet isolated smoke test
'@ | Set-Content (Join-Path $serverDir 'server.properties') -Encoding ascii
}
'eula=true' | Set-Content (Join-Path $serverDir 'eula.txt') -Encoding ascii
foreach ($clientDir in $clientDirs) {
    New-Item -ItemType Directory -Force $clientDir | Out-Null
    if (-not (Test-Path (Join-Path $clientDir 'options.txt'))) {
        @'
lang:zh_cn
guiScale:2
onboardAccessibility:false
maxFps:60
pauseOnLostFocus:false
renderDistance:3
simulationDistance:5
'@ | Set-Content (Join-Path $clientDir 'options.txt') -Encoding ascii
    }
}
Write-Output 'Isolated loopback smoke test prepared under build/. Existing worlds and reports are retained.'
