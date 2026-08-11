# JEI 异步优化 — Frozen Contracts

> Owner: PA-2 / agent2. Status: frozen for downstream stages.
> Source: [jei-async-optimization-design.md](jei-async-optimization-design.md), [task-plan.md](task-plan.md), [parallel-tasks.md](parallel-tasks.md).

本文冻结后续实现任务必须共享的命名、包结构、接口签名、配置键和安全边界。除非在 [parallel-tasks.md](parallel-tasks.md) 的 CR 表登记并通过，否则后续 agent 不应修改这些契约。

## 1. Scope

本契约适用于 Forge 1.20.1 / JEI 15.20.0.133 的 Mixin-only 异步优化项目。

必须满足：

- 不修改其他模组的 JEI Plugin。
- 不并行执行 JEI Plugin 回调。
- 不跳过插件、不限制 recipe 数量。生成式铁砧内容仅由独立、可关闭的内容选项控制。
- 不引入本地跨世界缓存或磁盘持久化缓存。
- 每一个功能都必须能通过配置文件关闭。
- 禁用某功能时必须 no-op 或回到 JEI 原始路径。

## 2. Package Layout

| Package | Purpose | Owning stage |
|---|---|---|
| `com.tonywww.jeioptimize` | Mod entry and shared constants. | PB-4 |
| `com.tonywww.jeioptimize.config` | Forge client config and feature flag facade. | PB-4 |
| `com.tonywww.jeioptimize.runtime` | Generation lifecycle, executors, task registry, one-start cache scope, client tick queue. | PB-1/PB-2/PD-1/PE-1/PE-3 |
| `com.tonywww.jeioptimize.index` | Async index abstractions and concrete async indexes. | PB-3/PF/PG |
| `com.tonywww.jeioptimize.snapshot` | Immutable snapshot records and snapshot builders. | PB-3/PE-2/PG-1 |
| `com.tonywww.jeioptimize.instrumentation` | Diagnostics, counters, plugin call context. | PC-1/PC-2 |
| `com.tonywww.jeioptimize.mixin` | Mixin classes only. | PC/PD/PF/PG |
| `com.tonywww.jeioptimize.mixin.accessor` | Accessor and invoker mixins. | PD/PF/PG |
| `com.tonywww.jeioptimize.mixin.registration` | Registration count mixins. | PC-2 |

## 3. Frozen Class Names

| Class | Required responsibility |
|---|---|
| `JeiOptimize` | Forge mod entry; registers `JeiOptConfig`. |
| `JeiOptConfig` | Defines and registers Forge client config. |
| `JeiOptFeatureFlags` | Read-only facade for all feature checks used by mixins. |
| `JeiOptRuntimeState` | Generation id, lifecycle invalidation, pending task cancellation. |
| `JeiOptExecutors` | Bounded worker executor, single-flight JEI startup executor, and client-thread publish helper. |
| `JeiOptTaskRegistry` | Tracks futures/tasks for cancellation and generation-safe publishing. |
| `JeiOptClientTickQueue` | Budgeted client-thread snapshot work queue. |
| `JeiOptCacheScope` | One-start in-memory cache, cleared on stop/reload. |
| `AsyncIndexState` | Async index state enum. |
| `AsyncIndex<T>` | Shared async index contract. |
| `IngredientSearchSnapshot` | Immutable per-ingredient search snapshot. |
| `RecipeIndexSnapshot` | Immutable per-recipe role UID snapshot. |
| `IngredientSearchSnapshotBuilder` | Client-thread search snapshot extraction. |
| `RecipeIndexSnapshotBuilder` | Client-thread recipe role UID extraction. |
| `AsyncSearchIndex` | Worker-built prefix search index. |
| `AsyncSortIndex` | Worker-built sort result. |
| `AsyncRecipeFocusIndex` | Worker-built R/U recipe focus map. |
| `AsyncCatalystIndex` | Worker-built catalyst map. |
| `JeiOptDiagnostics` | Timing and count reporting. |
| `JeiPluginCallContext` | Current plugin UID context for diagnostics. |

## 4. Frozen Interfaces

### 4.1 AsyncIndexState

```java
package com.tonywww.jeioptimize.index;

public enum AsyncIndexState {
    NOT_STARTED,
    SNAPSHOTTING,
    BUILDING,
    READY,
    FAILED
}
```

### 4.2 AsyncIndex

```java
package com.tonywww.jeioptimize.index;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface AsyncIndex<T> {
    AsyncIndexState state();

    CompletableFuture<T> future();

    Optional<T> readyValue();

    T awaitOrFallback(Supplier<T> fallback);
}
```

Rules:

- `awaitOrFallback` must never return partial data.
- If interrupted or failed, it must call the supplied fallback unless the caller explicitly handles failure.
- Implementations must not publish to JEI runtime from worker threads.

### 4.3 IngredientSearchSnapshot

