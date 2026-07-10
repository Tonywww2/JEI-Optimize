# JEI 异步优化 — Validation Scaffold

> Owner: agent3. Maps to: T2.3. Scope: baseline runClient and measurement procedure before optimization implementation.

## 1. Environment

Verified project assumptions:

- Minecraft: `1.20.1`
- Forge: `47.4.4`
- JEI API: `mezz.jei:jei-1.20.1-forge-api:15.20.0.133`
- JEI runtime: `mezz.jei:jei-1.20.1-forge:15.20.0.133`
- Gradle wrapper: `9.0.0`
- Gradle JVM: Java 21 required by Stonecutter 0.9.6
- Java compile target: Java 17 for Forge 1.20.1

Use Java 21 for Gradle commands:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

Confirm Gradle JVM:

```powershell
.\gradlew.bat --version
```

Expected marker:

```text
Gradle 9.0.0
Launcher JVM: 21.x
```

## 2. Baseline and Current runClient Smoke

Command:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon runClient
```

Baseline expected markers:

```text
Running Stonecutter 0.9.6
Architectury Loom: 1.11.x
ModLauncher running
Forge mod loading, version 47.4.4
Found mod file jei-1.20.1-forge-...-15.20.0.133.jar
Created: 256x256x0 jei:textures/atlas/gui.png-atlas
BUILD SUCCESSFUL
```

Notes:

- `Forge Version Check` may report Forge as outdated; this is not a validation failure.
- Realms auth warnings for user `Dev` are not validation failures.
- The Minecraft window closing normally prints `Stopping!` and should still end with `BUILD SUCCESSFUL`.
- If Gradle fails under Java 17 with a Stonecutter JVM requirement error, rerun with Java 21 as shown above.

## 3. Baseline Measurement Procedure

Before adding optimization mixins, capture one clean baseline run:

1. Ensure no optimization mixins are enabled beyond the empty baseline config.
2. Run `runClient` with Java 21 Gradle runtime.
3. Record whether the client reaches title screen.
4. Record the lines for JEI and Forge load markers listed in §2.
5. If diagnostics are already implemented later, record plugin timing and registration count output separately.

Baseline evidence table:

| Run | Date | Command | Result | Evidence / notes |
|---|---|---|---|---|
| baseline-0 | 2026-07-10 | `./gradlew.bat --no-daemon runClient` with Java 21 | reached Forge/JEI startup previously | JEI atlas marker observed: `jei:textures/atlas/gui.png-atlas`; final `BUILD SUCCESSFUL` after window stop. |
| current-1 | 2026-07-10 | `./gradlew.bat --no-daemon compileJava` with Java 21 | passed | Compile completed with existing deprecation warnings only. |
| current-2 | 2026-07-10 | `./gradlew.bat --no-daemon runClient` with Java 21 | passed | Latest log markers: Forge `47.4.4`, JEI jar discovered, `jei:textures/atlas/gui.png-atlas`, `Stopping!`; command exit code 0. |
| final-acceptance | 2026-07-10 | `./gradlew.bat --no-daemon compileJava` and `./gradlew.bat --no-daemon runClient` with Java 21 | passed | After removing reflection usage, compile and smoke still pass; latest run entered a singleplayer world and JEI started. |

Latest log evidence from `run/logs/latest.log`:

```text
Found mod file jei-1.20.1-forge-c9bd3659-15.20.0.133.jar
Forge mod loading, version 47.4.4, for MC 1.20.1
Created: 256x256x0 jei:textures/atlas/gui.png-atlas
Stopping!
```

No fatal `ERROR` marker was observed in the extracted latest-log startup markers. Realms auth warnings are expected in dev runs and are not validation failures.

## 2.1 Current Implementation Snapshot

Implemented and compiled components as of 2026-07-10:

| Area | Files | Build status | Runtime wiring status |
|---|---|---|---|
| Runtime generation | `JeiOptRuntimeState.java` | compileJava passed | Requires lifecycle mixin activation later. |
| Executors | `JeiOptExecutors.java` | compileJava passed | Available to async tasks. |
| Async contracts/snapshots | `AsyncIndexState.java`, `AsyncIndex.java`, `IngredientSearchSnapshot.java`, `RecipeIndexSnapshot.java` | compileJava passed | Library contracts only. |
| Config gates | `JeiOptConfig.java`, `JeiOptFeatureFlags.java` | compileJava + runClient passed | `run/config/jei_optimize-client.toml` generated with all frozen keys. |
| Diagnostics | `JeiOptDiagnostics.java`, `JeiPluginCallContext.java`, diagnostic mixins | compileJava passed | Runtime activation depends on mixin config entries. |
| Sync optimizations | `JeiOptCacheScope.java`, `IngredientFilterMixin.java`, `IngredientSorterMixin.java`, `RecipeManagerInternalCompactMixin.java` | compileJava passed | Runtime activation depends on mixin config entries and feature flags. |
| Async search/sort | `IngredientSearchSnapshotBuilder.java`, `AsyncSearchIndex.java`, `SearchIndexBuilder.java`, `AsyncSortIndex.java`, related mixins | compileJava passed | Functional equivalence still requires manual JEI interaction checks. |
| Recipe/catalyst async | `RecipeIndexSnapshotBuilder.java`, `AsyncRecipeFocusIndex.java`, `AsyncCatalystIndex.java`, related mixins | compileJava passed | Functional equivalence still requires manual R/U/catalyst checks. |

Generated config verified at `run/config/jei_optimize-client.toml` with all frozen keys and conservative defaults (`false` for feature-specific optimizations).

Reflection scan status: source scan under `src/main/java` found no remaining `java.lang.reflect`, `Class.forName`, `getDeclared*`, `setAccessible`, or reflective `invoke` usage. JEI internals are now accessed via direct compile-only JEI module dependencies or Mixin shadows/strong types.

## 4. Diagnostic Feature Validation

After T2.1/T2.2 are implemented, validate both enabled and disabled paths.

### 4.1 Plugin Timing

Config keys:

- `general.enabled`
- `diagnostics.pluginTiming`

Checks:

| Case | Config | Expected |
|---|---|---|
| timing disabled | `general.enabled=true`, `diagnostics.pluginTiming=false` | No plugin timing report; JEI behavior unchanged. |
| timing enabled | `general.enabled=true`, `diagnostics.pluginTiming=true` | Per phase/plugin timing appears in logs. |
| global disabled | `general.enabled=false` | No diagnostic mixin behavior beyond safe no-op. |

### 4.2 Registration Counts

Config keys:

- `general.enabled`
- `diagnostics.registrationCounts`

Checks:

| Case | Config | Expected |
|---|---|---|
| counts disabled | `diagnostics.registrationCounts=false` | No registration count report. |
| counts enabled | `diagnostics.registrationCounts=true` | Recipes/ingredients/aliases/categories/catalysts counts are associated with plugin uid. |

## 5. Optimization Feature Equivalence Checklist

Each optimization feature must be tested in two modes: enabled and disabled. Disabled mode must behave like JEI baseline or no-op.

| Feature | Config key | Disabled check | Enabled equivalence check | Status |
|---|---|---|---|---|
| One-start cache scope | `syncOptimizations.cacheScope` | Cache bypassed, no disk files created. | Search/R/U/catalyst results unchanged. | ◐ compile passed; manual equivalence pending |
| IngredientFilter batch init | `syncOptimizations.batchIngredientFilterInit` | JEI constructor path preserved. | Ingredient list size and search results unchanged. | ◐ compile/run smoke passed; manual search count pending |
| Sort key cache | `syncOptimizations.sortKeyCache` | JEI sort path preserved. | Empty search ingredient order unchanged. | ◐ compile/run smoke passed; manual ordering pending |
| Delayed compact | `syncOptimizations.delayCompact` | JEI compact path preserved. | Recipe queries unchanged after delayed compact. | ◐ compile/run smoke passed; manual R/U pending |
| Search preheat | `async.searchPreheat` | JEI search path preserved. | Display, `@`, `#`, `$`, `%`, `&`, `^` searches unchanged. | ◐ compile/run smoke passed; manual matrix pending |
| Snapshot chunking | `async.snapshotChunking` | Synchronous snapshot path or JEI baseline preserved. | No incomplete index is published. | ◐ compile/run smoke passed; stale publish scenarios pending |
| Sort preheat | `async.sortPreheat` | JEI sort path preserved. | Sorted index/order unchanged. | ◐ compile/run smoke passed; manual ordering pending |
| Recipe focus preheat | `async.recipeFocusPreheat` | JEI R/U path preserved. | R/U recipe sets unchanged. | ◐ compile/run smoke passed; manual R/U pending |
| Catalyst preheat | `async.catalystPreheat` | JEI catalyst path preserved. | Catalyst lookup unchanged. | ◐ compile/run smoke passed; manual catalyst check pending |

