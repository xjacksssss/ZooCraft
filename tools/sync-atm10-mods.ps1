# Copies the ATM10 instance's mods and configs into run-atm10 so `gradlew runAtm10Client`
# boots the ZooCraft dev build alongside the real modpack. The ATM10 instance itself is never modified.
param(
    [string]$Instance = "$env:USERPROFILE\curseforge\minecraft\Instances\All the Mods 10 - ATM10"
)
$ErrorActionPreference = 'Stop'
$target = Join-Path $PSScriptRoot '..\run-atm10'
New-Item -ItemType Directory -Force $target | Out-Null

foreach ($dir in 'config', 'defaultconfigs', 'kubejs') {
    $src = Join-Path $Instance $dir
    if (Test-Path $src) {
        robocopy $src (Join-Path $target $dir) /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
    }
}

# The dev environment supplies ZooCraft and SmartBrainLib itself, so skip those jars.
$mods = Join-Path $target 'mods'
robocopy (Join-Path $Instance 'mods') $mods *.jar /MIR /NFL /NDL /NJH /NJS /NP /XF 'zoocraft*.jar' 'SmartBrainLib*.jar' | Out-Null
Write-Host "Synced $((Get-ChildItem $mods -Filter *.jar).Count) mods into $mods"
