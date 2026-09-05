# JEI 异步优化 — Validation Scaffold

> Owner: agent3. Maps to: T2.3. Scope: baseline runClient and measurement procedure before optimization implementation.

## ATM9 / GTCEu Registration Stall

The inspected All the Mods 9 `1.1.1` environment uses Minecraft `1.20.1`, Forge `47.4.0`, JEI
`15.20.0.116`, GTCEu `7.2.0`, Just Enough Threads `0.13.6`, Java `21.0.8`, and a fixed 8 GiB heap.
Its debug log ends while `gtceu:jei_plugin` has been in recipe registration for 15 seconds. It does
not contain an out-of-memory error, deadlock proof, crash marker, or normal JEI completion marker,
so the evidence supports a heavy registration/allocation stall rather than a proven deadlock.

Version `0.13.7` batches only
`com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeJEICategory.registerRecipes`. The optional
Mixin activates only when that exact method contains exactly one `List.copyOf(Collection)` call and
one JEI `IRecipeRegistration.addRecipes(RecipeType, List)` call. Each category of more than 8192
recipes is submitted in immutable, ordered batches of at most 8192; smaller categories retain the
original `List.copyOf` path. No recipes or focus indexes are removed.

Repository validation completed:

| Check | Result |
|---|---|
| Forge 1.20.1 focused compile | passed |
| Forge 1.20.1 full build | passed |
| NeoForge 1.21.1 full build | passed; optional GTCEu Mixin remains absent when GTCEu is absent |
| Synthetic 20,003-entry ordered registration | passed in immutable batches `[8192, 8192, 3619]`; every entry exactly once |
| Forge remapped jar inspection | helper class, Mixin class, and Mixin JSON entry present |

Real ATM9 startup validation is still required. Success requires a JEI completion marker, GTCEu
batch start/completion markers, and no Mixin application error, plugin callback failure,
task-pump error, out-of-memory error, or crash.

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
| Config gates | `JeiOptConfig.java`, `JeiOptFeatureFlags.java` | compileJava + runClient passed | `run/config/justenoughthreads-client.toml` generated with all frozen keys. |
| Diagnostics | `JeiOptDiagnostics.java`, `JeiPluginCallContext.java`, diagnostic mixins | compileJava passed | Runtime activation depends on mixin config entries. |
| Sync optimizations | `JeiOptCacheScope.java`, `IngredientFilterMixin.java`, `IngredientSorterMixin.java`, `RecipeManagerInternalCompactMixin.java` | compileJava passed | Runtime activation depends on mixin config entries and feature flags. |
| Async search/sort | `IngredientSearchSnapshotBuilder.java`, `AsyncSearchIndex.java`, `SearchIndexBuilder.java`, `AsyncSortIndex.java`, related mixins | compileJava passed | Functional equivalence still requires manual JEI interaction checks. |
| Recipe/catalyst async | `RecipeIndexSnapshotBuilder.java`, `AsyncRecipeFocusIndex.java`, `AsyncCatalystIndex.java`, related mixins | compileJava passed | Functional equivalence still requires manual R/U/catalyst checks. |

