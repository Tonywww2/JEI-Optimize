# JEI 异步优化 — Release Readiness

> Owner: PH-3. Scope: summarize current implementation safety, disabled features, validation evidence, and remaining risks.

## 1. Current Readiness Status

Status: **implementation-ready for targeted testing; large-pack release validation pending**.

Reason: dual-loader builds and normal startup smoke tests pass, and world-stop cancellation has been
verified before runtime publication. The full feature-equivalence matrix and repeated 1800/1905-mod
pack lifecycle runs are still pending.

## 2. Verified Build / Smoke Evidence

Commands used with Java 21 Gradle runtime:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon runClient
```

Observed evidence:

| Check | Status | Evidence |
|---|---|---|
| Java compilation | Pass | `compileJava` exited with code 0 in latest context. |
| Client smoke | Pass | Normal startup completed on Forge JEI 15.20/15.48 and NeoForge JEI 19.27. |
| Forge startup | Pass | Forge 47.4.4 reached JEI runtime on both tested JEI generations. |
| Stop during startup | Pass | A real `JeiStarter.stop()` cancelled the active generation and prevented runtime publication. |
| JEI runtime present | Pass | JEI jar discovered and `jei:textures/atlas/gui.png-atlas` loaded in prior logs. |
| Missing mixin class errors | Not observed | Latest `runClient` completed successfully after mixin JSON wiring. |
| Invalid mixin target errors | Not observed | Latest `runClient` completed successfully after mixin JSON wiring. |

## 3. Mixin Wiring State

Current [jei_optimize.mixins.json](../../src/main/resources/jei_optimize.mixins.json) wires:

- `PluginCallerMixin`
- `JeiStarterMixin`
- `JeiStarterPublishLegacyMixin` or `JeiStarterPublishModernMixin` (selected from JEI members)
- `registration.IngredientManagerBuilderRegistrationCountMixin`
- `registration.RecipeCatalystRegistrationCountMixin`
- `registration.RecipeCategoryRegistrationCountMixin`
- `registration.RecipeRegistrationCountMixin`
- `IngredientFilterMixin`
- `IngredientSorterMixin`
- `RecipeManagerInternalCompactMixin`
- `ElementSearchMixin`
- `AsyncIngredientSorterMixin`
- `RecipeMapCatalystMixin`
- client: `ClientTickHookMixin`

Smoke result: configured mixins load without missing or invalid mixin errors in the latest `runClient` context.

## 4. Default Feature Safety

The current config contract reflects the shipped defaults. Features with remaining manual
equivalence gaps are called out explicitly below.

| Feature area | Config key | Default | Readiness |
|---|---|---|---|
| Master switch | `general.enabled` | true | Safe as a master gate. |
| Plugin timing | `diagnostics.pluginTiming` | false | Safe to enable for diagnostics; needs output validation. |
| Registration counts | `diagnostics.registrationCounts` | false | Safe to enable for diagnostics; needs output validation. |
| Stall watchdog | `diagnostics.stallWatchdog` | true | Observational; does not change plugin order or exceptions. |
| One-start cache | `syncOptimizations.cacheScope` | true | Shipped default; lifecycle-scoped. Full manual equivalence remains open. |
| IngredientFilter batch init | `syncOptimizations.batchIngredientFilterInit` | true | Shipped default; manual search matrix remains open. |
| Sort key cache | `syncOptimizations.sortKeyCache` | true | Shipped default; manual ordering check remains open. |
| Delayed compact | `syncOptimizations.delayCompact` | true | Shipped default; manual R/U check remains open. |
| Search preheat | `async.searchPreheat` | false | Not release-enabled until search matrix passes. |
| Snapshot chunking | `async.snapshotChunking` | true | Shipped default; world-stop cancellation passes. Reload/second-world checks remain open. |
| Sort preheat | `async.sortPreheat` | true | Shipped default; manual sort equivalence remains open. |
| Recipe focus preheat | `async.recipeFocusPreheat` | true | Shipped default; manual R/U equivalence remains open. |
| Catalyst preheat | `async.catalystPreheat` | true | Shipped default; manual catalyst equivalence remains open. |
| Parallel recipe pre-resolution | `async.parallelVanillaRecipes` | false | Keep disabled; modded recipes and lazy caches may be unsafe. |
| Serial background startup | `async.asyncStartup` | true | Cross-version smoke and deterministic stop-cancellation pass; large-pack repetition pending. |

Release posture: do not reintroduce parallel plugin dispatch. `asyncStartup` remains independently
disableable for plugins that require JEI's original caller thread.

## 5. Remaining Validation Gaps

The following checks from [validation.md](validation.md) remain open:

| Area | Gap | Blocks release? |
|---|---|---|
| Search equivalence | Display, `@`, `#`, `$`, `%`, `&`, `^` query matrices are not filled. | Yes for search features. |
| Recipe lookup | R/U result comparison is not filled. | Yes for recipe focus features. |
| Catalyst lookup | Catalyst click/lookup comparison is not filled. | Yes for catalyst feature. |
| Recipe transfer | Transfer behavior comparison is not filled. | Yes for any recipe query release claim. |
| Lifecycle | Exit-during-start passes; logout/re-enter and resource reload checks remain open. | Yes for universal async release claim. |
| Disabled path | `general.enabled=false` and each per-feature disabled path are not fully recorded. | Yes. |