## 6. Search Equivalence Matrix

Use the same world and same JEI config for baseline and optimized runs.

| Query type | Example query | Baseline result captured | Optimized result captured | Match |
|---|---|---|---|---|
| Display name | `stone` | ☐ | ☐ | ☐ |
| Mod name | `@minecraft` | ☐ | ☐ | ☐ |
| Tooltip | `#attack` | ☐ | ☐ | ☐ |
| Tag | `$planks` | ☐ | ☐ | ☐ |
| Creative tab | `%building` | ☐ | ☐ | ☐ |
| Resource location | `&minecraft:stone` | ☐ | ☐ | ☐ |
| Color | `^white` | ☐ | ☐ | ☐ |

## 7. Recipe / Catalyst Equivalence Matrix

| Action | Test ingredient/category | Baseline captured | Optimized captured | Match |
|---|---|---|---|---|
| R lookup | Stone | ☐ | ☐ | ☐ |
| U lookup | Stick | ☐ | ☐ | ☐ |
| Catalyst click | Crafting Table | ☐ | ☐ | ☐ |
| Recipe transfer | Crafting recipe with available ingredients | ☐ | ☐ | ☐ |

Current status: matrices are intentionally not auto-filled by compile/run smoke. They require manual in-game comparison with feature flags disabled/enabled in the same world.

