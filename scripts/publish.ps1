#Requires -Version 5.1
<#
.SYNOPSIS
    Build every loader and PUBLISH the Just Enough Threads release to CurseForge (asks to confirm first).

.DESCRIPTION
    Reads the version from gradle.properties, shows exactly what will be uploaded, and only after
    you confirm runs `gradlew publishAllVersions`. The CurseForge API token is read from your
    USER-level ~/.gradle/gradle.properties (curseforge.token=...) or the CURSEFORGE_TOKEN env var;
    the numeric project id comes from gradle.properties (curseforge.projectId).

    Tip: run .\scripts\dryrun.ps1 first to validate the whole pipeline without uploading.

.PARAMETER Yes
    Skip the interactive confirmation (non-interactive / CI use).

.PARAMETER JavaHome
    Optional path to a JDK 21 for the Gradle daemon (Stonecutter needs 21+). Omit to use
    $env:JAVA_HOME. Nothing machine-specific is baked in, so this stays portable and CI-safe.

.EXAMPLE
    .\scripts\publish.ps1

.EXAMPLE
    .\scripts\publish.ps1 -Yes
#>
[CmdletBinding()]
param(
    [switch]$Yes,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'

# Always run from the repository root (the folder containing gradlew.bat), regardless of CWD.
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ($JavaHome) {
    if (-not (Test-Path -LiteralPath $JavaHome)) {
        throw "JavaHome '$JavaHome' does not exist. Pass a valid JDK 21 path or omit -JavaHome."
    }
    $env:JAVA_HOME = $JavaHome
}

function Get-Prop([string]$name) {
    $escaped = [regex]::Escape($name)
    $line = Get-Content 'gradle.properties' | Where-Object { $_ -match "^\s*$escaped\s*=" } | Select-Object -First 1
    if ($line) { return ($line -replace "^\s*$escaped\s*=\s*", '').Trim() }
    return $null
}

$modId     = Get-Prop 'mod.id'
$modName   = Get-Prop 'mod.name'
$version   = Get-Prop 'mod.version'
$projectId = Get-Prop 'curseforge.projectId'

if ([string]::IsNullOrWhiteSpace($version)) {
    Write-Host "Could not read mod.version from gradle.properties." -ForegroundColor Red
    exit 1
}

# Derive the loader files from the actual Stonecutter nodes (versions/<mc>-<loader>/) so the banner
# stays correct if a loader/version is added or removed - no hardcoded loader or MC version.
$nodes = @(Get-ChildItem -Path 'versions' -Directory -ErrorAction SilentlyContinue |
    Select-Object -ExpandProperty Name | Sort-Object)

Write-Host ""
Write-Host "  About to PUBLISH $modName $version to CurseForge (project $projectId):" -ForegroundColor Yellow
if ($nodes.Count -gt 0) {
    foreach ($node in $nodes) {
        $loader = $node.Substring($node.LastIndexOf('-') + 1)
        $mc     = $node.Substring(0, $node.LastIndexOf('-'))
        Write-Host "    - $modId-$loader-$version+$mc.jar"
    }
} else {
    Write-Host "    - every loader version"
}
Write-Host ""

if (-not $Yes) {
    $answer = Read-Host "Publish version $version to CurseForge now? [y/N]"
    if ($answer -notmatch '^(y|yes)$') {
        Write-Host "Aborted - nothing was uploaded." -ForegroundColor Red
        exit 1
    }
}

Write-Host "== Publishing $modName $version to CurseForge ==" -ForegroundColor Cyan

# Pass args as an array so PowerShell hands each flag to Gradle as a single token.
& .\gradlew.bat @('publishAllVersions', '--console=plain')
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host "Publish FAILED (exit $code)." -ForegroundColor Red
    exit $code
}
Write-Host "Published $modName $version to CurseForge successfully." -ForegroundColor Green