Generated config verified at `run/config/justenoughthreads-client.toml` with all frozen keys and conservative defaults (`false` for feature-specific optimizations).

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
| Exit world or lose the server while async tasks are building | Tasks are cancelled or discarded by generation check. | ☑ Forge 1.20.1 / JEI 15.48: `Minecraft.clearLevel(Screen)` cancelled startup 253 ms after `Starting JEI...`; worker acknowledged cancellation 113 ms later. ☑ NeoForge 1.21.1 / JEI 19.27: `Minecraft.disconnect(Screen, boolean)` cancelled startup 318 ms after start; worker acknowledged 50 ms later. Neither run sent a runtime or completed startup. Evidence: `build/benchmarks/jei-compat/disconnect-cancel-*.latest.log`. |
| Enter a second world after first exit | No old search/recipe results appear. | ☐ |
| Resource reload during async build | Old generation results are not published. | ☐ |
| `general.enabled=false` then runClient | All feature mixins no-op or fall back to JEI baseline. | ☑ Forge JEI 15.48 completed its baseline startup in 1.526 seconds; early config gating prevented this mod's mixins and generation startup from applying. Evidence: `build/benchmarks/jei-compat/forge-15.48.0.179-general-disabled.latest.log`. |
| Chunked ingredient index publication | Progress is monotonic; no partial sidebar refresh; one final swap before runtime callbacks. | ☑ Automated startup smoke: JEI 15.20 completed 2670 ingredients in 6 chunks; JEI 15.48 completed 2670 in 6 chunks through its per-element ABI; NeoForge JEI 19.27 completed 1688 in 4 chunks. All published on Render thread before `Sending Runtime`, with no fallback after ABI selection. Manual visual progress review remains open. |
| Container input during active indexing | JEI input is ignored without consuming vanilla input or touching an unpublished runtime. | ☑ SDBF crash root cause: JEI 15.21 registered GUI input, submitted 24,680 ingredients in 50 chunks, then Ixeris replayed one queued key before runtime publication; JEI threw `Jei Client Configs have not been created yet`. Exact JEI 15.21 input ABI matches the guard. Real Forge screen-key events dispatched between chunk submission and final publication on JEI 15.20 and 15.48 hit the guard once, returned `consumed=false`, completed startup, and produced no runtime error. NeoForge remained safe before publication. Evidence: `build/benchmarks/jei-compat/input-guard-*.latest.log`. |
| JEED effect click during active indexing | Ignore only the premature effect activation; preserve vanilla container mouse input. | ◐ The global `MouseHandler.onPress` cancellation was removed. A temporary Forge 1.20 probe called JEED `onClickedEffect` after `registerRecipes`, while helpers existed but runtime publication was still pending; the dedicated guard cancelled the call and JEI completed in 2.741 seconds. The probe was removed and the final production run completed in 2.693 seconds. Direct slot/drag and second-world interaction checks remain open. Evidence: `build/benchmarks/jei-compat/*-jeed.latest.log`. |
| Window resize or maximize during active startup | Replayed third-party clicks cannot enter `RecipesGui` before `Internal.setRuntime`; runtime callbacks and publication cannot be separated by another queued event. | ☑ Crash report from Forge JEI 15.21 showed Ixeris replaying an FTB Quests mouse click after `onRuntimeAvailable` but before global runtime publication. JEI bytecode confirms callback-before-publication ordering. Runtime callbacks and publication now share one Render-thread task, with all public `RecipesGui` open entry points guarded until startup completes. Temporary direct early-show and queued-publication probes passed on Forge JEI 15.20, Forge JEI 15.48's running-field variant, and NeoForge JEI 19.27; the instrumentation was then removed. Final probe-free Forge 15.20 and NeoForge 19.27 smoke runs also completed startup. Evidence: `build/benchmarks/jei-compat/forge-15.20.0.120.latest.log`, `forge-15.48.0.179.latest.log`, and `neoforge-default.latest.log`. |
| Inventory rendering while joining a multiplayer server | JEI GUI initialization, layout updates, and overlays stay inactive until runtime publication, then resume without reopening the screen. | ☑ NeoForge JEI 19.27 crash path reached `RecipeBookmarkElement` from `GuiEventHandler.onDrawScreenPost` while `Internal.getJeiRuntime()` was unavailable. A temporary probe opened the inventory during active startup: the render guard blocked the callback, JEI completed in 2.113 seconds, and rendering resumed on the next frame with no runtime error. Probe instrumentation was removed. Final probe-free NeoForge 19.27, Forge JEI 15.20, and Forge JEI 15.48 runs completed with no injection or runtime failure. Evidence: `build/benchmarks/jei-compat/neoforge-default.latest.log`, `forge-15.20.0.120.latest.log`, and `forge-15.48.0.179.latest.log`. |
| Minecraft profiler isolation | JEI startup callbacks cannot mutate the render thread's active profiler map. | ☑ Forge and NeoForge probes confirmed `Minecraft.getProfiler()` was intercepted only on the dedicated startup thread (then `jei_optimize-start`, now `justenoughthreads-start`); the render-thread call did not hit the guard, startup completed, and no mixin/runtime failure occurred. `ActiveProfiler.getResults()` passes its mutable entries map directly to `FilledProfileResults`, matching the reported CME. The crash session also logged a render-thread profiler push/pop mismatch, so the exact writer remains unproven. Evidence: `build/benchmarks/jei-compat/profiler-guard-*.latest.log`. |
| Third-party search-string extraction | Tooltip and other mod callbacks never run concurrently in the pure worker pool. | ☑ Real JEI search insertion now runs in bounded client-thread chunks; workers coordinate publication and parallelize only immutable snapshot strings. An opt-in JEI 15.20 run captured all 2,670 snapshots over client ticks, then completed the pure prefix index with no failed prefixes. Forge JEI 15.20/15.48 and NeoForge JEI 19.27 completed without `ThreadLocalRandom` or `ConcurrentModificationException`. Evidence: `build/benchmarks/jei-compat/forge-15.20.0.120-search-snapshot.latest.log`. |

