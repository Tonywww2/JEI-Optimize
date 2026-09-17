param(
    [string] $InstanceDir = 'D:\Prism\instances\All the Mods 9 - ATM9\minecraft',
    [string] $Launcher = 'D:\PrismLauncher\prismlauncher.exe',
    [string] $World = 'New World',
    [ValidateSet('jet-off', 'off', 'on')][string] $Profile = 'off',
    [string] $Label = 'warmup',
    [switch] $ManualLaunch,
    [ValidateRange(30, 600)][int] $LaunchTimeoutSeconds = 120,
    [int] $TimeoutMinutes = 25
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$instance = (Resolve-Path $InstanceDir).Path
$instanceRoot = Split-Path -Parent $instance
$instanceId = Split-Path -Leaf $instanceRoot
$output = Join-Path $root "build\benchmarks\tooltip-pack\$Label-$Profile"
if (Test-Path $output) { throw "Run already exists: $output" }
$configPath = Join-Path $instance 'config\justenoughthreads-client.toml'
$launcherConfigPath = Join-Path $instanceRoot 'instance.cfg'
$optionsPath = Join-Path $instance 'options.txt'
$utf8 = New-Object System.Text.UTF8Encoding($false)

function Set-SectionValue([string] $Text, [string] $Section, [string] $Key, [string] $Value) {
    $sectionPattern = '(?ms)^\[' + [regex]::Escape($Section) + '\]\s*\r?\n(?<body>.*?)(?=^\[|\z)'
    $sectionMatch = [regex]::Match($Text, $sectionPattern)
    if (!$sectionMatch.Success) { throw "Missing section: $Section" }
    $body = $sectionMatch.Groups['body'].Value
    $keyPattern = '(?m)^\s*' + [regex]::Escape($Key) + '\s*=.*$'
    $replacement = "$Key=$Value"
    if ([regex]::IsMatch($body, $keyPattern)) {
        $body = [regex]::Replace($body, $keyPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement })
    } else {
        $body = $body.TrimEnd() + "`r`n$replacement`r`n`r`n"
    }
    return $Text.Substring(0, $sectionMatch.Groups['body'].Index) + $body + $Text.Substring($sectionMatch.Groups['body'].Index + $sectionMatch.Groups['body'].Length)
}

function Read-Shared([string] $Path) {
    if (!(Test-Path $Path)) { return '' }
    try {
        $stream = [IO.File]::Open($Path, 'Open', 'Read', 'ReadWrite')
        try {
            $reader = New-Object IO.StreamReader($stream)
            try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
        } finally { $stream.Dispose() }
    } catch [IO.IOException] { return '' }
}

function Restore-TestValues([string] $Path, [string] $Original, [string] $Applied, [string[]] $Keys) {
    $current = [IO.File]::ReadAllText($Path)
    foreach ($key in $Keys) {
        $pattern = '(?m)^\s*' + [regex]::Escape($key) + '\s*=.*$'
        $before = [regex]::Match($Original, $pattern)
        $test = [regex]::Match($Applied, $pattern)
        $now = [regex]::Match($current, $pattern)
        $testValue = $test.Value.Trim()
        $currentValue = $now.Value.Trim()
        if ($key -eq 'JvmArgs' -and $test.Success -and $now.Success) {
            $testValue = $testValue.Substring($testValue.IndexOf('=') + 1).Trim().Trim('"')
            $currentValue = $currentValue.Substring($currentValue.IndexOf('=') + 1).Trim().Trim('"')
        }
        if ($test.Success -and $now.Success -and $testValue -eq $currentValue) {
            $replacement = if ($before.Success) { $before.Value } else { '' }
            $current = $current.Substring(0, $now.Index) + $replacement + $current.Substring($now.Index + $now.Length)
        } elseif ($now.Value -ne $before.Value) {
            Write-Warning "Preserving concurrent change to $key in $Path"
        }
    }
    [IO.File]::WriteAllText($Path, $current, $utf8)
}

function Get-RecordingPath([string] $CommandLine) {
    $match = [regex]::Match($CommandLine, '(?i)filename=(?<path>[^"\r\n]+?\.jfr)(?=[",\s]|$)')
    if (!$match.Success) { return '' }
    return [IO.Path]::GetFullPath($match.Groups['path'].Value)
}

