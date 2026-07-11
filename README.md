# Just Enough Threads

A Minecraft mod for **Forge (1.20.1)** and **NeoForge (1.21.1)** that speeds up [JEI (Just Enough Items)](https://www.curseforge.com/minecraft/mc-mods/jei) startup by moving its heaviest work off the main thread and across CPU cores.

In large modpacks, JEI spends several seconds building its ingredient search index and processing recipes while the game sits on the loading screen. Just Enough Threads removes the biggest of those costs from the loading screen and parallelizes another, so you get into your world sooner.

## What it does

- **Off-thread ingredient filter build** — `asyncIngredientFilter` (on by default)

  JEI's "Building ingredient filter" step (its search index over every item and fluid) normally runs on the main thread and blocks loading for several seconds in large packs. This mod builds it on worker threads *after* you enter the world, then atomically swaps the finished index into JEI. The result is identical to JEI's own, and if the off-thread build fails for any reason it falls back to JEI's synchronous build.

  Visible effect: the JEI item list is empty for a few seconds after you enter the world, then fills in all at once.

- **Parallel vanilla recipe validation** — `parallelVanillaRecipes` (on by default)

  JEI's built-in plugin validates all vanilla-type recipes (crafting, smelting, stonecutting, and so on) one at a time. This mod runs that validation across CPU cores. The output is identical to JEI's sequential result and falls back to sequential validation on any error.

Every optimization sits behind a config flag and degrades safely to JEI's stock behavior. If anything looks wrong, set `enabled = false` to turn the whole mod off.

## Results

Measured in a large modpack (roughly 21,000 items and fluids and 34,000 vanilla-type recipes), entering the same world on the same machine with the optimizations off vs on:

| JEI startup timer | Optimizations off | Optimizations on |
|-------------------|-------------------|------------------|
| Building runtime | 5.18 s | 0.55 s |
| Starting JEI (total) | 10.7 s | 6.4 s |

The ingredient search index (the biggest single cost) moves off the main thread: JEI's on-thread "Building ingredient filter" step drops from several seconds to about 0.1 s, and the real index build (about 8 s here) runs on worker threads after you are already in the world.

Results vary with hardware and pack size. Measure your own with `scripts/measure-optimizations.ps1` (see [docs/optimization-measurement.md](docs/optimization-measurement.md)).

## Requirements

| Minecraft | Loader | JEI |
|-----------|--------|-----|
| 1.20.1 | Forge 47.4.4+ | 15.20.0.120 |
| 1.21.1 | NeoForge 21.1.x | 19.27.0.340 |

Client-side only. JEI is a required dependency.

Because the mod hooks JEI's internal classes, each build is tied to the JEI version above. A very different JEI build may move the internals it patches - keep JEI on the listed version (or rebuild the mod against your JEI).

## Configuration

Config file: `config/jei_optimize-client.toml`

| Option | Section | Default | Description |
|--------|---------|---------|-------------|
| `enabled` | general | `true` | Master switch. When `false`, the mod does nothing and JEI behaves normally. |
| `asyncIngredientFilter` | async | `true` | Build the ingredient search filter off-thread after world entry. |
| `parallelVanillaRecipes` | async | `true` | Validate vanilla recipes in parallel across CPU cores. |
| `workerThreads` | async | `2` | Worker-thread count for off-thread tasks (1-8). |
| `pluginTiming` | diagnostics | `false` | Log per-plugin, per-phase JEI startup timings (for measurement). |
| `registrationCounts` | diagnostics | `false` | Log per-plugin recipe and ingredient registration counts. |
| `disableAnvilRepairRecipes` | jeiContent | `false` | Hide JEI's generated anvil repair recipes (also skips generating them at startup). |
| `disableAnvilEnchantRecipes` | jeiContent | `false` | Hide JEI's generated anvil enchanting recipes for combining books (also skips generating them). |

Remaining flags in the file are experimental and off by default.

## Measuring the effect

`scripts/measure-optimizations.ps1` helps you run a before/after comparison of JEI startup timings in your own instance. See [docs/optimization-measurement.md](docs/optimization-measurement.md) for the full procedure.

## Building

Gradle must run on JDK 21. The Forge (1.20.1) target compiles to Java 17; the NeoForge (1.21.1) target compiles to Java 21.

    ./gradlew build

`build` compiles and jars **both loaders**. The jars land in each version's build folder:

    versions/1.20.1-forge/build/libs/jei_optimize-forge-<version>+1.20.1.jar
    versions/1.21.1-neoforge/build/libs/jei_optimize-neoforge-<version>+1.21.1.jar

### Publishing

`scripts/dryrun.ps1` and `scripts/publish.ps1` build every loader and push the release to CurseForge via the `mod-publish-plugin`:

    ./scripts/dryrun.ps1     # build both loaders, validate the pipeline, upload NOTHING
    ./scripts/publish.ps1    # build both loaders, then publish to CurseForge (asks to confirm)

The upload token is read from the `CURSEFORGE_TOKEN` environment variable, or `curseforge.token` in your user-level `~/.gradle/gradle.properties` (never commit it). The numeric `curseforge.projectId` lives in `gradle.properties`.

## How it works

The mod is Mixin-based and hooks JEI's own internal classes (`@Pseudo` mixins with `remap = false`), so it is tied to a specific JEI version. Every hook checks its config flag (and the master `enabled`) first; when a flag is off the hook is inert and JEI runs unchanged.

### Off-thread ingredient filter build (`asyncIngredientFilter`)

`IngredientFilterMixin` targets JEI's `IngredientFilter`:

1. **Skip the on-thread indexing.** A `@Redirect` on the per-ingredient `addIngredient` call inside the `IngredientFilter` constructor suppresses JEI's normal indexing loop when the feature is on, so the constructor returns almost immediately instead of building the search index on the main thread.
2. **Submit an off-thread build.** An `@Inject` at the end of the constructor calls `AsyncIngredientFilterBuilder.buildAsync(...)`, which submits a task to the worker pool. On a worker thread it creates a fresh, isolated search index with JEI's own `ElementPrefixParser` and `ElementSearch`, then runs `ElementSearch.addAll(...)` — the exact JEI code path, just off the main thread. Element visibility is computed on the same worker; this is safe because those elements are not yet reachable from the live filter.
3. **Swap it in on the main thread.** The same `@Inject` enqueues a finalize task into `JeiOptClientTickQueue`. Each client tick, `ClientTickHookMixin` (injected into `Minecraft.tick`) drains the queue. The finalize task polls the build without blocking; once it is ready it assigns the finished index into the filter's `elementSearch` field and calls `invalidateCache()`, so JEI rebuilds its visible list from the new index.

Because the new index is never shared with the main thread until the swap, there is no read/write race, and because it is built with JEI's own classes the result is identical to JEI's. If the worker build throws, the finalize task falls back to a synchronous build into the live filter (JEI's normal behavior). This is also why the item list is briefly empty after you spawn: the constructor returned an empty index, and it fills once the worker build swaps in.

### Parallel vanilla recipe validation (`parallelVanillaRecipes`)

`VanillaRecipesMixin` targets JEI's `VanillaRecipes`:

- **Crafting recipes.** An `@Inject` at the head of `getCraftingRecipes` hands off to `VanillaRecipeParallelBuilder`, which runs JEI's own `CategoryRecipeValidator` over a parallel stream of all crafting recipes and partitions them (handled vs special) with `Collectors.partitioningBy`. On an ordered stream this preserves recipe order and produces the same result JEI builds sequentially.
- **Other vanilla types.** A `@Redirect` turns the `List.stream()` call inside `getValidHandledRecipes` into `parallelStream()`, covering smelting, blasting, smoking, campfire, stonecutting and smithing.

The validator holds only final fields and its checks are read-only, so it is safe to share across threads; the output is identical to JEI's sequential result. If the parallel pass throws, it falls back to a sequential stream.

### Optional: hide anvil recipes (`disableAnvilRepairRecipes`, `disableAnvilEnchantRecipes`)

`AnvilRecipeControlMixin` injects at the head of JEI's `AnvilRecipeMaker.getRepairRecipes` and `getBookEnchantmentRecipes`. When the matching flag is on, that generator returns an empty stream, so those anvil recipes are never generated or shown. Both flags default off. This is a content choice rather than a transparent optimization, but it also removes their startup cost — anvil recipe generation is one of JEI's larger startup steps in big packs.

### Shared infrastructure

- **Worker pool** (`JeiOptExecutors`) — a small fixed pool of daemon threads (`workerThreads`, default 2) for off-thread builds, plus a helper for running work back on the main thread.
- **Client-tick work queue** (`JeiOptClientTickQueue` + `ClientTickHookMixin`) — a queue drained a little each client tick under a time budget, used to run main-thread finalize work (such as the index swap) a piece at a time instead of blocking a single frame.
- **Mixin registration** — all hooks are listed in `jei_optimize.mixins.json`. On Forge they are registered through Architectury Loom's `forge.mixinConfig`; on NeoForge through the `[[mixins]]` entry in `neoforge.mods.toml`. Either way this is what actually loads them in both the development and production environments.

### Diagnostics

`pluginTiming` and `registrationCounts` add per-plugin, per-phase JEI startup timing and registration-count logging. They are off by default and cost nothing when off; the measurement script turns them on to read the numbers.

## License

All Rights Reserved.