### JEI Hot-Path Compatibility Matrix

| Runtime | Selected optimizations | Result |
|---|---|---|
| Forge JEI `15.20.0.120` | `JEI_15_LEGACY`; legacy lazy recipe layouts; brewing/menu variants skipped by ABI | JEI started in 2.503 seconds; 2,670 ingredients published in 6 safe chunks. Auto worker selection resolved to 8 threads. |
| Forge JEI `15.48.0.178` | Indexed brewing lookup and hidden anvil/grindstone menu batching | JEI startup took 3.123 seconds; 2,670 ingredients published in 6 chunks. Old JEI config keys produced expected version-switch noise. |
| Forge JEI `15.48.0.179` | `JEI_15_MODERN`; indexed brewing lookup and hidden anvil/grindstone menu batching | The brewing index reported active for generation 2; JEI started in 2.397 seconds and published 2,670 ingredients in 6 chunks with no fallback. Auto worker selection resolved to 8 threads. |
| NeoForge JEI `19.27.0.340` | `JEI_19_PLUS`; PotionBrewing-aware indexed lookup; upstream direct grindstone computation retained | The brewing index reported active for generation 2; JEI started in 4.650 seconds and published 1,688 ingredients in 4 chunks with no fallback. Auto worker selection resolved to 8 threads. |

### JEI 15.49+ Artifact and Client Task-Pump Guard

JEI `15.49.0.199-.202` Gradle metadata exposes a standalone-incomplete `-unshaded` runtime
variant. Compatibility overrides now select Maven's complete default jar, and
`scripts/test-jei-compat.ps1` rejects `-unshaded`, verifies Forge's relocated Baked Substring
implementation, and writes the exact runtime/config hashes. Forge and NeoForge log formats have
separate path extractors; both produced manifests in the final runs.

A temporary probe invoked `Minecraft.managedBlock` from `justenoughthreads-start`. The production
`ClientTaskPumpGuardMixin` intercepted its single `Minecraft.pollTask()` attempt and returned
without dequeuing. Render thread completed the queued work, JEI started in 3.118 seconds, and the
run had no plugin, wrong-thread, empty-queue, or critical Mixin error. The probe was removed before
the following production-only matrix:

| Runtime | Loaded JEI SHA-256 | JEI time | Guard hits | Relevant errors |
|---|---|---:|---:|---:|
| Forge JEI `15.49.0.199` | `8DC48E23211B42FF47660C72A03B3F13A3DD734D6DD237B7FC1CFDE114BFA596` | 3.073 s | 0 | 0 |
| Forge JEI `15.57.0.207` | `CF252568CC15D10C6DF8A598143C2F85AA2839FAF0768227A97848EF2BFDD237` | 3.677 s | 0 | 0 |
| Forge `.199` + Re:Avaritia `1.4.1` | `.199` hash above | 3.353 s | 0 | 0 |
| NeoForge JEI `19.27.0.340` | `1343FD994F411CB53C430DA8674EC31064A7EB6724A6B4A7AF76949AA3E0E13E` | 5.625 s | 0 | 0 |
| NeoForge 19.27 + Re:Avaritia `1.4.1` | NeoForge hash above | 5.916 s | 0 | 0 |

Every run entered a real singleplayer world and emitted exactly one JEI completion marker.
"Relevant errors" counts plugin exceptions, runtime `RunningOnDifferentThreadException`,
`NoSuchElementException`, Baked Substring linkage failures, and critical Mixin failures. Forge
debug logs mention the `RunningOnDifferentThreadException.class` file once while scanning the
Minecraft jar; runtime logs contain no thrown instance. Both Re:Avaritia runs identified
`avaritia:jei_plugin` without requiring a main-thread policy entry.

Pinned Re:Avaritia SHA-512 values remained:

- Forge: `1E75A8A93B1DE4A5A574EE1D1F652E02060DF25FBE7B0A3E0F9FFCE7D94E9DD0A77265DA450621484FC1888B1C49C02A8C35732FF15A20F3C4FA13A099879DA9`
- NeoForge: `34217CB841F149E8AD697FD79BEE3FCBAFAB096731D9498157A887E78FAFEBAA72F52D09E104E7391F5C529B6CA61A6006C98C9FE9204ADF85228AEE519D400F`

