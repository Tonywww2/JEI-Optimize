#Requires -Version 5.1
[CmdletBinding()]
param([string] $JavaHome = 'C:\Program Files\Java\jdk-21')

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build\tooltip-tests'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sources = @(
    'src/main/java/com/tonywww/jeioptimize/integration/MineColoniesAttributeModifiers.java'
    'src/test/java/com/tonywww/jeioptimize/integration/TooltipMineColoniesAttributesTest.java'
    'src/main/java/com/tonywww/jeioptimize/snapshot/TooltipSearchSnapshot.java'
    'src/main/java/com/tonywww/jeioptimize/runtime/TooltipCaptureContext.java'
    'src/main/java/com/tonywww/jeioptimize/runtime/JeiOptFilterBuildGate.java'
    'src/main/java/com/tonywww/jeioptimize/runtime/JeiOptUiRefreshBatch.java'
    'src/test/java/com/tonywww/jeioptimize/runtime/TooltipUiRefreshBatchTest.java'
    'src/main/java/com/tonywww/jeioptimize/runtime/JeiOptTooltipCache.java'
    'src/main/java/com/tonywww/jeioptimize/index/TooltipSearchIndex.java'
    'src/main/java/com/tonywww/jeioptimize/index/TooltipIndexPipeline.java'
    'src/main/java/com/tonywww/jeioptimize/index/DeferredNativeSearchStorage.java'
    'src/test/java/com/tonywww/jeioptimize/index/TooltipDeferredStorageTest.java'
    'src/main/java/com/tonywww/jeioptimize/index/TooltipSnapshotProducer.java'
    'src/test/java/com/tonywww/jeioptimize/index/TooltipPipelineTest.java'
    'src/test/java/com/tonywww/jeioptimize/runtime/TooltipCaptureContextTest.java'
    'src/test/java/com/tonywww/jeioptimize/runtime/TooltipBuildGateTest.java'
    'src/test/java/com/tonywww/jeioptimize/index/TooltipSearchIndexTest.java'
) | ForEach-Object { Join-Path $root $_ }
& (Join-Path $JavaHome 'bin\javac.exe') --release 17 -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Tooltip test compilation failed' }
foreach ($test in @('runtime.TooltipCaptureContextTest', 'runtime.TooltipBuildGateTest', 'index.TooltipSearchIndexTest', 'index.TooltipPipelineTest', 'runtime.TooltipUiRefreshBatchTest', 'index.TooltipDeferredStorageTest', 'integration.TooltipMineColoniesAttributesTest')) {
    & (Join-Path $JavaHome 'bin\java.exe') -cp $output "com.tonywww.jeioptimize.$test"
    if ($LASTEXITCODE -ne 0) { throw "Failed: $test" }
}