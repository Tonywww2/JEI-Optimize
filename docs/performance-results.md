# JEI Optimize Performance Benchmark Results

Generated: 2026-08-05 10:58:57 +08:00

## Protocol

- Command: `.\\gradlew.bat --no-daemon runClient --args="--quickPlaySingleplayer sstt"`
- Runtime mods: JEI, Mekanism, CoFH Core, Thermal Series core, Thermal Foundation, Farmer's Delight.
- Baseline profile: `general.enabled=false`; diagnostics disabled.
- Optimized profile: `general.enabled=true` with the shipped default feature flags; diagnostics disabled.
- Measurement point: JEI's own `Starting JEI took` log line after quick-playing into world `sstt`.
- Warmup runs per profile: 1. Measured runs per profile: 3.
- Java: `java version "21.0.7" 2025-04-15 LTS`

## Summary

| Metric | Baseline mean ms | Optimized mean ms | Delta ms | Delta percent |
| --- | ---: | ---: | ---: | ---: |
| JeiTotalMeanMs | 1874.667 | 1827 | -47.667 | -2.543% |
| WallToJeiMeanMs | 63216.333 | 61846.667 | -1369.667 | -2.167% |
| IngredientFilterMeanMs | 413.233 | 151.6 | -261.633 | -63.314% |
| RuntimeMeanMs | 780.767 | 679.9 | -100.867 | -12.919% |
| RegisteringRuntimeMeanMs | 709.867 | 607.867 | -102 | -14.369% |

Positive delta means the optimized profile was slower; negative delta means it was faster.

## Per-Run Data

| Profile | Iteration | Warmup | Status | JEI total ms | Wall to JEI ms | Ingredient filter ms | Runtime ms | Ingredients |
| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |
| baseline | 1 | True | metrics-captured | 2049 | 58868 | 411.1 | 771.7 | 2641 |
| optimized | 1 | True | metrics-captured | 1719 | 60977 | 117.3 | 556.9 | 2641 |
| baseline | 1 | False | metrics-captured | 1977 | 67355 | 489.5 | 878.3 | 2641 |
| optimized | 1 | False | metrics-captured | 2007 | 71452 | 150.9 | 702.5 | 2641 |
| baseline | 2 | False | metrics-captured | 1833 | 64493 | 339.3 | 682.3 | 2641 |
| optimized | 2 | False | metrics-captured | 1702 | 56292 | 161.4 | 635 | 2641 |
| baseline | 3 | False | metrics-captured | 1814 | 57801 | 410.9 | 781.7 | 2641 |
| optimized | 3 | False | metrics-captured | 1772 | 57796 | 142.5 | 702.2 | 2641 |

## Artifacts

- CSV: `C:\Users\12044\Documents\EX\IDEA_PROJECT\JEI_Optimize\build\benchmarks\jei-optimize\results.csv`
- Raw logs: `C:\Users\12044\Documents\EX\IDEA_PROJECT\JEI_Optimize\build\benchmarks\jei-optimize\raw`
