param(
    [int] $Iterations = 3,
    [int] $WarmupIterations = 1,
    [string] $World = "sstt",
    [int] $TimeoutSeconds = 180,
    [int] $PostJeiWaitSeconds = 5,
    [string] $OutputDir = "build/benchmarks/jei-optimize",
    [string] $JavaHome = "C:\Program Files\Java\jdk-21"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir
$ConfigPath = Join-Path $RepoRoot "run\config\jei_optimize-client.toml"
$RunLogPath = Join-Path $RepoRoot "run\logs\latest.log"
$GradleBat = Join-Path $RepoRoot "gradlew.bat"
$ResolvedOutputDir = Join-Path $RepoRoot $OutputDir
$RawOutputDir = Join-Path $ResolvedOutputDir "raw"
$ResultsCsv = Join-Path $ResolvedOutputDir "results.csv"
$SummaryMarkdown = Join-Path $ResolvedOutputDir "summary.md"
$LatestDocsMarkdown = Join-Path $RepoRoot "docs\performance-results.md"

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Write-TextNoBom($Path, $Text) {
    $parent = Split-Path -Parent $Path
    if ($parent -and !(Test-Path $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Text, $Utf8NoBom)
}

function Read-TextIfExists($Path) {
    if (!(Test-Path $Path)) {
        return ""
    }
    $resolvedPath = (Resolve-Path $Path).Path
    try {
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

function New-JeiOptimizeConfig($Profile) {
    $enabled = if ($Profile -eq "optimized") { "true" } else { "false" }
    $feature = if ($Profile -eq "optimized") { "true" } else { "false" }
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
    switch -Regex ($Unit) {
        "^s$" { return $number * 1000.0 }
        "^ms$" { return $number }
        "^(us|μs|µs)$" { return $number / 1000.0 }
        "^ns$" { return $number / 1000000.0 }
        default { return $number }
    }
}

function Match-DurationMs($Text, $Pattern) {
    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (!$match.Success) {
        return $null
    }
    return Convert-DurationToMs $match.Groups[1].Value $match.Groups[2].Value
}

function Match-Integer($Text, $Pattern) {
    $match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (!$match.Success) {
        return $null
    }
    return [int] $match.Groups[1].Value
}

function Stop-ProcessTree($ProcessId) {
    $children = @(Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue)
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }
    try {
        Stop-Process -Id $ProcessId -Force -ErrorAction Stop
    } catch {
    }
}

function Start-BenchmarkRun($Profile, $Iteration, [bool] $Warmup) {
    $label = if ($Warmup) { "warmup" } else { "measure" }
    $runId = "{0}-{1}-{2}" -f $Profile, $label, $Iteration
    $stdoutPath = Join-Path $RawOutputDir "$runId.stdout.log"
    $stderrPath = Join-Path $RawOutputDir "$runId.stderr.log"
    $latestCopyPath = Join-Path $RawOutputDir "$runId.latest.log"

    Write-TextNoBom $ConfigPath (New-JeiOptimizeConfig $Profile)
    if (Test-Path $RunLogPath) {
        Remove-Item $RunLogPath -Force
    }

    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:Path
    try {
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;$oldPath"
        $gradleArguments = "--no-daemon runClient --args=`"--quickPlaySingleplayer $World`""
        $process = Start-Process -FilePath $GradleBat `
            -ArgumentList $gradleArguments `
            -WorkingDirectory $RepoRoot `
            -RedirectStandardOutput $stdoutPath `
            -RedirectStandardError $stderrPath `
            -PassThru

        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $metricsCaptured = $false
        $status = "timeout"
        $exitCode = $null

        while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
            if ($process.HasExited) {
                $exitCode = $process.ExitCode
                $status = if ($metricsCaptured) { "completed" } else { "process-exited" }
                break
            }

            $logText = Read-TextIfExists $RunLogPath
            if (!$metricsCaptured -and $logText -match "Starting JEI took") {
                $metricsCaptured = $true
                $status = "metrics-captured"
                $metricWallMs = [math]::Round($stopwatch.Elapsed.TotalMilliseconds, 0)
                $deadline = [DateTime]::UtcNow.AddSeconds($PostJeiWaitSeconds)
                while (!$process.HasExited -and [DateTime]::UtcNow -lt $deadline) {
                    [void] $process.WaitForExit(500)
                }
                if (!$process.HasExited) {
                    Stop-ProcessTree -ProcessId $process.Id
                    [void] $process.WaitForExit(10000)
                    $exitCode = $process.ExitCode
                } else {
                    $exitCode = $process.ExitCode
                }
                break
            }

            [void] $process.WaitForExit(500)
        }

        if (!$process.HasExited) {
            Stop-ProcessTree -ProcessId $process.Id
            [void] $process.WaitForExit(10000)
            $exitCode = $process.ExitCode
        }

        $finalLog = Read-TextIfExists $RunLogPath
        if (Test-Path $RunLogPath) {
            Copy-Item $RunLogPath $latestCopyPath -Force
        }

        if (!(Get-Variable -Name metricWallMs -Scope Local -ErrorAction SilentlyContinue)) {
            $metricWallMs = $null
        }

        return [pscustomobject]@{
            Profile = $Profile
            Iteration = $Iteration
            Warmup = $Warmup
            Status = $status
            ExitCode = $exitCode
            WallToJeiMs = $metricWallMs
            JeiTotalMs = Match-DurationMs $finalLog "Starting JEI took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            RegisteringIngredientsMs = Match-DurationMs $finalLog "Registering ingredients took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            RegisteringCategoriesMs = Match-DurationMs $finalLog "Registering categories took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            RegisteringRecipesMs = Match-DurationMs $finalLog "Registering recipes took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            BuildingRecipeRegistryMs = Match-DurationMs $finalLog "Building recipe registry took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            BuildingIngredientListMs = Match-DurationMs $finalLog "Building ingredient list took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            BuildingIngredientFilterMs = Match-DurationMs $finalLog "Building ingredient filter took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            RegisteringRuntimeMs = Match-DurationMs $finalLog "Registering Runtime took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            BuildingRuntimeMs = Match-DurationMs $finalLog "Building runtime took\s+([0-9.]+)\s*(s|ms|us|μs|µs|ns)"
            IngredientsAdded = Match-Integer $finalLog "Added\s+(\d+)\s+ingredients"
            JeiStarted = [bool]($finalLog -match "JEI StartEventObserver transitioning state from ENABLED to JEI_STARTED")
            LogFile = $latestCopyPath
            StdoutFile = $stdoutPath
            StderrFile = $stderrPath
        }
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:Path = $oldPath
    }
}

function Average($Values) {
    $valid = @($Values | Where-Object { $null -ne $_ })
    if ($valid.Count -eq 0) { return $null }
    return ($valid | Measure-Object -Average).Average
}

function Median($Values) {
    $valid = @($Values | Where-Object { $null -ne $_ } | Sort-Object)
    if ($valid.Count -eq 0) { return $null }
    $middle = [int] [math]::Floor($valid.Count / 2)
    if ($valid.Count % 2 -eq 1) {
        return [double] $valid[$middle]
    }
    return ([double] $valid[$middle - 1] + [double] $valid[$middle]) / 2.0
}

function Format-Number($Value) {
    if ($null -eq $Value) { return "n/a" }
    return ([double] $Value).ToString("0.###", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Summarize($Rows, $Profile) {
    $measured = @($Rows | Where-Object { $_.Profile -eq $Profile -and !$_.Warmup })
    return [pscustomobject]@{
        Profile = $Profile
        Runs = $measured.Count
        JeiTotalMeanMs = Average ($measured | ForEach-Object { $_.JeiTotalMs })
        JeiTotalMedianMs = Median ($measured | ForEach-Object { $_.JeiTotalMs })
        WallToJeiMeanMs = Average ($measured | ForEach-Object { $_.WallToJeiMs })
        IngredientFilterMeanMs = Average ($measured | ForEach-Object { $_.BuildingIngredientFilterMs })
        RuntimeMeanMs = Average ($measured | ForEach-Object { $_.BuildingRuntimeMs })
        RegisteringRuntimeMeanMs = Average ($measured | ForEach-Object { $_.RegisteringRuntimeMs })
        IngredientsAdded = ($measured | Select-Object -First 1).IngredientsAdded
    }
}

function DifferenceLine($Name, $Baseline, $Optimized) {
    $base = $Baseline.$Name
    $opt = $Optimized.$Name
    if ($null -eq $base -or $null -eq $opt) {
        return "| $Name | n/a | n/a | n/a | n/a |"
    }
    $delta = $opt - $base
    $percent = if ([math]::Abs($base) -lt 0.0001) { $null } else { ($delta / $base) * 100.0 }
    return "| $Name | $(Format-Number $base) | $(Format-Number $opt) | $(Format-Number $delta) | $(Format-Number $percent)% |"
}

if (!(Test-Path $GradleBat)) {
    throw "gradlew.bat not found at $GradleBat"
}
if (!(Test-Path $JavaHome)) {
    throw "JavaHome not found: $JavaHome"
}

New-Item -ItemType Directory -Force -Path $RawOutputDir | Out-Null

$originalConfigBytes = $null
$configExisted = Test-Path $ConfigPath
if ($configExisted) {
    $originalConfigBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $ConfigPath).Path)
}

$rows = New-Object System.Collections.Generic.List[object]
try {
    foreach ($profile in @("baseline", "optimized")) {
        for ($warmup = 1; $warmup -le $WarmupIterations; $warmup++) {
            Write-Host "[$profile] warmup $warmup/$WarmupIterations"
            $rows.Add((Start-BenchmarkRun -Profile $profile -Iteration $warmup -Warmup $true))
        }
    }

    for ($iteration = 1; $iteration -le $Iterations; $iteration++) {
        foreach ($profile in @("baseline", "optimized")) {
            Write-Host "[$profile] measurement $iteration/$Iterations"
            $rows.Add((Start-BenchmarkRun -Profile $profile -Iteration $iteration -Warmup $false))
        }
    }
} finally {
    if ($configExisted) {
        [System.IO.File]::WriteAllBytes($ConfigPath, $originalConfigBytes)
    } elseif (Test-Path $ConfigPath) {
        Remove-Item $ConfigPath -Force
    }
}

$rows | Export-Csv -Path $ResultsCsv -NoTypeInformation -Encoding UTF8

$baselineSummary = Summarize $rows "baseline"
$optimizedSummary = Summarize $rows "optimized"
$timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
$javaVersion = (cmd /c "`"$JavaHome\bin\java.exe`" -version 2>&1" | Select-Object -First 1)
$failedRows = @($rows | Where-Object { !$_.JeiStarted -or $null -eq $_.JeiTotalMs })

$markdown = New-Object System.Collections.Generic.List[string]
$markdown.Add("# JEI Optimize Performance Benchmark Results")
$markdown.Add("")
$markdown.Add("Generated: $timestamp")
$markdown.Add("")
$markdown.Add("## Protocol")
$markdown.Add("")
$markdown.Add("- Command: ``.\\gradlew.bat --no-daemon runClient --args=`"--quickPlaySingleplayer $World`"``")
$markdown.Add("- Runtime mods: JEI, Mekanism, CoFH Core, Thermal Series core, Thermal Foundation, Farmer's Delight.")
$markdown.Add("- Baseline profile: ``general.enabled=false``; diagnostics disabled.")
$markdown.Add("- Optimized profile: ``general.enabled=true`` with all sync and async optimization flags enabled; diagnostics disabled.")
$markdown.Add("- Measurement point: JEI's own ``Starting JEI took`` log line after quick-playing into world ``$World``.")
$markdown.Add("- Warmup runs per profile: $WarmupIterations. Measured runs per profile: $Iterations.")
$markdown.Add("- Java: ``$javaVersion``")
$markdown.Add("")
$markdown.Add("## Summary")
$markdown.Add("")
$markdown.Add("| Metric | Baseline mean ms | Optimized mean ms | Delta ms | Delta percent |")
$markdown.Add("| --- | ---: | ---: | ---: | ---: |")
$markdown.Add((DifferenceLine "JeiTotalMeanMs" $baselineSummary $optimizedSummary))
$markdown.Add((DifferenceLine "WallToJeiMeanMs" $baselineSummary $optimizedSummary))
$markdown.Add((DifferenceLine "IngredientFilterMeanMs" $baselineSummary $optimizedSummary))
$markdown.Add((DifferenceLine "RuntimeMeanMs" $baselineSummary $optimizedSummary))
$markdown.Add((DifferenceLine "RegisteringRuntimeMeanMs" $baselineSummary $optimizedSummary))
$markdown.Add("")
$markdown.Add("Positive delta means the optimized profile was slower; negative delta means it was faster.")
$markdown.Add("")
$markdown.Add("## Per-Run Data")
$markdown.Add("")
$markdown.Add("| Profile | Iteration | Warmup | Status | JEI total ms | Wall to JEI ms | Ingredient filter ms | Runtime ms | Ingredients |")
$markdown.Add("| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |")
foreach ($row in $rows) {
    $markdown.Add("| $($row.Profile) | $($row.Iteration) | $($row.Warmup) | $($row.Status) | $(Format-Number $row.JeiTotalMs) | $(Format-Number $row.WallToJeiMs) | $(Format-Number $row.BuildingIngredientFilterMs) | $(Format-Number $row.BuildingRuntimeMs) | $(Format-Number $row.IngredientsAdded) |")
}
$markdown.Add("")
$markdown.Add("## Artifacts")
$markdown.Add("")
$markdown.Add("- CSV: ``$ResultsCsv``")
$markdown.Add("- Raw logs: ``$RawOutputDir``")
if ($failedRows.Count -gt 0) {
    $markdown.Add("")
    $markdown.Add("## Warnings")
    $markdown.Add("")
    $markdown.Add("$($failedRows.Count) run(s) did not report JEI startup metrics. Inspect the raw logs before trusting the summary.")
}

$markdownText = ($markdown -join "`r`n") + "`r`n"
Write-TextNoBom $SummaryMarkdown $markdownText
Write-TextNoBom $LatestDocsMarkdown $markdownText

Write-Host "Results written to $SummaryMarkdown"
Write-Host "CSV written to $ResultsCsv"