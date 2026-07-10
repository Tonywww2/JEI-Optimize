<#
.SYNOPSIS
  Measure Just Enough Threads JEI startup timings in an external Minecraft instance.

.DESCRIPTION
  Runs a before/after (A/B) comparison of JEI startup cost. The script does not launch
  Minecraft; it only writes the config profile and reads the instance log.

    1. -Action baseline   : write a config with the optimizations OFF (JEI stock) and timing ON.
    2. launch the instance, enter your test world, wait for the JEI item list to appear, quit.
    3. -Action report     : print the timings from the instance log (save the output).
    4. -Action optimized  : write a config with the optimizations ON and timing ON.
    5. repeat launch + report, then compare the two reports.

  Use the same instance, same world and the same hardware for both runs.

.EXAMPLE
  .\measure-optimizations.ps1 -InstanceDir 'D:\Instances\MyPack\.minecraft' -Action baseline
  .\measure-optimizations.ps1 -InstanceDir 'D:\Instances\MyPack\.minecraft' -Action report
#>
param(
    [Parameter(Mandatory = $true)]
    [string] $InstanceDir,

    [ValidateSet('baseline', 'optimized', 'report')]
    [string] $Action = 'report'
)

$ErrorActionPreference = 'Stop'
$resolved = (Resolve-Path $InstanceDir).Path
$configPath = Join-Path $resolved 'config\jei_optimize-client.toml'

function Write-Profile([bool] $optimized) {
    $flag = if ($optimized) { 'true' } else { 'false' }
    $text = @"
[general]
enabled = true

[diagnostics]
pluginTiming = true
registrationCounts = true

[async]
asyncIngredientFilter = $flag
parallelVanillaRecipes = $flag
"@
    $dir = Split-Path $configPath -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($configPath, $text, $utf8)

    $label = if ($optimized) { 'OPTIMIZED (asyncIngredientFilter + parallelVanillaRecipes ON)' } else { 'BASELINE (optimizations OFF)' }
    Write-Host "Wrote $label profile to:"
    Write-Host "  $configPath"
    Write-Host ''
    Write-Host 'Next: launch the instance, enter your test world, wait for the JEI item list to fill, then quit.'
    Write-Host "Then run: .\measure-optimizations.ps1 -InstanceDir `"$resolved`" -Action report"
}

function Clean([object] $match) {
    return ($match.Line -replace '^\[.*?\]\s\[.*?\]\s\[.*?\]:\s', '').Trim()
}

function Report {
    $log = $null
    foreach ($name in @('logs\latest.log', 'logs\debug.log')) {
        $candidate = Join-Path $resolved $name
        if (Test-Path $candidate) { $log = $candidate; break }
    }
    if (-not $log) {
        Write-Host "No logs\latest.log or logs\debug.log found under $resolved"
        Write-Host 'Make sure you launched the instance and entered a world after writing a profile.'
        return
    }

    Write-Host "Reading $log"
    Write-Host ''
    Write-Host '=== Ingredient filter ==='
    Select-String -Path $log -Pattern 'Building ingredient filter took|async ingredient filter build submitted|async ingredient filter build completed|async ingredient filter fell back' |
        ForEach-Object { Clean $_ }
    Write-Host ''
    Write-Host '=== Recipes (jei:minecraft) ==='
    Select-String -Path $log -Pattern "plugin phase 'Registering recipes' for jei:minecraft took|parallel crafting recipes|falling back to sequential|registration counts for jei:minecraft:" |
        ForEach-Object { Clean $_ }
    Write-Host ''
    Write-Host '=== JEI startup totals ==='
    Select-String -Path $log -Pattern 'Building runtime took|Starting JEI took' |
        ForEach-Object { Clean $_ }
    Write-Host ''
    Write-Host 'Compare baseline vs optimized:'
    Write-Host ' - "Building ingredient filter took" should drop to ~100 ms when optimized (built off-thread instead).'
    Write-Host ' - "async ingredient filter build completed ... after world entry" shows the off-thread build time.'
    Write-Host ' - "Registering recipes for jei:minecraft" should drop when optimized.'
    Write-Host ' - "registration counts for jei:minecraft: RECIPES=N" must be identical in both runs (correctness check).'
}

switch ($Action) {
    'baseline' { Write-Profile $false }
    'optimized' { Write-Profile $true }
    'report' { Report }
}