Evidence:
`build/benchmarks/jei-compat/{forge-15.49.0.199,forge-15.57.0.207,neoforge-default}.task-pump-guard-final.*`
and the corresponding `task-pump-guard-final-avaritia` files. The original large-pack log omits
the interval containing the first caller, so the production guard records a complete first-hit
stack plus active phase/plugin for any future reproduction.

#### Production Forge namespace follow-up

An SDBF production `debug.log` from 2026-09-05 loaded Just Enough Threads `0.13.5` and completed JEI
once in 26.76 seconds. It contained zero `RunningOnDifferentThreadException`, Client task execution
errors, `NoSuchElementException`, plugin callback errors, or critical Mixin errors. It nevertheless
showed that the new guard was not active:

```text
JEI Optimize turned off its off-main client task-pump guard optimization: this JEI build's
net.minecraft.util.thread.BlockableEventLoop no longer declares method pollTask()Z.
```

Forge production exposes the same method as `m_7245_()Z`; the release refmap already targeted that
name, but the custom ASM precheck used only the development name and rejected the Mixin first.
Version `0.13.6` accepts either `pollTask()Z` or `m_7245_()Z`, still with the exact descriptor. The
remapped jar was checked to contain both gate names and the refmap target. A subsequent user-run
SDBF launch recorded the new DEBUG enable line for `m_7245_()Z` and Mixin's application of
`ClientTaskPumpGuardMixin` to `BlockableEventLoop`; the old disable warning was absent.

Post-fix development-runtime clients selected `pollTask()Z` explicitly on both loaders. Forge JEI
`15.49.0.199` completed in 2.844 seconds and NeoForge JEI `19.27.0.340` in 5.763 seconds. Both had
one guard-enable marker, zero guard-disable markers, zero normal-path guard hits, and zero plugin,
wrong-thread, empty-queue, or critical Mixin errors. The Forge `0.13.6` remapped jar contains the
`m_7245_()Z` refmap target for the production namespace.

The production SDBF run used JEI `15.21.0.148`, completed JEI once in 21.59 seconds, remained in a
singleplayer world for roughly six minutes, and shut down normally. It had zero guard hits,
`RunningOnDifferentThreadException`, Client task execution errors, `NoSuchElementException`, plugin
callback errors, critical Mixin errors, crash reports, or lifecycle failures. Because it neither
triggered the guarded path nor used the original report's JEI `.199`, it verifies deployment and
normal-path safety rather than an end-to-end reproduction of the original race.

One unrelated `ConcurrentModificationException` occurred on `Thread-17` while NightConfig's
`WriteAsyncFileConfig` iterated a `LinkedHashMap`, before JEI startup began. The only JET-thread
ERROR remained Apotheosis `7.4.8` indexing an empty socketing recipe; neither stack entered the
Minecraft Client task pump from `justenoughthreads-start`.

The same log's only JET-thread ERROR was an independent Apotheosis socketing recipe that failed
JEI's `setRecipe`; `jei:forge_gui` also emitted one five-second slow-phase report before startup
completed. Neither matched the queue-race signature.

Thermal Expansion runtime compatibility was also verified on Forge with JEI `15.21.0.148` and
Thermal Expansion `11.0.1.29`. `ThermalExpansionJeiPluginMixin` applied, and Stirling fuel batches
compacted from 5 to 5 and from 313 to 14 pages. JEI startup completed in 5.168 seconds with zero
`Could not compact Thermal`, ABI-disable, `InjectionError`, `InvalidInjectionException`, or
`MixinApplyError` markers. The remapped `0.13.3` Forge jar invokes the production-mapped
`Recipe.m_6423_()` method instead of reflecting the development-only `getId` name. Evidence:
`build/benchmarks/jei-compat/forge-15.21.0.148.thermal.latest.log` and
`forge-15.21.0.148.thermal.debug.log`. The same run applied the Mekanism Nutritional Liquifier
feature and compacted 117 recipes to 12 pages without a compaction or ABI failure.

Generator Galore `1.2.5` runtime compatibility was verified separately against JEI `15.21.0.148`
using the cached Loom-named artifact derived from the released jar listed below. The compatibility
Mixin applied to `lambda$registerRecipes$14`, and all 11 solid-fuel batches reached the compactor;
the largest reductions were 132 to 43, 120 to 86, and 126 to 8 pages. JEI startup completed in
7.872 seconds with zero compaction fallback, ABI-disable, `InjectionError`,
`InvalidInjectionException`, or `MixinApplyError` markers. Evidence:
`build/benchmarks/jei-compat/forge-15.21.0.148.generator-galore.latest.log` and
`forge-15.21.0.148.generator-galore.debug.log`.