```java
package com.tonywww.jeioptimize.snapshot;

import java.util.List;

public record IngredientSearchSnapshot(
    Object uid,
    List<String> names,
    List<String> modNames,
    List<String> modIds,
    List<String> tooltipStrings,
    List<String> tagStrings,
    List<String> creativeTabStrings,
    List<String> colorStrings,
    String resourceLocation,
    boolean visible,
    int createdIndex
) {}
```

Rules:

- All lists must be immutable or treated as immutable after construction.
- `uid` must be stable only for the current JEI start lifecycle.
- Snapshot creation happens on the client thread unless PA-1 proves a field is safe off-thread.

### 4.4 RecipeIndexSnapshot

```java
package com.tonywww.jeioptimize.snapshot;

import java.util.Map;
import java.util.Set;

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

public record RecipeIndexSnapshot<T>(
    RecipeType<T> recipeType,
    T recipe,
    Map<RecipeIngredientRole, Set<Object>> roleToIngredientUids,
    boolean hidden
) {}
```

Rules:

- `recipe` is carried only as an identity/reference for current runtime structures; it is not serialized or persisted.
- `roleToIngredientUids` is immutable after construction.
- `IRecipeCategory.setRecipe` and `IIngredientHelper.getUniqueId` remain on the client thread unless PA-1 proves otherwise.

## 5. Configuration Contract

Config file: `run/config/justenoughthreads-client.toml`.

Implementation class: `com.tonywww.jeioptimize.config.JeiOptConfig`.

Feature facade: `com.tonywww.jeioptimize.config.JeiOptFeatureFlags`.

### 5.1 Boolean feature gates

| Key | Default before full validation | Disable semantics |
|---|---|---|
| `general.enabled` | `true` | If false, all mixin behavior no-ops or falls back to JEI baseline. |
| `diagnostics.pluginTiming` | `false` | Plugin timing mixin records nothing and emits no timing logs. |
| `diagnostics.registrationCounts` | `false` | Registration count mixins record nothing. |
| `diagnostics.stallWatchdog` | `true` | No long-phase stack sampling is performed. |
| `jeiContent.disableAnvilRepairRecipes` | `true` | JEI generates and displays material-repair recipes. |
| `jeiContent.disableAnvilEnchantRecipes` | `true` | JEI generates and displays enchanted-book recipes. |
| `syncOptimizations.cacheScope` | `true` | UID/string/sort caches are bypassed. |
| `syncOptimizations.batchIngredientFilterInit` | `true` | `IngredientFilter` constructor uses JEI baseline behavior. |
| `syncOptimizations.sortKeyCache` | `true` | Sort/tag helper cache is bypassed. |
| `syncOptimizations.delayCompact` | `true` | JEI compact runs at original timing. |
| `async.searchPreheat` | `false` | Search uses JEI baseline search path. |
| `async.snapshotChunking` | `true` | No tick-budgeted snapshot queue is scheduled. |
| `async.sortPreheat` | `true` | Sorting uses JEI baseline path. |
| `async.recipeFocusPreheat` | `true` | R/U focus lookup uses JEI baseline path. |
| `async.catalystPreheat` | `true` | Catalyst lookup uses JEI baseline path. |
| `async.deferredIngredientFilter` | `true` | Ingredient-filter construction is not deferred. |
| `async.asyncIngredientFilter` | `true` | JEI builds its ingredient filter synchronously. |
| `async.parallelVanillaRecipes` | `false` | Recipe ingredients are not pre-resolved on workers. |
| `async.asyncStartup` | `true` | `JeiStarter.start()` runs on its caller thread. |

Defaults reflect the shipped configuration after validation and change-control approval. Any later
default change still requires a CR.

### 5.2 Numeric config keys

| Key | Default | Bounds | Disable relation |
|---|---|---|---|
| `async.workerThreads` | `4` | `1..8` | Ignored when all async features are disabled. |
| `async.snapshotBudgetMs` | `2` | `1..10` | Ignored when `async.snapshotChunking=false`. |

### 5.3 Feature flag facade

Required methods:

```java
package com.tonywww.jeioptimize.config;

public final class JeiOptFeatureFlags {
    public static boolean enabled();
    public static boolean pluginTiming();
    public static boolean registrationCounts();
    public static boolean stallWatchdog();
    public static boolean disableAnvilRepairRecipes();
    public static boolean disableAnvilEnchantRecipes();
    public static boolean cacheScope();
    public static boolean batchIngredientFilterInit();
    public static boolean sortKeyCache();
    public static boolean delayCompact();
    public static boolean searchPreheat();
    public static boolean snapshotChunking();
    public static boolean sortPreheat();
    public static boolean recipeFocusPreheat();
    public static boolean catalystPreheat();
    public static boolean deferredIngredientFilter();
    public static boolean asyncIngredientFilter();
    public static boolean parallelVanillaRecipes();
    public static boolean asyncStartup();
    public static int workerThreads();
    public static int snapshotBudgetMs();
}
```

