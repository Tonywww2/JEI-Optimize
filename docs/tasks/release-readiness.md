# JEI 异步优化 — Release Readiness

> Owner: PH-3. Scope: summarize current implementation safety, disabled features, validation evidence, and remaining risks.

## 1. Current Readiness Status

Status: **not release-ready; smoke-test ready only**.

Reason: the project compiles and `runClient` starts successfully with the current mixin wiring, but the full feature-equivalence matrix in [validation.md](validation.md) is not complete. The current state is suitable for local smoke testing and targeted validation, not for distribution to a modpack.

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
| Client smoke | Pass | `runClient` exited with code 0 in latest context. |
| Forge startup | Pass | Forge 47.4.4 reached client startup in prior logs. |
| JEI runtime present | Pass | JEI jar discovered and `jei:textures/atlas/gui.png-atlas` loaded in prior logs. |
| Missing mixin class errors | Not observed | Latest `runClient` completed successfully after mixin JSON wiring. |
| Invalid mixin target errors | Not observed | Latest `runClient` completed successfully after mixin JSON wiring. |

## 3. Mixin Wiring State

Current [jei_optimize.mixins.json](../../src/main/resources/jei_optimize.mixins.json) wires:

- `PluginCallerMixin`
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

The current config contract intentionally defaults most behavior-changing features to disabled until equivalence evidence exists.

| Feature area | Config key | Default | Readiness |
|---|---|---|---|
| Master switch | `general.enabled` | true | Safe as a master gate. |
| Plugin timing | `diagnostics.pluginTiming` | false | Safe to enable for diagnostics; needs output validation. |
| Registration counts | `diagnostics.registrationCounts` | false | Safe to enable for diagnostics; needs output validation. |
| One-start cache | `syncOptimizations.cacheScope` | false | Not release-enabled until search/R/U/catalyst equivalence is checked. |
| IngredientFilter batch init | `syncOptimizations.batchIngredientFilterInit` | false | Not release-enabled until ingredient list/search equivalence is checked. |
| Sort key cache | `syncOptimizations.sortKeyCache` | false | Not release-enabled until order equivalence is checked. |
| Delayed compact | `syncOptimizations.delayCompact` | false | Not release-enabled until recipe query equivalence is checked. |
| Search preheat | `async.searchPreheat` | false | Not release-enabled until search matrix passes. |
| Snapshot chunking | `async.snapshotChunking` | false | Not release-enabled until lifecycle/stale publish checks pass. |
| Sort preheat | `async.sortPreheat` | false | Not release-enabled until sort order equivalence is checked. |
| Recipe focus preheat | `async.recipeFocusPreheat` | false | Not release-enabled until R/U equivalence passes. |
| Catalyst preheat | `async.catalystPreheat` | false | Not release-enabled until catalyst equivalence passes. |

Release posture: users can keep all behavior-changing switches disabled. That state should behave close to baseline while still allowing smoke of config and mixin loading.

## 5. Remaining Validation Gaps

The following checks from [validation.md](validation.md) remain open:

| Area | Gap | Blocks release? |
|---|---|---|
| Search equivalence | Display, `@`, `#`, `$`, `%`, `&`, `^` query matrices are not filled. | Yes for search features. |
| Recipe lookup | R/U result comparison is not filled. | Yes for recipe focus features. |
| Catalyst lookup | Catalyst click/lookup comparison is not filled. | Yes for catalyst feature. |
| Recipe transfer | Transfer behavior comparison is not filled. | Yes for any recipe query release claim. |
| Lifecycle | Logout/re-enter, resource reload, and stale publish checks are not filled. | Yes for async release claim. |
| Disabled path | `general.enabled=false` and each per-feature disabled path are not fully recorded. | Yes. |

## 6. Remaining Risks

| Risk | Level | Current mitigation | Release decision |
|---|---|---|---|
| Feature-equivalence not proven | High | Features default false; validation checklist exists. | Do not release with features enabled. |
| Async query paths may return incomplete data if misconfigured | High | Fallback paths exist by design; validation incomplete. | Keep async features off by default. |
| Mixin target drift in future JEI versions | Medium | Target evidence recorded for JEI 15.20.0.133 only. | Scope release to exact JEI version unless reverified. |
| Delayed compact off-thread behavior | Medium | Feature default false. | Do not enable without recipe query validation. |
| Reflection-based internals in mixins | Resolved | Source scan found no `java.lang.reflect`, `Class.forName`, `getDeclared*`, `setAccessible`, or reflective `invoke` usage under `src/main/java`. | No release blocker from reflection remains. |
| Diagnostics overhead | Low | Diagnostics default false. | Safe for opt-in profiling. |

## 7. Safe Current Usage

Safe for:

- Local development.
- `compileJava` checks.
- `runClient` smoke testing.
- Opt-in diagnostics experiments.
- Filling [validation.md](validation.md) matrices.

Not yet safe for:

- Distribution to a large modpack with optimization features enabled.
- Claiming JEI search/R/U/catalyst equivalence.
- Enabling async features by default.
- Enabling delayed compact by default.

## 8. Required Before Release

1. Complete PH-1 validation tables in [validation.md](validation.md).
2. Complete PH-2 final docs writeback after validation results are known.
3. Run `runClient` with `general.enabled=false` and confirm baseline/no-op behavior.
4. Run `runClient` with each feature enabled individually and confirm no startup crash.
5. Compare search results for all query prefixes listed in validation matrix.
6. Compare R/U/catalyst/recipe transfer behavior against baseline.
7. Test logout/re-enter and resource reload while async tasks are active.
8. Decide which features, if any, can default to true based on evidence.

## 9. Final Gate Summary

| Gate | Result | Notes |
|---|---|---|
| Build gate | Pass | `compileJava` passes in latest context. |
| Smoke gate | Pass | `runClient` passes in latest context. |
| Mixin load gate | Pass | No missing/invalid mixin errors observed in latest context. |
| Equivalence gate | Not complete | Validation matrices are still open. |
| Release gate | Blocked | Build/smoke/reflection gates pass; blocked only on manual equivalence matrix execution for search, R/U, catalyst, transfer, logout/re-enter, reload, and disabled paths. |

## Revision Log

- 2026-07-10 — Initial PH-3 release readiness summary created.
- 2026-07-10 — Final acceptance update: reflection removed from source, `compileJava` passed, `runClient` passed; release remains blocked only on manual equivalence matrix execution.