$existingGames = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -in @('java.exe', 'javaw.exe') -and $_.CommandLine -match 'org.prismlauncher.EntryPoint|BootstrapLauncher|forgeclient'
})
if ($existingGames.Count) { throw 'Close running Minecraft clients before measurement.' }
if (Get-Process -Name prismlauncher -ErrorAction SilentlyContinue) {
    throw 'Exit Prism completely before preparation: an open launcher may reuse cached JVM arguments and overwrite an earlier JFR.'
}
New-Item -ItemType Directory -Path $output -Force | Out-Null
$config = [IO.File]::ReadAllText($configPath)
$originalConfig = $config
$config = Set-SectionValue $config 'general' 'enabled' $(if ($Profile -eq 'jet-off') { 'false' } else { 'true' })
$config = Set-SectionValue $config 'async' 'tooltipSearchIndex' $(if ($Profile -eq 'on') { 'true' } else { 'false' })
$config = Set-SectionValue $config 'diagnostics' 'tooltipSearchMetrics' 'false'
$prism = [IO.File]::ReadAllText($launcherConfigPath)
$originalPrism = $prism
$originalOptions = [IO.File]::ReadAllText($optionsPath)
Copy-Item -LiteralPath $launcherConfigPath -Destination (Join-Path $output 'original-instance.cfg')
Copy-Item -LiteralPath $configPath -Destination (Join-Path $output 'original-config.toml')
Copy-Item -LiteralPath $optionsPath -Destination (Join-Path $output 'original-options.txt')
[pscustomobject]@{Status='preparing'; Profile=$Profile; Label=$Label} |
    ConvertTo-Json | Set-Content (Join-Path $output 'status.json') -Encoding UTF8