Hidden-menu A/B on Forge JEI 15.48 used identical configuration except
`skipRedundantMenuUpdates`. Both runs registered 95 generated anvil recipes and 250 grindstone
recipes; the disabled run skipped all four menu-related mixins before application. Evidence:
`build/benchmarks/jei-compat/forge-15.48.0.179-menu-batch-{on,off}.latest.log`.

Brewing-index A/B on Forge JEI 15.48 used identical configuration except
`indexedBrewingLookup`. Both runs generated 228 brewing recipes; the enabled run reported an active
generation-scoped index with no divergence fallback, while the disabled run skipped both brewing
index variants before application. Evidence: `build/benchmarks/jei-compat/forge-15.48.0.179-borrowed-optimizations.latest.log`
and `forge-15.48.0.179-brewing-index-off.latest.log`.

Current status: runClient smoke passed with generated default config. Default feature-specific optimization gates are `false`, so this primarily validates baseline/no-op startup. Explicit `general.enabled=false` run remains pending.

### Optional Integration Smoke: Just Enough Effect Descriptions

| Input | Version / SHA-256 | Result |
|---|---|---|
| JEED, Forge 1.20.1 | `1.20-2.2.5` / `841DEE59C98C1B490073866A47DBD77F13125D425A8C6A8E850CF8C43E699A83` | Released bytecode exposes `onClickedEffect(MobEffectInstance, double, double, int)` and the expected helpers/runtime fields, but dereferences them without null checks. The dedicated guard mixin loaded on JEI 15.20 and 15.48. |
| JEED, NeoForge 1.21.1 | `1.21-2.3.3` / `B3FF4795E0CBB491FFADA136884A78BA8D66DF5D9C3C44230BFFDBFA3DC797D7` | Released bytecode has the same guarded ABI and already includes upstream helpers/runtime null checks. The additional generation-aware guard mixin loaded successfully. |
| Forge JEI 15.20 | `15.20.0.120` | JEI startup completed in 2.681 seconds with no JEED NPE, mixin injection error, or ABI-disable marker. |
| Forge JEI 15.48 | `15.48.0.179` | JEI startup completed in 3.211 seconds with no JEED NPE, mixin injection error, or ABI-disable marker. |
| NeoForge JEI 19.27 | `19.27.0.340` | JEI startup completed in 4.708 seconds with no JEED NPE, mixin injection error, or ABI-disable marker. |

The compatibility follows the narrow early-return design in JEED pull request 77, while also
checking this project's generation-aware startup state. That extra state check prevents a static,
non-null JEED runtime left by an earlier world from being used during a later JEI startup. The old
global `MouseHandler` mixin is no longer registered, so unrelated container clicks are not held
back while the ingredient index builds. A temporary runtime probe directly exercised the pre-runtime
effect-click path on the released Forge jar; it was safely ignored and then removed before the final
probe-free build and smoke run.

### Optional Integration Smoke: Tinkers' Construct

| Input | Version / SHA-256 | Result |
|---|---|---|
| Tinkers' Construct | `3.11.2.166` / `653B49D73481A1325BA78ADC7273DEE6C765A51BAFDF264A7510576D6EF91C43` | Forge 1.20.1 runtime loaded; casting compaction reduced 2,473 display pages to 2,390. |
| Mantle | `1.11.104` / `6052E47C3981064BD5213728EAA3C5418289F6F85B07A1CD4DD1505239248838` | Required dependency loaded with Tinkers. |
| JEI 15.20 | `15.20.0.120` | Default-off and enabled prefilter runs reached JEI startup; enabled run removed 2,060 tagged variants before global indexing. |
| JEI 15.48 | `15.48.0.179` | Enabled prefilter run reached JEI startup in 4.231 seconds and removed the same 2,060 variants. |

The tested Tinkers jar contains both default item tag resources: `tconstruct:modifiable` and
`tconstruct:parts`. No official Tinkers' Construct NeoForge 1.21.1 release was identified, so that
optional integration remains a guarded no-op on the NeoForge target.

### Optional Integration Smoke: Forge 1.20.1 Multi-Mod Compatibility

The following released jars were checked against Forge 47.4.4 and JEI 15.48.0.179:

