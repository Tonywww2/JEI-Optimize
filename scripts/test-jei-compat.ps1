#Requires -Version 5.1
<#
.SYNOPSIS
    Launch the dev client against a specific JEI build and shut it down automatically.

.DESCRIPTION
    Starts `runClient` with quick-play into a world, watches run/logs/latest.log until JEI reports
    "Starting JEI took", waits a few more seconds so late log lines land, then kills the whole
    process tree. The client is always terminated: the kill lives in a `finally`, so a crash, a
    hang, a parse error or Ctrl-C still leaves no orphaned Minecraft window behind.

.PARAMETER JeiVersion
    JEI build to resolve at runtime, e.g. 15.48.0.179. Omit to use the version from gradle.properties.

.PARAMETER KeepConfig
    Do not overwrite run/config/jei_optimize-client.toml. Use this to test a hand-written config.

.EXAMPLE
    .\scripts\test-jei-compat.ps1 -JeiVersion 15.48.0.179

.EXAMPLE
    .\scripts\test-jei-compat.ps1 -Loader neoforge -KeepConfig
#>
[CmdletBinding()]
param(
    [string] $JeiVersion = "",
    [ValidateSet("forge", "neoforge")]
    [string] $Loader = "forge",
    [string] $World = "",
    [switch] $KeepConfig,
    [int] $TimeoutSeconds = 300,
    [int] $PostJeiWaitSeconds = 12,
    [string] $JavaHome = "C:\Program Files\Java\jdk-21"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

if ($Loader -eq "forge") {
    $GradleProject = ":1.20.1-forge"
    $McVersion = "1.20.1"
    # 1.20.1-forge is the active Stonecutter version, so Loom points it at the shared run dir.
    $RunDir = Join-Path $RepoRoot "run"
    if (-not $World) { $World = "sstt" }
} else {
    $GradleProject = ":1.21.1-neoforge"
    $McVersion = "1.21.1"
    $RunDir = Join-Path $RepoRoot "versions\1.21.1-neoforge\run"
    if (-not $World) { $World = "v121" }
}

$ConfigPath = Join-Path $RunDir "config\jei_optimize-client.toml"
$RunLogPath = Join-Path $RunDir "logs\latest.log"
$OutDir = Join-Path $RepoRoot "build\benchmarks\jei-compat"
$Tag = "$Loader-" + $(if ($JeiVersion) { $JeiVersion } else { "default" })
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Stop-ProcessTree($ProcessId) {
    foreach ($child in @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue)) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }
    try { Stop-Process -Id $ProcessId -Force -ErrorAction Stop } catch { }
}

function Read-LogShared($Path) {
    if (!(Test-Path $Path)) { return "" }
    try {
        $stream = [System.IO.File]::Open((Resolve-Path $Path).Path, 'Open', 'Read', 'ReadWrite')
        try {
            $reader = New-Object System.IO.StreamReader($stream)
            try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
        } finally { $stream.Dispose() }
    } catch [System.IO.IOException] { return "" }
}

if (-not $KeepConfig) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $ConfigPath) | Out-Null
    [System.IO.File]::WriteAllText($ConfigPath, "[general]`r`nenabled = true`r`n", $utf8NoBom)
}

for ($attempt = 0; $attempt -lt 60 -and (Test-Path $RunLogPath); $attempt++) {
    try { Remove-Item $RunLogPath -Force -ErrorAction Stop } catch { Start-Sleep -Milliseconds 500 }
}
if (Test-Path $RunLogPath) { throw "$RunLogPath is still locked; a previous client has not exited" }

$oldJavaHome = $env:JAVA_HOME
$oldPath = $env:Path
$process = $null
$status = "timeout"
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

try {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;$oldPath"

    $gradleArgs = "--no-daemon $GradleProject`:runClient --args=`"--quickPlaySingleplayer $World`""
    if ($JeiVersion) { $gradleArgs += " -Pjei.runtime.$McVersion=$JeiVersion" }

    Write-Host "running $Loader client (JEI $(if ($JeiVersion) { $JeiVersion } else { 'default' }), world '$World'), auto-exit within $TimeoutSeconds s ..."
    $process = Start-Process -FilePath (Join-Path $RepoRoot 'gradlew.bat') `
        -ArgumentList $gradleArgs -WorkingDirectory $RepoRoot -PassThru `
        -RedirectStandardOutput (Join-Path $OutDir "$Tag.stdout.log") `
        -RedirectStandardError (Join-Path $OutDir "$Tag.stderr.log")

    $hardDeadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $hardDeadline) {
        if ($process.HasExited) { $status = "process-exited"; break }

        $text = Read-LogShared $RunLogPath
        if ($text -match "Starting JEI took") {
            $status = "jei-started"
            $wait = [DateTime]::UtcNow.AddSeconds($PostJeiWaitSeconds)
            if ($wait -gt $hardDeadline) { $wait = $hardDeadline }
            while (!$process.HasExited -and [DateTime]::UtcNow -lt $wait) { [void] $process.WaitForExit(500) }
            break
        }
        if ($text -match "---- Minecraft Crash Report ----") { $status = "crashed"; break }

        [void] $process.WaitForExit(1000)
    }
} finally {
    if ($null -ne $process -and !$process.HasExited) {
        Stop-ProcessTree -ProcessId $process.Id
        [void] $process.WaitForExit(20000)
    }
    if (Test-Path $RunLogPath) { Copy-Item $RunLogPath (Join-Path $OutDir "$Tag.latest.log") -Force }
    $env:JAVA_HOME = $oldJavaHome
    $env:Path = $oldPath
}

Write-Host ("loader={0} jei={1} status={2} seconds={3} log={4}" -f `
    $Loader, $(if ($JeiVersion) { $JeiVersion } else { "default" }), $status,
    [int] $stopwatch.Elapsed.TotalSeconds, (Join-Path $OutDir "$Tag.latest.log"))

if ($status -ne "jei-started") { exit 1 }
