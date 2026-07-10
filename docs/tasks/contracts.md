# JEI 异步优化 — Frozen Contracts

> Owner: PA-2 / agent2. Status: frozen for downstream stages.
> Source: [jei-async-optimization-design.md](jei-async-optimization-design.md), [task-plan.md](task-plan.md), [parallel-tasks.md](parallel-tasks.md).

本文冻结后续实现任务必须共享的命名、包结构、接口签名、配置键和安全边界。除非在 [parallel-tasks.md](parallel-tasks.md) 的 CR 表登记并通过，否则后续 agent 不应修改这些契约。

## 1. Scope

本契约适用于 Forge 1.20.1 / JEI 15.20.0.133 的 Mixin-only 异步优化项目。

必须满足：

- 不修改其他模组的 JEI Plugin。
- 不并行执行 JEI Plugin 回调。
- 不跳过插件、不关闭 JEI 功能、不限制 recipe 数量。
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
| `JeiOptExecutors` | Bounded worker executor and client-thread publish helper. |
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

Config file: `run/config/jei_optimize-client.toml`.

Implementation class: `com.tonywww.jeioptimize.config.JeiOptConfig`.

Feature facade: `com.tonywww.jeioptimize.config.JeiOptFeatureFlags`.

### 5.1 Boolean feature gates

| Key | Default before full validation | Disable semantics |
|---|---|---|
| `general.enabled` | `true` | If false, all mixin behavior no-ops or falls back to JEI baseline. |
| `diagnostics.pluginTiming` | `false` | Plugin timing mixin records nothing and emits no timing logs. |
| `diagnostics.registrationCounts` | `false` | Registration count mixins record nothing. |
| `syncOptimizations.cacheScope` | `false` | UID/string/sort caches are bypassed. |
| `syncOptimizations.batchIngredientFilterInit` | `false` | `IngredientFilter` constructor uses JEI baseline behavior. |
| `syncOptimizations.sortKeyCache` | `false` | Sort/tag helper cache is bypassed. |
| `syncOptimizations.delayCompact` | `false` | JEI compact runs at original timing. |
| `async.searchPreheat` | `false` | Search uses JEI baseline search path. |
| `async.snapshotChunking` | `false` | No tick-budgeted snapshot queue is scheduled. |
| `async.sortPreheat` | `false` | Sorting uses JEI baseline path. |
| `async.recipeFocusPreheat` | `false` | R/U focus lookup uses JEI baseline path. |
| `async.catalystPreheat` | `false` | Catalyst lookup uses JEI baseline path. |

Defaults are intentionally conservative. A later validation stage may change defaults through a CR after equivalence evidence exists.

### 5.2 Numeric config keys

| Key | Default | Bounds | Disable relation |
|---|---|---|---|
| `async.workerThreads` | `2` | `1..8` | Ignored when all async features are disabled. |
| `async.snapshotBudgetMs` | `2` | `1..10` | Ignored when `async.snapshotChunking=false`. |

### 5.3 Feature flag facade

Required methods:

```java
package com.tonywww.jeioptimize.config;

public final class JeiOptFeatureFlags {
    public static boolean enabled();
    public static boolean pluginTiming();
    public static boolean registrationCounts();
    public static boolean cacheScope();
    public static boolean batchIngredientFilterInit();
    public static boolean sortKeyCache();
    public static boolean delayCompact();
    public static boolean searchPreheat();
    public static boolean snapshotChunking();
    public static boolean sortPreheat();
    public static boolean recipeFocusPreheat();
    public static boolean catalystPreheat();
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

Publish rules:

- Worker results are published only through `JeiOptExecutors` to the client thread.
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

- Mixin class names must be recorded in [jei-targets.md](jei-targets.md) before being added to `jei_optimize.mixins.json`.
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