Rules:

- Every boolean method except `enabled()` must return `enabled() && specificConfigValue`.
- Mixins must call the specific method closest to their feature.
- No mixin may read `JeiOptConfig` fields directly.

## 6. Threading & Safety Contract

Worker threads may process only:

- Immutable snapshot records.
- `String`, primitive values, boxed primitives.
- Ingredient UID / recipe UID values scoped to current start.
- Immutable `List`, `Map`, `Set` built before submission.

Worker threads must not call:

- `IModPlugin`.
- `IRecipeCategory`.
- `IIngredientHelper`.
- `IIngredientRenderer`.
- `Minecraft`, `ClientLevel`, `Screen`.
- Mutable `ItemStack` logic.
- JEI registration objects.

`async.asyncStartup` is the single controlled exception to the `IModPlugin` worker rule. It moves
JEI's build and registration sequence onto one dedicated startup thread; it does not use the worker
pool and does not run plugin callbacks concurrently. The exception requires all of the following:

- At most one JEI startup body executes at a time.
- Plugin callback order and JEI exception behavior remain unchanged.
- Every start owns a generation token and checks cancellation between plugin callbacks.
- Stop invalidates the generation, interrupts the startup thread, and cancels derived tasks.
- The final `IModPlugin.onRuntimeAvailable` callback batch executes serially on the client thread.
- Ingredient-filter chunks build only into an isolated search index. Chunk completion may update
    progress, but must not mutate the live filter or invalidate its sidebar cache.
- The client thread swaps the completed search index and invalidates the sidebar exactly once
    before `onRuntimeAvailable` callbacks and runtime publication.
- JEI screen input callbacks must return "not handled" while startup progress is active because
    JEI registers them before its runtime exists. The guard must not cancel vanilla screen input and
    must become inert immediately after runtime publication completes.
- The runtime is published on the client thread only when its generation is still current.
- Packs containing an earlier registration callback that requires the client thread can disable
    `asyncStartup` to restore JEI's caller-thread startup path.

Publish rules:

- Worker results are published only through `JeiOptExecutors` to the client thread.
- The JEI startup thread may wait for the ingredient-filter publication gate; the client/render
    thread must never wait on that gate.
- Calls to `Minecraft.getProfiler()` from the dedicated JEI startup thread must return
    `InactiveProfiler.INSTANCE`. The render thread's mutable `ActiveProfiler` map must never be
    shared with startup callbacks because `FilledProfileResults` iterates it without synchronization.
    This is a containment boundary; a profiler CME alone does not identify the specific writer.
- Every publish checks `JeiOptRuntimeState.isCurrent(generation)`.
- Stop/reload invalidates generation and cancels pending tasks.
- Disabled features must cancel or ignore existing feature-specific tasks.

## 7. Cache Contract

Allowed:

- One-start in-memory UID cache.
- One-start in-memory search string cache.
- One-start in-memory sort/tag helper cache.
- One-start in-memory snapshot/index builder intermediates.

Forbidden:

- Disk cache.
- Cross-world cache.
- Cross-server cache.
- Serialized prefix index cache.
- Serialized recipe UID cache.

Lifecycle:

- Cache scope is created during JEI start only if `syncOptimizations.cacheScope=true`.
- Cache scope is cleared on JEI stop, reload/restart, or generation invalidation.
- Cache data must not be static without generation scoping.

## 8. Diagnostics Contract

- Diagnostics are off by default.
- `diagnostics.pluginTiming` controls timing logs.
- `diagnostics.registrationCounts` controls count collection.
- Diagnostics must not catch, suppress, or transform plugin exceptions beyond JEI baseline behavior.
- Diagnostics must not call plugin APIs more than JEI already calls them.

## 9. Mixin Contract

- Mixin class names must be recorded in [jei-targets.md](jei-targets.md) before being added to `justenoughthreads.mixins.json`.
- Every behavior-changing injection starts with a feature flag check.
- If feature is disabled, injection must return without changing JEI behavior.
- Accessors/invokers live under `com.tonywww.jeioptimize.mixin.accessor`.
- Registration diagnostics mixins live under `com.tonywww.jeioptimize.mixin.registration`.

## 10. Change Control

Changing any of the following requires a CR in [parallel-tasks.md](parallel-tasks.md):

- Package names.
- Frozen class names.
- `AsyncIndex` signature.
- Snapshot record fields.
- Config key names or defaults.
- Worker safety rules.
- Cache lifecycle rules.

## 11. PA-2 Acceptance Checklist

- ☑ Package layout frozen.
- ☑ Class names frozen.
- ☑ Async interface signatures frozen.
- ☑ Snapshot record fields frozen.
- ☑ Config keys and disable semantics frozen.
- ☑ Worker safety rules frozen.
- ☑ Cache boundaries frozen.
- ☑ Change control process stated.
