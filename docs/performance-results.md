# JEI Optimize Performance Benchmark Results

Generated: 2026-07-10 23:50:26 +08:00

## Protocol

- Command: `.\\gradlew.bat --no-daemon runClient --args="--quickPlaySingleplayer sstt"`
- Runtime mods: JEI, Mekanism, CoFH Core, Thermal Series core, Thermal Foundation, Farmer's Delight.
- Baseline profile: `general.enabled=false`; diagnostics disabled.
- Optimized profile: `general.enabled=true` with all sync and async optimization flags enabled; diagnostics disabled.
- Measurement point: JEI's own `Starting JEI took` log line after quick-playing into world `sstt`.
- Warmup runs per profile: 1. Measured runs per profile: 3.
- Java: `java version "21.0.7" 2025-04-15 LTS`

## Summary

| Metric | Baseline mean ms | Optimized mean ms | Delta ms | Delta percent |
| --- | ---: | ---: | ---: | ---: |
| JeiTotalMeanMs | 1485 | 1495.667 | 10.667 | 0.718% |
| WallToJeiMeanMs | 40807 | 39362.667 | -1444.333 | -3.539% |
| IngredientFilterMeanMs | 331.533 | 305.433 | -26.1 | -7.873% |
| RuntimeMeanMs | 650.833 | 603.167 | -47.667 | -7.324% |
| RegisteringRuntimeMeanMs | 602.4 | 558.5 | -43.9 | -7.288% |

Positive delta means the optimized profile was slower; negative delta means it was faster.

## Per-Run Data

| Profile | Iteration | Warmup | Status | JEI total ms | Wall to JEI ms | Ingredient filter ms | Runtime ms | Ingredients |
| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |
| baseline | 1 | True | metrics-captured | 1462 | 35864 | 311.2 | 610.9 | 2641 |
| optimized | 1 | True | metrics-captured | 1383 | 38805 | 291.4 | 617.5 | 2641 |
| baseline | 1 | False | metrics-captured | 1406 | 38926 | 311.2 | 627.4 | 2641 |
| optimized | 1 | False | metrics-captured | 1419 | 38905 | 283 | 559 | 2641 |
| baseline | 2 | False | metrics-captured | 1572 | 41499 | 375.6 | 726.6 | 2641 |
| optimized | 2 | False | metrics-captured | 1481 | 39353 | 324.2 | 608.9 | 2641 |
| baseline | 3 | False | metrics-captured | 1477 | 41996 | 307.8 | 598.5 | 2641 |
| optimized | 3 | False | metrics-captured | 1587 | 39830 | 309.1 | 641.6 | 2641 |

## Artifacts

- CSV: `C:\Users\12044\Documents\EX\IDEA_PROJECT\JEI_Optimize\build\benchmarks\jei-optimize\results.csv`
- Raw logs: `C:\Users\12044\Documents\EX\IDEA_PROJECT\JEI_Optimize\build\benchmarks\jei-optimize\raw`
