param(
    [Parameter(Mandatory = $true)]
    [string] $InstanceDir,

    [ValidateSet("baseline", "optimized", "observe")]
    [string] $Profile = "observe",

    [int] $TimeoutMinutes = 10,
    [int] $PollSeconds = 1,
    [int] $PostMetricSeconds = 5,
    [string] $OutputRoot = "",
    [switch] $ReadExistingLog,
    [switch] $NoRestoreConfig,
    [switch] $NoZip
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$CollectorVersion = "1"

function Write-TextNoBom($Path, $Text) {
    $parent = Split-Path -Parent $Path
    if ($parent -and !(Test-Path $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Text, $Utf8NoBom)
}

function Read-TextShared($Path) {
    if (!(Test-Path $Path)) {
        return ""
    }
    try {
        $resolvedPath = (Resolve-Path $Path).Path
        $stream = [System.IO.File]::Open($resolvedPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
            try {
                return $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    } catch [System.IO.IOException] {
        return ""
    }
}

function Read-LogSince($Path, [ref] $Offset) {
    if (!(Test-Path $Path)) {
        return ""
    }
    try {
        $resolvedPath = (Resolve-Path $Path).Path
        $stream = [System.IO.File]::Open($resolvedPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            if ($Offset.Value -gt $stream.Length) {
                $Offset.Value = 0
            }
            [void] $stream.Seek($Offset.Value, [System.IO.SeekOrigin]::Begin)
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
            try {
                $text = $reader.ReadToEnd()
                $Offset.Value = $stream.Position
                return $text
            } finally {
                $reader.Dispose()
            }
        } finally {
            $stream.Dispose()
        }
    } catch [System.IO.IOException] {
        return ""
    }
}

function Copy-FileShared($Source, $Destination) {
    if (!(Test-Path $Source)) {
        return
    }
    $parent = Split-Path -Parent $Destination
    if ($parent -and !(Test-Path $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    $sourceStream = [System.IO.File]::Open((Resolve-Path $Source).Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        $destinationStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
        try {
            $sourceStream.CopyTo($destinationStream)
        } finally {
            $destinationStream.Dispose()
        }
    } finally {
        $sourceStream.Dispose()
    }
}

function New-JeiOptimizeConfig($ProfileName) {
    $enabled = if ($ProfileName -eq "optimized") { "true" } else { "false" }
    $feature = if ($ProfileName -eq "optimized") { "true" } else { "false" }
    return @"
[general]
enabled = $enabled

[diagnostics]
pluginTiming = false
registrationCounts = false

[syncOptimizations]
cacheScope = $feature
batchIngredientFilterInit = $feature
sortKeyCache = $feature
delayCompact = $feature

[async]
searchPreheat = $feature
snapshotChunking = $feature
sortPreheat = $feature
recipeFocusPreheat = $feature
catalystPreheat = $feature
workerThreads = 2
snapshotBudgetMs = 2
"@
}

function Convert-DurationToMs($Value, $Unit) {
    $number = [double]::Parse($Value, [System.Globalization.CultureInfo]::InvariantCulture)
    $normalized = $Unit.ToLowerInvariant()
    if ($normalized -eq "s") {
        return $number * 1000.0
    }
    if ($normalized -eq "ms") {
        return $number
    }
    if ($normalized -eq "ns") {
        return $number / 1000000.0
    }
    return $number / 1000.0
}

function Match-LastDurationMs($Text, $Pattern) {
    $matches = [regex]::Matches($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -eq 0) {
        return $null
    }
    $match = $matches[$matches.Count - 1]
    return Convert-DurationToMs $match.Groups[1].Value $match.Groups[2].Value
}

function Match-LastInteger($Text, $Pattern) {
    $matches = [regex]::Matches($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -eq 0) {
        return $null
    }
    return [int] $matches[$matches.Count - 1].Groups[1].Value
}

function Get-JeiMetrics($Text) {
    return [pscustomobject]@{
        JeiStarted = [bool]($Text -match "JEI StartEventObserver transitioning state from ENABLED to JEI_STARTED")
        JeiTotalMs = Match-LastDurationMs $Text "Starting JEI took\s+([0-9.]+)\s*(\S+)"
        RegisteringIngredientsMs = Match-LastDurationMs $Text "Registering ingredients took\s+([0-9.]+)\s*(\S+)"
        RegisteringCategoriesMs = Match-LastDurationMs $Text "Registering categories took\s+([0-9.]+)\s*(\S+)"
        RegisteringRecipesMs = Match-LastDurationMs $Text "Registering recipes took\s+([0-9.]+)\s*(\S+)"
        BuildingRecipeRegistryMs = Match-LastDurationMs $Text "Building recipe registry took\s+([0-9.]+)\s*(\S+)"
        BuildingIngredientListMs = Match-LastDurationMs $Text "Building ingredient list took\s+([0-9.]+)\s*(\S+)"
        BuildingIngredientFilterMs = Match-LastDurationMs $Text "Building ingredient filter took\s+([0-9.]+)\s*(\S+)"
        RegisteringRuntimeMs = Match-LastDurationMs $Text "Registering Runtime took\s+([0-9.]+)\s*(\S+)"
        BuildingRuntimeMs = Match-LastDurationMs $Text "Building runtime took\s+([0-9.]+)\s*(\S+)"
        IngredientsAdded = Match-LastInteger $Text "Added\s+(\d+)\s+ingredients"
        ErrorLines = [regex]::Matches($Text, "\[(?:[^\]]+/)?(?:ERROR|FATAL)\]").Count
        CrashMentioned = [bool]($Text -match "Crash Report|Game crashed|crash-reports")
    }
}

function Format-Number($Value) {
    if ($null -eq $Value) {
        return "n/a"
    }
    return ([double] $Value).ToString("0.###", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-JavaFromPath() {
    try {
        return @(cmd /c "java -version 2>&1") -join "`n"
    } catch {
        return "java -version failed: $($_.Exception.Message)"
    }
}

function Export-ModManifest($ModsDir, $Destination) {
    $rows = New-Object System.Collections.Generic.List[object]
    if (Test-Path $ModsDir) {
        $modFiles = @(Get-ChildItem $ModsDir -File -Recurse -ErrorAction SilentlyContinue | Where-Object { $_.Extension -in @(".jar", ".disabled") })
        foreach ($file in $modFiles) {
            $hash = ""
            try {
                if ($file.Extension -eq ".jar") {
                    $hash = (Get-FileHash $file.FullName -Algorithm SHA256).Hash
                }
            } catch {
                $hash = "hash-failed: $($_.Exception.Message)"
            }
            $rows.Add([pscustomobject]@{
                Name = $file.Name
                RelativePath = $file.FullName.Substring($ModsDir.Length).TrimStart("\", "/")
                Length = $file.Length
                LastWriteTimeUtc = $file.LastWriteTimeUtc.ToString("o")
                Sha256 = $hash
            })
        }
    }
    $rows | Export-Csv -Path $Destination -NoTypeInformation -Encoding UTF8
    return $rows.Count
}

function Copy-IfExists($Source, $Destination) {
    if (Test-Path $Source) {
        Copy-FileShared $Source $Destination
    }
}

$ResolvedInstanceDir = (Resolve-Path $InstanceDir).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $ResolvedInstanceDir "jei-optimize-external-results"
}
$OutputRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputRoot)
$RunStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$OutputDir = Join-Path $OutputRoot "$RunStamp-$Profile"
$RawDir = Join-Path $OutputDir "raw"
$ConfigDir = Join-Path $OutputDir "config"
$CrashDir = Join-Path $OutputDir "crash-reports"
$ConfigPath = Join-Path $ResolvedInstanceDir "config\jei_optimize-client.toml"
$LatestLogPath = Join-Path $ResolvedInstanceDir "logs\latest.log"
$DebugLogPath = Join-Path $ResolvedInstanceDir "logs\debug.log"
$ModsDir = Join-Path $ResolvedInstanceDir "mods"

New-Item -ItemType Directory -Force -Path $RawDir, $ConfigDir, $CrashDir | Out-Null

$originalConfigBytes = $null
$configExisted = Test-Path $ConfigPath
if ($configExisted) {
    $originalConfigBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $ConfigPath).Path)
    Copy-IfExists $ConfigPath (Join-Path $ConfigDir "jei_optimize-client.before.toml")
}

try {
    if ($Profile -ne "observe") {
        Write-TextNoBom $ConfigPath (New-JeiOptimizeConfig $Profile)
        Copy-IfExists $ConfigPath (Join-Path $ConfigDir "jei_optimize-client.applied.toml")
        Write-Host "Applied JEI Optimize profile '$Profile' to $ConfigPath"
    } else {
        Write-Host "Observe mode: existing JEI Optimize config is left unchanged."
    }

    Write-Host "Start the external Minecraft instance now, enter a world, and wait for JEI to finish starting."
    Write-Host "Watching $LatestLogPath for up to $TimeoutMinutes minute(s)."

    $offset = 0L
    if ((Test-Path $LatestLogPath) -and !$ReadExistingLog) {
        $offset = (Get-Item $LatestLogPath).Length
    }
    $captured = New-Object System.Text.StringBuilder
    if ($ReadExistingLog) {
        $existingLogText = Read-TextShared $LatestLogPath
        [void] $captured.Append($existingLogText)
    }

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $timeout = [TimeSpan]::FromMinutes($TimeoutMinutes)
    $status = "timeout"
    $metricWallMs = $null
    $metrics = Get-JeiMetrics $captured.ToString()

    while ($stopwatch.Elapsed -lt $timeout) {
        $newText = Read-LogSince $LatestLogPath ([ref] $offset)
        if ($newText.Length -gt 0) {
            [void] $captured.Append($newText)
            $metrics = Get-JeiMetrics $captured.ToString()
            if ($metrics.JeiStarted -and $null -ne $metrics.JeiTotalMs) {
                $metricWallMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 0)
                $status = "metrics-captured"
                if ($PostMetricSeconds -gt 0) {
                    [System.Threading.Thread]::Sleep($PostMetricSeconds * 1000)
                }
                break
            }
        }
        [System.Threading.Thread]::Sleep([Math]::Max(1, $PollSeconds) * 1000)
    }

    $capturedText = $captured.ToString()
    $metrics = Get-JeiMetrics $capturedText
    Write-TextNoBom (Join-Path $RawDir "captured-log-segment.log") $capturedText
    Copy-IfExists $LatestLogPath (Join-Path $RawDir "latest.log")
    Copy-IfExists $DebugLogPath (Join-Path $RawDir "debug.log")
    Copy-IfExists $ConfigPath (Join-Path $ConfigDir "jei_optimize-client.after.toml")

    $jeiConfigs = @(Get-ChildItem (Join-Path $ResolvedInstanceDir "config") -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "jei*" })
    foreach ($configFile in $jeiConfigs) {
        Copy-IfExists $configFile.FullName (Join-Path $ConfigDir $configFile.Name)
    }

    $packMetadataNames = @("manifest.json", "minecraftinstance.json", "mmc-pack.json", "instance.cfg")
    foreach ($metadataName in $packMetadataNames) {
        Copy-IfExists (Join-Path $ResolvedInstanceDir $metadataName) (Join-Path $OutputDir $metadataName)
    }

    $crashReportsPath = Join-Path $ResolvedInstanceDir "crash-reports"
    if (Test-Path $crashReportsPath) {
        $crashes = @(Get-ChildItem $crashReportsPath -File -Filter "*.txt" -ErrorAction SilentlyContinue | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 5)
        foreach ($crash in $crashes) {
            Copy-IfExists $crash.FullName (Join-Path $CrashDir $crash.Name)
        }
    }

    $modCount = Export-ModManifest $ModsDir (Join-Path $OutputDir "mods-manifest.csv")

    $os = $null
    $cpu = $null
    $memoryBytes = $null
    try { $os = Get-CimInstance Win32_OperatingSystem } catch {}
    try { $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1 } catch {}
    if ($null -ne $os) { $memoryBytes = [uint64] $os.TotalVisibleMemorySize * 1024 }

    $environment = [ordered]@{
        CollectorVersion = $CollectorVersion
        Timestamp = (Get-Date).ToString("o")
        InstanceDir = $ResolvedInstanceDir
        Profile = $Profile
        Status = $status
        TimeoutMinutes = $TimeoutMinutes
        PostMetricSeconds = $PostMetricSeconds
        MachineName = $env:COMPUTERNAME
        UserDomain = $env:USERDOMAIN
        OS = if ($null -ne $os) { $os.Caption } else { "unknown" }
        OSVersion = if ($null -ne $os) { $os.Version } else { "unknown" }
        CpuName = if ($null -ne $cpu) { $cpu.Name } else { "unknown" }
        LogicalProcessors = if ($null -ne $cpu) { $cpu.NumberOfLogicalProcessors } else { $null }
        TotalMemoryBytes = $memoryBytes
        PowerShellVersion = $PSVersionTable.PSVersion.ToString()
        JavaFromPath = Get-JavaFromPath
        ModCount = $modCount
    }
    Write-TextNoBom (Join-Path $OutputDir "environment.json") (($environment | ConvertTo-Json -Depth 5) + "`r`n")

    $metricRecord = [ordered]@{
        Profile = $Profile
        Status = $status
        WallToJeiMs = $metricWallMs
        JeiStarted = $metrics.JeiStarted
        JeiTotalMs = $metrics.JeiTotalMs
        RegisteringIngredientsMs = $metrics.RegisteringIngredientsMs
        RegisteringCategoriesMs = $metrics.RegisteringCategoriesMs
        RegisteringRecipesMs = $metrics.RegisteringRecipesMs
        BuildingRecipeRegistryMs = $metrics.BuildingRecipeRegistryMs
        BuildingIngredientListMs = $metrics.BuildingIngredientListMs
        BuildingIngredientFilterMs = $metrics.BuildingIngredientFilterMs
        RegisteringRuntimeMs = $metrics.RegisteringRuntimeMs
        BuildingRuntimeMs = $metrics.BuildingRuntimeMs
        IngredientsAdded = $metrics.IngredientsAdded
        ErrorLines = $metrics.ErrorLines
        CrashMentioned = $metrics.CrashMentioned
    }
    Write-TextNoBom (Join-Path $OutputDir "metrics.json") (($metricRecord | ConvertTo-Json -Depth 5) + "`r`n")
    [pscustomobject]$metricRecord | Export-Csv -Path (Join-Path $OutputDir "metrics.csv") -NoTypeInformation -Encoding UTF8

    $summary = New-Object System.Collections.Generic.List[string]
    $summary.Add("# JEI Optimize External Collection")
    $summary.Add("")
    $summary.Add("Generated: $((Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz"))")
    $summary.Add("")
    $summary.Add("## Run")
    $summary.Add("")
    $summary.Add("- Profile: ``$Profile``")
    $summary.Add("- Status: ``$status``")
    $summary.Add("- Instance: ``$ResolvedInstanceDir``")
    $summary.Add("- Mods found: $modCount")
    $summary.Add("")
    $summary.Add("## Metrics")
    $summary.Add("")
    $summary.Add("| Metric | Value |")
    $summary.Add("| --- | ---: |")
    $summary.Add("| JEI started | $($metrics.JeiStarted) |")
    $summary.Add("| JEI total ms | $(Format-Number $metrics.JeiTotalMs) |")
    $summary.Add("| Wall to JEI ms | $(Format-Number $metricWallMs) |")
    $summary.Add("| Building ingredient filter ms | $(Format-Number $metrics.BuildingIngredientFilterMs) |")
    $summary.Add("| Building runtime ms | $(Format-Number $metrics.BuildingRuntimeMs) |")
    $summary.Add("| Registering runtime ms | $(Format-Number $metrics.RegisteringRuntimeMs) |")
    $summary.Add("| Ingredients added | $(Format-Number $metrics.IngredientsAdded) |")
    $summary.Add("| Error/FATAL log markers | $($metrics.ErrorLines) |")
    $summary.Add("| Crash mentioned | $($metrics.CrashMentioned) |")
    $summary.Add("")
    $summary.Add("## Files")
    $summary.Add("")
    $summary.Add("- ``metrics.json`` and ``metrics.csv``")
    $summary.Add("- ``environment.json``")
    $summary.Add("- ``mods-manifest.csv``")
    $summary.Add("- ``raw/latest.log`` and ``raw/captured-log-segment.log``")
    $summary.Add("- ``config/`` JEI-related config snapshots")
    $summary.Add("- ``crash-reports/`` latest crash reports, when present")
    if ($status -ne "metrics-captured") {
        $summary.Add("")
        $summary.Add("## Warning")
        $summary.Add("")
        $summary.Add("JEI startup metrics were not captured before timeout. Inspect raw logs and crash reports.")
    }
    Write-TextNoBom (Join-Path $OutputDir "summary.md") (($summary -join "`r`n") + "`r`n")

    if (!$NoZip) {
        $zipPath = "$OutputDir.zip"
        if (Test-Path $zipPath) {
            Remove-Item $zipPath -Force
        }
        Compress-Archive -Path (Join-Path $OutputDir "*") -DestinationPath $zipPath -Force
        Write-Host "Data package written to $zipPath"
    }
    Write-Host "Collection written to $OutputDir"
    if ($status -ne "metrics-captured") {
        exit 2
    }
} finally {
    if ($Profile -ne "observe" -and !$NoRestoreConfig) {
        if ($configExisted) {
            [System.IO.File]::WriteAllBytes($ConfigPath, $originalConfigBytes)
            Write-Host "Restored original JEI Optimize config."
        } elseif (Test-Path $ConfigPath) {
            Remove-Item $ConfigPath -Force
            Write-Host "Removed generated JEI Optimize config."
        }
    }
}