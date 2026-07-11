#Requires -Version 5.1
<#
.SYNOPSIS
    Build both loader jars (Forge 1.20.1 + NeoForge 1.21.1) and show where they landed.

.DESCRIPTION
    Runs `gradlew build`, which compiles and jars every Stonecutter node (both loaders) in one go,
    then lists the produced remapped jars. Use scripts/publish.ps1 to release them to CurseForge.

.PARAMETER JavaHome
    Optional path to a JDK 21 for the Gradle daemon (Stonecutter needs 21+). Omit to use
    $env:JAVA_HOME. Nothing machine-specific is baked in, so this is safe to commit.

.EXAMPLE
    .\scripts\build.ps1
#>
[CmdletBinding()]
param(
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

Write-Host "== Building all loaders (Forge 1.20.1 + NeoForge 1.21.1) ==" -ForegroundColor Cyan

# Pass args as an array so PowerShell hands each flag to Gradle as a single token.
& .\gradlew.bat @('build', '--console=plain')
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host "Build FAILED (exit $code)." -ForegroundColor Red
    exit $code
}

Write-Host ""
Write-Host "Built jars:" -ForegroundColor Green
Get-ChildItem -Path 'versions\*\build\libs\*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch 'sources' } |
    Sort-Object Name |
    ForEach-Object { "    {0}  ({1:N0} bytes)" -f $_.FullName.Replace("$repoRoot\", ''), $_.Length }