## 8. Lifecycle / Stale Publish Checks

| Scenario | Expected | Status |
|---|---|---|
| Exit world while async tasks are building | Tasks are cancelled or discarded by generation check. | ☐ |
| Enter a second world after first exit | No old search/recipe results appear. | ☐ |
| Resource reload during async build | Old generation results are not published. | ☐ |
| `general.enabled=false` then runClient | All feature mixins no-op or fall back to JEI baseline. | ☐ |

Current status: runClient smoke passed with generated default config. Default feature-specific optimization gates are `false`, so this primarily validates baseline/no-op startup. Explicit `general.enabled=false` run remains pending.

## 9. Failure Triage

| Symptom | Likely cause | First action |
|---|---|---|
| `Stonecutter requires JVM 21` | Gradle ran under Java 17. | Set `JAVA_HOME` to JDK 21 and rerun. |
| Missing JEI API artifact with `:api` classifier | Wrong JEI dependency notation. | Use `mezz.jei:jei-1.20.1-forge-api:15.20.0.133`. |
| Mixin class not found | `jei_optimize.mixins.json` lists unimplemented class. | Remove entry or add class. |
| Mixin target not found | JEI target changed or mapping mismatch. | Check `docs/tasks/jei-targets.md` and decompile dependency. |
| Search result missing | Async index published incomplete data. | Disable feature, compare baseline, inspect snapshot completeness. |
| Old result after world switch | Generation check failed. | Audit `JeiOptRuntimeState.isCurrent` before publish. |

## 10. Progress Writeback

When a validation run resolves a to-verify item:

1. Update this file with evidence and status.
2. Update [parallel-tasks.md](parallel-tasks.md) for the owning task output/status.
3. Update [task-plan.md](task-plan.md) task checkbox if acceptance is complete.
4. Update [jei-async-optimization-design.md](jei-async-optimization-design.md) §7 if a design fact changes from to-verify to verified.

## Revision Log

- 2026-07-10 — PA-3 initial validation scaffold created by agent3.
- 2026-07-10 — PH-1 updated validation with current compileJava/runClient evidence, generated config confirmation, implementation snapshot, and remaining manual equivalence checks.
- 2026-07-10 — Final acceptance update: reflection scan is clean; compileJava and runClient pass after direct JEI compile-only dependency refactor.