## 6. Remaining Risks

| Risk | Level | Current mitigation | Release decision |
|---|---|---|---|
| Feature-equivalence not fully proven | High | Validation checklist and per-feature disable paths exist. | Complete manual search/R/U/catalyst checks before the next release claim. |
| Async query paths may return incomplete data if misconfigured | High | Generation checks and fallback paths exist; validation incomplete. | Keep per-feature kill switches documented. |
| A plugin requires the render thread | High | Plugin callbacks remain serial; `asyncStartup=false` restores JEI's original caller-thread path. | Do not claim universal compatibility before large-pack testing. |
| Mixin target drift in future JEI versions | Medium | Runtime member selection verified on JEI 15.20, 15.48, and 19.27. | Scope release to verified ranges unless rechecked. |
| Delayed compact behavior | Medium | Work remains on the main thread and is config-gated. | Complete recipe query validation. |
| Reflection-based internals in mixins | Resolved | Source scan found no `java.lang.reflect`, `Class.forName`, `getDeclared*`, `setAccessible`, or reflective `invoke` usage under `src/main/java`. | No release blocker from reflection remains. |
| Diagnostics overhead | Low | Diagnostics default false. | Safe for opt-in profiling. |

## 7. Safe Current Usage

Safe for:

- Local development.
- `compileJava` checks.
- `runClient` smoke testing.
- Targeted Forge/NeoForge testing with the shipped defaults.
- Opt-in diagnostics experiments.
- Filling [validation.md](validation.md) matrices.

Not yet safe for:

- Claiming universal large-modpack compatibility for `asyncStartup` before repeated lifecycle tests.
- Claiming JEI search/R/U/catalyst equivalence.

## 8. Required Before Release

1. Complete PH-1 validation tables in [validation.md](validation.md).
2. Complete PH-2 final docs writeback after validation results are known.
3. Run `runClient` with `general.enabled=false` and confirm baseline/no-op behavior.
4. Run `runClient` with each feature enabled individually and confirm no startup crash.
5. Compare search results for all query prefixes listed in validation matrix.
6. Compare R/U/catalyst/recipe transfer behavior against baseline.
7. Test logout/re-enter and resource reload while async tasks are active.
8. Repeat cold start, exit-during-start, and second-world tests in the 1800- and 1905-mod packs.

## 9. Final Gate Summary

| Gate | Result | Notes |
|---|---|---|
| Build gate | Pass | `compileJava` passes in latest context. |
| Smoke gate | Pass | `runClient` passes in latest context. |
| Mixin load gate | Pass | No missing/invalid mixin errors observed in latest context. |
| Stop-cancellation gate | Pass | Active startup was interrupted and acknowledged cancellation before runtime publication. |
| Equivalence gate | Not complete | Validation matrices are still open. |
| Release gate | Blocked | Build/smoke/stop gates pass; blocked on manual equivalence, second-world/reload, disabled-path, and large-pack repetition. |

## Revision Log

- 2026-07-10 — Initial PH-3 release readiness summary created.
- 2026-07-10 — Final acceptance update: reflection removed from source, `compileJava` passed, `runClient` passed; release remains blocked only on manual equivalence matrix execution.