$started = Get-Date
$game = $null
$status = 'preparation-failed'
$peakWorkingSet = 0L
try {
[IO.File]::WriteAllText($configPath, $config, $utf8)
$prism = Set-SectionValue $prism 'General' 'OverrideJavaArgs' 'true'
$jfrPath = (Join-Path $output 'recording-active.jfr').Replace('\', '/')
$jfrOption = '-XX:StartFlightRecording=name=jet,settings=profile,dumponexit=true,maxsize=512m,filename=' + $jfrPath
if ($jfrPath -match '\s') { $jfrOption = '"' + $jfrOption + '"' }
$jvmArgs = '-Djet.benchmark=true -Djet.benchmark.autoExit=true ' + $jfrOption
$prism = Set-SectionValue $prism 'General' 'JvmArgs' $jvmArgs
[IO.File]::WriteAllText($launcherConfigPath, $prism, $utf8)
[IO.File]::WriteAllText($optionsPath, [regex]::Replace($originalOptions, '(?m)^pauseOnLostFocus:.*$', 'pauseOnLostFocus:false'), $utf8)
Copy-Item $configPath (Join-Path $output 'config.toml')
Copy-Item $optionsPath (Join-Path $output 'options.txt')
Get-ChildItem (Join-Path $instance 'mods') -Filter '*.jar' | Sort-Object Name | ForEach-Object {
    [pscustomobject]@{ Name=$_.Name; Bytes=$_.Length; Sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
} | Export-Csv (Join-Path $output 'mods.csv') -NoTypeInformation -Encoding UTF8
$started = Get-Date
$status = 'timeout'
    [pscustomobject]@{Status='waiting-for-client'; Profile=$Profile; Started=$started.ToString('o'); ManualLaunch=[bool]$ManualLaunch} |
        ConvertTo-Json | Set-Content (Join-Path $output 'status.json') -Encoding UTF8
    if ($ManualLaunch) {
        Write-Host "Measurement prepared. Open Prism, launch '$instanceId', and enter world '$World'."
        [void](Read-Host 'After opening Prism, press Enter to attach to the client')
    } else {
        Start-Process -FilePath $Launcher -ArgumentList @('--launch', ('"' + $instanceId + '"'), '--world', ('"' + $World + '"')) | Out-Null
    }
    $launchDeadline = $started.AddSeconds($LaunchTimeoutSeconds)
    $deadline = $started.AddMinutes($TimeoutMinutes)
    $logPath = Join-Path $instance 'logs\latest.log'
    $nextProgress = Get-Date
    while ((Get-Date) -lt $deadline) {
        if ($null -eq $game) {
            $candidate = Get-CimInstance Win32_Process | Where-Object {
                $_.Name -in @('java.exe', 'javaw.exe') -and $_.CreationDate -ge $started.AddSeconds(-2) -and
                $_.CommandLine -match 'jet\.benchmark=true' -and $_.CommandLine -match 'org.prismlauncher.EntryPoint|BootstrapLauncher'
            } | Select-Object -First 1
            if ($null -ne $candidate) {
                $game = Get-Process -Id $candidate.ProcessId
                $actualJfr = Get-RecordingPath $candidate.CommandLine
                if (!$actualJfr -or ![string]::Equals($actualJfr, [IO.Path]::GetFullPath($jfrPath), [StringComparison]::OrdinalIgnoreCase)) {
                    $status = 'recording-path-mismatch'
                    throw "Client uses an unexpected JFR path: '$actualJfr'. Close this client; do not use it as a benchmark sample."
                }
                [pscustomobject]@{ Java=$candidate.ExecutablePath; Pid=$candidate.ProcessId; Profile=$Profile; Started=$started.ToString('o'); World=$World; RecordingPath=$actualJfr } |
                    ConvertTo-Json | Set-Content (Join-Path $output 'environment.json') -Encoding UTF8
                Write-Host "Attached to test client $($game.Id): $Label-$Profile"
                [pscustomobject]@{Status='running'; Pid=$game.Id; Profile=$Profile; Started=$started.ToString('o')} |
                    ConvertTo-Json | Set-Content (Join-Path $output 'status.json') -Encoding UTF8
            } else {
                if ((Get-Date) -ge $launchDeadline) {
                    $status = 'client-not-started'
                    throw 'No benchmark client appeared before launch timeout; no performance sample was collected.'
                }
                $launcherProcess = Get-Process -Name prismlauncher -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($null -eq $launcherProcess) {
                    $status = 'launcher-not-running'
                    throw 'Prism is not running; no benchmark client can be attached.'
                }
                [void] $launcherProcess.WaitForExit(1000)
                continue
            }
        }
        $game.Refresh()
        if ($game.HasExited) {
            $text = Read-Shared $logPath
            $status = if ($text -match 'JET benchmark complete: queries=120') { 'complete' } else { 'client-exited-incomplete' }
            if ($text -match 'Caught an error from mod plugin:|JEI failed to start|Mixin apply failed|MixinApplyError|InvalidInjectionException|InjectionError|NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|A JEI API method is being called by another mod from the wrong thread:|Found a broken recipe') {
                $status = 'runtime-error'
            }
            if (!(Test-Path -LiteralPath $jfrPath) -or (Get-Item -LiteralPath $jfrPath).Length -eq 0) {
                $status = 'recording-missing'
            } else {
                Copy-Item -LiteralPath $jfrPath -Destination (Join-Path $output 'recording.jfr') -ErrorAction Stop
                Get-FileHash -LiteralPath (Join-Path $output 'recording.jfr') -Algorithm SHA256 |
                    Select-Object Hash,Algorithm | ConvertTo-Json |
                    Set-Content (Join-Path $output 'recording-hash.json') -Encoding UTF8
            }
            break
        }
        $peakWorkingSet = [Math]::Max($peakWorkingSet, $game.WorkingSet64)
        if ((Get-Date) -ge $nextProgress) {
            $text = Read-Shared $logPath
            $markers = [regex]::Matches($text, '(?m)^.*(?:Starting JEI\.\.\.|Registering recipes took|JEI native budgeted filter published:|JEI startup layout notifications coalesced:|JET benchmark sidebar drawn:|JET benchmark complete:).*$')
            $stage = if ($markers.Count) { $markers[$markers.Count - 1].Value.Trim() } else { 'Waiting for world/JEI startup' }
            Write-Host ("Client {0}, elapsed {1}s: {2}" -f $game.Id, [int]((Get-Date)-$started).TotalSeconds, $stage)
            $nextProgress = (Get-Date).AddSeconds(30)
        }
        [void] $game.WaitForExit(1000)
    }
    if ($status -eq 'timeout' -and $null -ne $game -and !$game.HasExited) {
        & 'C:\Program Files\Java\jdk-21\bin\jcmd.exe' $game.Id Thread.print -l | Out-File (Join-Path $output 'timeout-threads.txt') -Encoding UTF8
        throw 'Test timed out; client left running for inspection. Do not begin another run.'
    }
} finally {
    if ($null -eq $game -and $status -eq 'timeout') { $status = 'client-not-started' }
    foreach ($name in @('latest.log', 'debug.log')) {
        $source = Join-Path $instance "logs\$name"
        if ($null -ne $game -and (Test-Path $source)) { Copy-Item $source (Join-Path $output $name) -Force }
    }
    [pscustomobject]@{Status=$status; PeakWorkingSetBytes=$peakWorkingSet; WallSeconds=((Get-Date)-$started).TotalSeconds} |
        ConvertTo-Json | Set-Content (Join-Path $output 'status.json') -Encoding UTF8
    Restore-TestValues $launcherConfigPath $originalPrism $prism @('OverrideJavaArgs', 'JvmArgs')
    Restore-TestValues $configPath $originalConfig $config @('enabled', 'tooltipSearchIndex', 'tooltipSearchMetrics')
    $currentOptions = [IO.File]::ReadAllText($optionsPath)
    $originalPause = [regex]::Match($originalOptions, '(?m)^pauseOnLostFocus:.*$').Value
    if ($originalPause -and $currentOptions -match '(?m)^pauseOnLostFocus:false\s*$') {
        $currentOptions = [regex]::Replace($currentOptions, '(?m)^pauseOnLostFocus:.*$', $originalPause)
        [IO.File]::WriteAllText($optionsPath, $currentOptions, $utf8)
    }
}
$log = Read-Shared (Join-Path $output 'latest.log')
if ($status -ne 'complete') { throw "Benchmark not complete: $status ($output)" }
if ($Profile -eq 'on' -and ($log -notmatch 'JEI tooltip index ready:.*nativeFallback=false' -or $log -match 'JEI tooltip indexing fell back|JEI tooltip index bypass')) {
    throw "Tooltip path not active: $output"
}
if ($Profile -ne 'on' -and $log -match 'JEI tooltip index ready:') { throw 'Baseline unexpectedly used tooltip index' }
if ($log -match 'JEI tooltip optimized validation passed:') { throw 'Differential diagnostics contaminated measurement' }
Write-Host "Benchmark captured: $output"