| Input | Version / SHA-256 | Runtime result |
|---|---|---|
| Celestial Forge | `1.1.9` / `F2D80F27A2AA68F5B8E7EDE2543F775B6B43B1F51A77A98D831FA02C1D8FEE56` | Default cache and optional 3-family limiter both reached JEI startup. |
| Celestial Core | `1.6.1` / `2D63D1BF4ED81B3F53E1DD35356DF39559C9764618EB01AB37C09D9E6DC1BF64` | Required by the exercised Celestial tooltip path. |
| L2 Library | `2.5.3` / `866DAC367928C6A0B1FD64DDCD078F4F217309EE7DBECCFD18A96C2766207CFA` | Required by Celestial Forge; its nested libraries were supplied explicitly to the Loom runtime. |
| L2 Damage Tracker | `0.3.8` / `95DF8A97CE66C9A1EB48CA7F5B01EA9B1EDD1FF31093B7643DE91F92BAC912D7` | Loaded from the official Celestial Core JarJar payload. |
| Embers Rekindled | `1.4.7` / `D119455E74A8976AD3533A2C2B25FE942DF8A27A57FCF35DA1CD05177123477A` | Default compaction and optional representative limiting reached JEI startup. |
| Super Factory Manager | `4.34.0` / `34EE6EAB2783B0B3A53450A6700C215E21DC653357862B24BE7888E95554EF56` | Default cache and optional representative limiting reached JEI startup. |
| Ultimate Car Mod | `1.0.45` / `49648F66E9D0537AF2452C5C77AE402313A783E7E14166E64282B4A766E678F1` | Recipe builder and category compatibility mixins loaded in the combined smoke. |
| Iron Furnaces | `4.1.8` / `E457052522CEF1F8644B6929A64EAB96DE04CE9E6E32A715F1D68D79DA26D66B` | JEI plugin and both generator category mixins loaded in the combined smoke. |
| Generator Galore | `1.2.5` / `4AEBAEDFAFC1F4A7C6A1D88C59DCE17EDABE5AC549CBF99F35C47A11517EEC4A` | JEI plugin compatibility mixin loaded in the combined smoke. |
| Productive Trees | `0.2.6` / `068B8185E0D6C82170FBA90FD615B4A97D294647029AEC7723AD40F7DFC06FE0` | Blocked before JEI startup by an upstream Forge registration crash. |
| Productive Lib | `0.0.4` / `E91C001B3589F505B5A71A533137BC672C59DF68D515714FC4FAC42C57BEC8F0` | Official Productive Trees JarJar payload was supplied explicitly; it did not resolve the upstream crash. |

Runtime matrix:

| Case | Configuration | Result |
|---|---|---|
| Seven-mod default integration | Lossless integration features enabled; all aggressive modes and Tinkers prefilter disabled | Celestial Forge, Embers, SFM, Ultimate Car, Iron Furnaces, Generator Galore, and Tinkers reached JEI startup in 5.155 seconds; 7,623 ingredients were submitted in 16 chunks. All targeted compatibility mixins loaded with no missing class, injection, or ABI-disable marker. |
| Explicit no-op paths | `cacheCelestialForgeReinforce=false`, `compactEmbersDawnstoneAnvil=false`, `cacheSfmFallingAnvil=false`; all three aggressive modes disabled | The three official mods still registered their original recipe types and reached JEI startup in 2.801 seconds. The target mixins loaded, so this exercises the configuration gates rather than absent targets. |
| Celestial lossless cache | Cache enabled; aggressive mode disabled; complete official dependency chain supplied | Empty dynamic ingredients are now cache misses and entries are generation-scoped. A temporary released-jar probe called every wrapper's `input()` and `result()` twice; all ten wrappers retained identical non-empty first/cached counts (18-91 stacks). The probe was removed and the final production run reached JEI startup in 2.331 seconds without an injection or ABI-disable marker. |
| Celestial representative limit | `aggressiveCelestialForgeReinforce=true`, representative limit `3` | JEI started in 2.558 seconds with the reinforce wrapper and recipe type present and no missing class, injection, or ABI-disable marker. |
| Embers representative limits | `aggressiveEmbersDawnstoneAnvil=true`; limits `3` and `16` | Released Embers 1.4.7 generated 1,057 Dawnstone Anvil display pages. Accepting its legitimate empty-top breakdown pages and sharing the repair budget across singleton groups reduced this to 21 pages; JEI started in 1.918 seconds with no injection or compaction failure. The same fixed code with aggressive mode disabled performed lossless compaction to 34 pages. |
| SFM representative limits | `aggressiveSfmFallingAnvil=true`; limits `3` and `16` | The category and recipe type loaded; JEI started in 2.505 seconds with no missing class, injection, or ABI-disable marker. Opening SFM's Falling Anvil category to execute its private layout method remains a manual check. |

