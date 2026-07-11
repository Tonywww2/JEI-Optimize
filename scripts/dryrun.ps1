#Requires -Version 5.1
<#
.SYNOPSIS
    Dry-run the Just Enough Threads CurseForge release pipeline. Builds every loader, uploads NOTHING.

.DESCRIPTION
    Runs `gradlew publishAllVersions -Ppublish.dryRun=true`, which exercises the whole publish
    flow (jar build, metadata, project id + token resolution) WITHOUT sending anything to
    CurseForge. Use it to gain confidence before running publish.ps1.

.PARAMETER JavaHome
    Optional path to a JDK 21 for the Gradle daemon (Stonecutter needs 21+). Omit to use
    $env:JAVA_HOME. Nothing machine-specific is baked in, so this is safe to commit.

.EXAMPLE
    .\scripts\dryrun.ps1
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

# Read the version straight from gradle.properties for the banner.
$versionLine = Get-Content 'gradle.properties' | Where-Object { $_ -match '^\s*mod\.version\s*=' } | Select-Object -First 1
$version = ($versionLine -replace '^\s*mod\.version\s*=\s*', '').Trim()

Write-Host "== Just Enough Threads DRY RUN (no upload) - version $version ==" -ForegroundColor Cyan

# Pass args as an array so PowerShell hands -Ppublish.dryRun=true to Gradle as a single token.
& .\gradlew.bat @('publishAllVersions', '-Ppublish.dryRun=true', '--console=plain')
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host "Dry run FAILED (exit $code)." -ForegroundColor Red
    exit $code
}
Write-Host "Dry run OK - nothing was uploaded. Run .\scripts\publish.ps1 to release." -ForegroundColor Green
