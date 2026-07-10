# JEI Optimize Performance Benchmark

This benchmark compares JEI startup performance with this mod's optimization flags disabled and enabled in the same Forge development runtime.

## Profiles

- Baseline: `general.enabled=false`; diagnostics disabled.
- Optimized: `general.enabled=true`; all sync and async optimization flags enabled; diagnostics disabled.

The script edits `run/config/jei_optimize-client.toml` before each run and restores the original file when finished.

## Command

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\scripts\benchmark-jei-optimize.ps1 -Iterations 3 -WarmupIterations 1 -World sstt
```

## Measured Metrics

- Primary metric: JEI's own `Starting JEI took` log line.
- Secondary metrics: JEI runtime build, ingredient filter build, recipe registration, and wall-clock time until the JEI startup line appears.
- Run environment: Forge dev client with JEI, Mekanism, CoFH Core, Thermal Series core, Thermal Foundation, and Farmer's Delight.

## Outputs

- `build/benchmarks/jei-optimize/results.csv`
- `build/benchmarks/jei-optimize/summary.md`
- `docs/performance-results.md`
- raw stdout/stderr/latest.log copies under `build/benchmarks/jei-optimize/raw/`

The script stops each client process after the JEI startup metric is captured. A non-zero game exit caused by this controlled stop is not treated as a benchmark failure when JEI metrics were captured.

## External Modpack Collection

For launcher-managed or complex external modpack testing, use `scripts/collect-external-jei-optimize.ps1` instead. It does not start Minecraft; it writes the requested JEI Optimize profile, watches the external instance log, and packages metrics, logs, configs, mod manifest, and crash reports. See `docs/external-performance-collection.md`.