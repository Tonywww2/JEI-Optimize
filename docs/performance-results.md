# JEI Optimize Performance Benchmark Results

Generated: 2026-08-06 00:08:25 +08:00

## Protocol

- Command: `.\\gradlew.bat --no-daemon runClient --args="--quickPlaySingleplayer sstt"`
- Runtime mods: JEI, Mekanism, CoFH Core, Thermal Series core, Thermal Foundation, Farmer's Delight.
- Baseline profile: `general.enabled=false`; diagnostics disabled.
- Optimized profile: `general.enabled=true` with the shipped default feature flags; diagnostics disabled.
- Measurement point: JEI's own `Starting JEI took` log line after quick-playing into world `sstt`.
- Warmup runs per profile: 0. Measured runs per profile: 1.
- Java: `java version "21.0.7" 2025-04-15 LTS`

## Summary

| Metric | Baseline mean ms | Optimized mean ms | Delta ms | Delta percent |
| --- | ---: | ---: | ---: | ---: |
| JeiTotalMeanMs | 1453 | 1173 | -280 | -19.27% |
| WallToJeiMeanMs | 45030 | 52727 | 7697 | 17.093% |
| IngredientFilterMeanMs | 322.3 | 101.4 | -220.9 | -68.539% |
| RuntimeMeanMs | 644.5 | 428.4 | -216.1 | -33.53% |
| RegisteringRuntimeMeanMs | 591.9 | 383 | -208.9 | -35.293% |

Positive delta means the optimized profile was slower; negative delta means it was faster.

## Per-Run Data

| Profile | Iteration | Warmup | Status | JEI total ms | Wall to JEI ms | Ingredient filter ms | Runtime ms | Ingredients |
| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | ---: |
| baseline | 1 | False | metrics-captured | 1453 | 45030 | 322.3 | 644.5 | 2641 |
| optimized | 1 | False | metrics-captured | 1173 | 52727 | 101.4 | 428.4 | 2641 |

## Artifacts

- CSV: `C:\Users\12044\Documents\EX\IDEA_PROJECT\JEI_Optimize\build\benchmarks\jei-optimize\results.csv`
- Raw logs: `C:\Users\12044\Documents\EX\IDEA_PROJECT\JEI_Optimize\build\benchmarks\jei-optimize\raw`