Productive Trees could not reach JEI registration. With its official nested Productive Lib loaded,
the mod failed in `CapabilityContainerBlock.<init>` while reading `BlockStateProperties.AXIS` from
`minecraft:air`, followed by a missing registry object for
`productivetrees:time_traveller_display`. This occurs during the mod's Forge registration lifecycle,
before JEI or the Productive Trees compatibility mixin can execute, so it is recorded as an upstream
runtime blocker rather than a compatibility failure.

Loom does not reliably discover JarJar dependencies nested in externally supplied runtime jars. The
Celestial and Productive Trees smoke runs therefore extracted the unmodified nested jars from the
official artifacts and passed them explicitly through `-RuntimeModJars`. This is a development-runtime
constraint; the nested payloads are not repackaged into this project's release jar.

## 9. Failure Triage

| Symptom | Likely cause | First action |
|---|---|---|
| `Stonecutter requires JVM 21` | Gradle ran under Java 17. | Set `JAVA_HOME` to JDK 21 and rerun. |
| Missing JEI API artifact with `:api` classifier | Wrong JEI dependency notation. | Use `mezz.jei:jei-1.20.1-forge-api:15.20.0.133`. |
| Mixin class not found | `justenoughthreads.mixins.json` lists unimplemented class. | Remove entry or add class. |
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
- 2026-08-07 — Verified single-flight startup cancellation and pre-publication abort on world stop; normal startup smoke passed on JEI 15.20, JEI 15.48, and NeoForge JEI 19.27.
- 2026-08-27 — Added Tinkers 3.11.2.166 / Mantle 1.11.104 runtime evidence for casting compaction and the default-off global ingredient prefilter on JEI 15.20 and 15.48.
- 2026-08-27 — Added released-jar smoke evidence for Celestial Forge, Embers, SFM, Ultimate Car, Iron Furnaces, Generator Galore, and the Productive Trees upstream registration blocker; verified default, explicit no-op, and aggressive startup paths.
- 2026-08-29 — Replaced the global container mouse lock with a generation-aware JEED effect-click guard; verified released JEED jars on Forge JEI 15.20/15.48 and NeoForge JEI 19.27.
- 2026-09-01 — Rechecked the reported JEI boundary where `15.20.0.119` is the last unaffected
	version and `15.20.0.120` is the first affected version. Official Maven source and binary diffs
	show identical Java classes; only manifests/version metadata and `pl_pl.json` differ. With the
	duplicate project-owned preheat indexes retired, both versions completed the same 2,670-element,
	six-chunk filter path without OOM or fallback in local quick-play smoke tests. Large-pack retained
	heap comparison remains required because the local fixture cannot reproduce the reported 2 GiB.
- 2026-09-01 — Compared released Forge JEI `15.49.0.199` and `15.49.0.200` jars. Only manifests
	and `GrindstoneRecipeMaker.class` differ. Version `.200` moved `isItemEnchantable` into
	`canEnchant`, bypassing the old call-site Mixin. Added mutually exclusive instruction-checked
	legacy/modern variants. Both released class hashes matched the Maven runtime artifacts; quick-play
	smoke selected legacy on `.199` and modern on `.200`, with both retaining 73 of 621 compatible
	items across 39 enchantments and completing JEI startup without injection errors or OOM.
- 2026-09-02 — Investigated `latest_tail.log`: 628 of 630 crafting errors came from JEI's debug-only
	second layout build after `isHandled=false`; two genuine recipe/indexing failures used separate
	branches and remain fully diagnosed. Six of nine plugin failures were wrong-thread JEI API calls.
	Async startup is capability-gated on the exact `PluginCaller.callOnPlugins -> Consumer.accept`
	instruction. The safe diagnostic applies
	only when the verified three-call layout and unhandled-category log marker both match. Added atomic
	ABI checks for Sophisticated Storage recipe-extension cache reuse and anvil representative Mixins,
	loader-specific grindstone repair checks, and generation/plugin-finally cleanup for anvil,
	grindstone, and SFM temporary contexts. Forge JEI 15.20, Forge JEI 15.49.0.200, and NeoForge JEI
	19.27 all completed quick-play with `wrong-thread=0`, `false broken-setRecipe=0`, and no critical
	injection failure. The remaining Tinkers Jewelry category omission, Supplementaries null color
	mapping, and AlmostFluidified null runtime are third-party state errors and remain visible.
