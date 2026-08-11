# JEI Optimize Performance Benchmark

This benchmark compares JEI startup performance with this mod's optimization flags disabled and enabled in the same Forge development runtime.

## Profiles

- Baseline: `general.enabled=false`; diagnostics disabled.
- Optimized: `general.enabled=true`; all sync and async optimization flags enabled; diagnostics disabled.

The script edits `run/config/justenoughthreads-client.toml` before each run and restores the original file when finished.

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

## Extreme Ingredient Lists

For packs with roughly 2,000 or more JEI pages, run at least two ingredient-count tiers and keep
the same world, window size, search configuration, and JVM heap between profiles.

| Metric | Why it matters |
|---|---|
| Ingredient count and distinct uid count | Correctness gate; optimized and baseline values must match. |
| Chunk count and chunk-build duration | Measures real progress throughput and detects chunk-size regressions. |
| Sidebar publication time | Confirms the final atomic refresh remains small as page count grows. |
| Sidebar cache invalidations | Must be one during initial chunked publication, not one per chunk. |
| First 10 seconds frame-time P95/P99 | Detects visible stutter while the background index builds. |
| Peak heap and GC pause time | Detects tooltip/tag snapshot or index-object amplification. |
| Plain, `@`, `#`, and `$` search P50/P95 | Separates initial indexing gains from query-time scaling. |

Compare at minimum:

1. Current release baseline with optimizations disabled.
2. Chunked worker build (`asyncIngredientFilter=true`).
3. Isolated client-tick build (`asyncIngredientFilter=false`, `deferredIngredientFilter=true`).
4. At least one larger ingredient tier above the reported 2,000-page pack.

The first accepted optimization for this scale is already part of the chunked builder: intermediate
chunks do not invalidate JEI's live sidebar cache, and the completed index is swapped once. Further
work remains benchmark-gated:

- Replace full token scans in the optional prefix shadow index with an n-gram/inverted structure
	only if that feature is enabled and `@`/`#`/`$` P95 is a demonstrated bottleneck.
- Add generation-local string interning for tooltip/tag snapshots only if heap profiles show enough
	duplicate-string retention to offset the pool overhead.
- Wire recipe-focus, catalyst, and sort preheats into production queries only after result-set
	equivalence tests prove that the built indexes are complete and actually consumed.
- Never parallelize plugin callbacks or call recipe categories, renderers, or ingredient helpers
	from generic worker tasks to chase benchmark numbers.

## External Modpack Collection

For launcher-managed or complex external modpack testing, use `scripts/collect-external-jei-optimize.ps1` instead. It does not start Minecraft; it writes the requested JEI Optimize profile, watches the external instance log, and packages metrics, logs, configs, mod manifest, and crash reports. See `docs/external-performance-collection.md`.