- 2026-09-03 — Issues 6 and 7 identified five JEI plugins that call main-thread-only APIs from
	registration/runtime callbacks. Added `async.mainThreadPlugins` with those exact plugin IDs as
	defaults and support for user-added plugin IDs or whole-mod namespaces. A Forge JEI `15.20.0.120`
	smoke added `justenoughthreads:core` as a temporary custom entry: all of its callback bodies ran on
	`Render thread`, while unlisted `jei:minecraft` recipe registration remained on
	`justenoughthreads-start`. JEI completed in 3.726 seconds with no wrong-thread or Mixin errors.
	The test entry was removed after the run. Evidence:
	`build/benchmarks/jei-compat/forge-15.20.0.120.main-thread-plugin-list.debug.log`.
- 2026-09-03 — Investigated the 25,000-line `latest_tail.log` from Forge `1.20.1`, JEI
	`15.49.0.199`. Its 42 duplicate subtype errors all came from Storage in Motion iterating the same
	`SubtypeInterpreters` map already registered by Sophisticated Storage. A released-jar A/B with
	Storage in Motion `0.10.37.355`, Sophisticated Storage `1.4.86.2131`, and Sophisticated Core
	`1.3.84.2308` reproduced exactly 42 errors with both `asyncStartup=true` and `false`, excluding
	threading as the cause. The ABI-gated compatibility Mixin now skips only that duplicate map read;
	both asynchronous and synchronous client runs completed with zero duplicate subtype, plugin, or
	Mixin errors. Evidence: `forge-15.20.0.134.storage-in-motion-{async,sync,fixed-final,fixed-sync}.debug.log`.
- 2026-09-03 — The same log contained no JEI wrong-thread assertion. The other visible failures are
	third-party state, dependency, or data errors and remain unsuppressed: Avaritia Integration reads
	missing optional-module items while building its creative tab; Tinkers Jewelry EX lacks
	`AbstractMaterialStatsCategory`; Adams Ars Plus duplicates an Ars Nouveau `DyeRecipe` extension;
	Supplementaries receives a null color mapping; AlmostFluidified has no initialized runtime;
	JER treats four projectile entity types as living mobs; Apotheosis assembles an empty socketing
	recipe; Brewin' and Chewin' references a missing Farmer's Respite item; and All The Imbaium tooltip
	math divides by zero. PneumaticCraft's 12 no-world messages did not reproduce with its released
	`6.0.23` jar in an isolated async client. JEI's 42 `is running and has taken` ERROR lines are slow
	phase progress reports from `PluginCallerTimerRunnable`, not thrown exceptions.
- 2026-09-03 — Verified the default issue-7 route with released Collector's Reap `1.5.5` and
	Blueprint `7.1.4` on Forge JEI `15.20.0.120`. Its `registerRecipes` callback body ran on Render
	thread and successfully removed 23 item stacks and 21 fluid stacks. JEI completed in 7.241 seconds
	with zero wrong-thread, plugin, or Mixin failures. The unlisted Farmer's Delight callback remained
	on `justenoughthreads-start`. Evidence:
	`build/benchmarks/jei-compat/forge-15.20.0.120.collectors-reap-main-thread.debug.log`.
- 2026-09-03 — Verified the loader-specific built-in route on NeoForge JEI `19.27.0.340`.
	`jei:minecraft` ingredient registration and `jei:neoforge_gui` runtime registration executed on
	Render thread, while JEI startup coordination remained on `justenoughthreads-start`. Startup
	completed in 5.330 seconds with zero wrong-thread, plugin, or Mixin failures. Evidence:
	`build/benchmarks/jei-compat/neoforge-default.builtin-main-thread-phases.debug.log`.
- 2026-09-05 — Forced complete JEI artifacts for runtime overrides, added cross-loader artifact
	manifests, and guarded Minecraft Client task pumping from the JET startup thread. A controlled
	probe hit the guard once; final Forge `.199`/`.207`, NeoForge 19.27, and both Re:Avaritia clients
	completed with zero relevant runtime errors.
- 2026-09-05 — Audited an SDBF production Forge log. The original queue-race signature was absent,
	but `0.13.5` had failed closed because its custom ABI gate did not recognize Forge's production
	SRG name `m_7245_()Z`. Version `0.13.6` accepts that exact alias; production launch verification
	later confirmed the SRG enable marker and actual Mixin application. The clean run used JEI
	`15.21.0.148` and did not hit the guard, so the original `.199` trigger remains unreproduced.