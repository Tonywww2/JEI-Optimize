# Just Enough Threads

A Minecraft mod for **Forge (1.20.1)** and **NeoForge (1.21.1)** that speeds up [JEI (Just Enough Items)](https://www.curseforge.com/minecraft/mc-mods/jei) startup by moving its heaviest work off the main thread and across CPU cores.

In large modpacks, JEI spends several seconds building its ingredient search index and processing recipes while the game sits on the loading screen. Just Enough Threads removes the biggest of those costs from the loading screen and replaces expensive generated anvil and grindstone combinations with compact representative sets, so you get into your world sooner without losing recipe coverage.

## What it does

- **Responsive, serial JEI startup** — `asyncStartup` (on by default)

  JEI startup runs on one dedicated background thread, so the render thread can keep updating while
  recipes and the runtime are built. Plugin callbacks keep JEI's original order and are never run
  concurrently. Final `onRuntimeAvailable` callbacks return to the client thread before the runtime
  is published there, so plugins can safely use JEI's main-thread-only runtime APIs. Leaving the
  world, timing out, or losing the server cancels the active generation, interrupts its build, and
  prevents stale publication.

- **Off-thread ingredient filter build** — `asyncIngredientFilter` (on by default)

  JEI's "Building ingredient filter" step (its search index over every item and fluid) normally runs on the main thread and blocks loading for several seconds in large packs. This mod builds an isolated index in real chunks on worker threads after world entry. The inventory shows completed chunks in a progress bar; at 100%, the client atomically swaps the finished index and refreshes the sidebar once. If the worker build fails, it falls back to JEI's synchronous build.

  Visible effect: the game remains responsive while the bar advances, and the JEI item list appears complete in one update instead of repeatedly rebuilding partial pages.

  While this progress panel is visible, JEI-specific keyboard and mouse actions are temporarily
  ignored because JEI has registered those listeners but has not published the runtime they need.
  Normal inventory input remains available, and JEI controls activate automatically at completion.

- **Experimental recipe ingredient pre-resolution** — `parallelVanillaRecipes` (off by default)

  This option resolves recipe ingredient tags across worker threads before JEI reads them. It can help some packs, but custom recipes and lazy ingredient caches are not guaranteed to be thread-safe, so it is opt-in and should be benchmarked against the specific pack.

- **Coverage-preserving generated recipe compaction** — on by default

  JEI's synthetic anvil and grindstone examples are limited before expensive item-stack and menu
  simulation. Every applicable enchantment remains visible on up to three distinct item families,
  with up to 16 generic repair examples. When Iron's Spells 'n Spellbooks is installed, its Arcane
  Anvil imbuing matrix is reduced to a minimal representative set that still covers every eligible
  item and every spell level. The integration is optional and safely falls back if its API changes.

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

Config file: `config/justenoughthreads-client.toml`

When upgrading from an older release, `config/jei_optimize-client.toml` is copied automatically if
the new file does not exist. The legacy file is left untouched and never overwrites a new config.

| Option | Section | Default | Description |
|--------|---------|---------|-------------|
| `enabled` | general | `true` | Master switch. When `false`, the mod does nothing and JEI behaves normally. |
| `asyncStartup` | async | `true` | Run JEI startup serially on a dedicated background thread; cancel it on world exit, timeout, or server shutdown. |
| `asyncIngredientFilter` | async | `true` | Build the ingredient search filter off-thread in chunks, then publish the complete sidebar once. |
| `parallelVanillaRecipes` | async | `false` | Experimentally pre-resolve recipe ingredients across worker threads. |
| `workerThreads` | async | `4` | Worker-thread count for derived off-thread tasks (1-8). |
| `pluginTiming` | diagnostics | `false` | Log per-plugin, per-phase JEI startup timings (for measurement). |
| `registrationCounts` | diagnostics | `false` | Log per-plugin recipe and ingredient registration counts. |
| `stallWatchdog` | diagnostics | `true` | Sample and report code responsible for a JEI phase that exceeds the configured threshold. |
| `disableAnvilRepairRecipes` | jeiContent | `false` | Hide all generated anvil repair recipes. Overrides representative optimization for this recipe class. |
| `disableAnvilEnchantRecipes` | jeiContent | `false` | Hide all generated anvil enchanting recipes. Overrides representative optimization for this recipe class. |
| `optimizeAnvilRepresentatives` | jeiContent | `true` | Keep coverage-preserving anvil examples instead of every generated item combination. |
| `anvilRepresentativesPerEnchantment` | jeiContent | `3` | Maximum distinct item families retained for each anvil enchantment. |
| `anvilRepairRepresentatives` | jeiContent | `16` | Maximum generic material-repair examples retained for the anvil. |
| `optimizeGrindstoneRepresentatives` | jeiContent | `true` | Keep coverage-preserving examples on JEI versions that generate synthetic grindstone recipes. |
| `grindstoneRepresentativesPerEnchantment` | jeiContent | `3` | Maximum distinct item families retained for each removable enchantment. |
| `grindstoneRepairRepresentatives` | jeiContent | `16` | Maximum generic self-repair examples retained for the grindstone. |
| `compactIronsSpellsImbuing` | jeiContent | `true` | Compact optional Iron's Spells Arcane Anvil imbuing combinations while preserving every item and spell level. |
| `aggressiveCelestialForgeReinforce` | jeiContent | `false` | Limit Celestial Forge reinforce previews to representative item families. This can remove direct focus hits. |
| `aggressiveEmbersDawnstoneAnvil` | jeiContent | `false` | Limit compacted Embers anvil groups to representative tools. This can remove direct focus hits. |
| `aggressiveSfmFallingAnvil` | jeiContent | `false` | Limit SFM's unfiltered disenchantment overview per enchantment; focused queries retain the original path. |
| `aggressiveRepresentativesPerGroup` | jeiContent | `3` | Item-family limit for explicitly enabled aggressive integration modes. |
| `aggressiveGenericRepairRepresentatives` | jeiContent | `16` | Example limit for generic repair recipes in explicitly enabled aggressive modes. |
| `prefilterTinkersIngredients` | jeiContent | `false` | Remove `#tconstruct:modifiable` and `#tconstruct:parts` stacks before JEI builds its global item index. |
| `tinkersIngredientFilterAdditionalTags` | jeiContent | `""` | Comma-separated item tags added to the Tinkers prefilter; a leading `#` is optional. |

Remaining flags in the file are experimental and off by default.

## Measuring the effect

`scripts/measure-optimizations.ps1` helps you run a before/after comparison of JEI startup timings in your own instance. See [docs/optimization-measurement.md](docs/optimization-measurement.md) for the full procedure.

## Building

Gradle must run on JDK 21. The Forge (1.20.1) target compiles to Java 17; the NeoForge (1.21.1) target compiles to Java 21.

    ./gradlew build

`build` compiles and jars **both loaders**. The jars land in each version's build folder:

    versions/1.20.1-forge/build/libs/justenoughthreads-<version>+1.20.1.jar
    versions/1.21.1-neoforge/build/libs/justenoughthreads-<version>+1.21.1.jar

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
2. **Build real chunks off-thread.** An `@Inject` at the end of the constructor calls `AsyncIngredientFilterBuilder.buildChunkedAsync(...)`. The worker creates a fresh, isolated search index and adds `ingredientFilterChunkSize` elements per chunk, updating the visible progress only after each complete chunk. JEI 15.20/19.27 use their bulk `addAll` API; JEI 15.48 uses its compatible per-element `add` API.
3. **Publish once on the main thread.** A finalize task in `JeiOptClientTickQueue` polls the build without blocking. At 100%, it assigns the finished index to the filter and calls `invalidateCache()` exactly once. The JEI startup thread waits for this publication before runtime-available callbacks and final runtime publication, while the render thread keeps ticking and drawing the progress bar.

Because the new index is never shared with the main thread until the swap, JEI never serves a partially built sidebar. The deferred client-tick path follows the same isolated-index rule and no longer invalidates the sidebar after every chunk.

JEI installs its container input listeners before `Internal.setRuntime(...)`. `JeiClientInputGuardMixin`
returns "not handled" from those listeners while startup progress is active, preventing early R/U,
click, scroll, or drag handling from reading a missing runtime without blocking vanilla screen input.
JEED 1.20 also exposes effect clicks before its JEI helpers and runtime are ready, so an optional
ABI-checked guard ignores only that effect activation until the current runtime is published. It
does not intercept Minecraft's global mouse handler, and ordinary inventory slots remain usable
while JEI loads. Other screen extensions need similarly narrow compatibility guards if they access
unpublished JEI state.

### Experimental recipe ingredient pre-resolution (`parallelVanillaRecipes`)

`VanillaRecipesMixin` targets JEI's `VanillaRecipes`:

- At the head of `getCraftingRecipes`, `VanillaRecipeWarmup` snapshots the recipe manager and asks worker threads to resolve each recipe's ingredient tags before JEI performs its normal validation.
- JEI still validates, orders and registers every recipe on the main thread; the worker results are discarded after populating Minecraft's lazy ingredient caches.
- Modded `Recipe` implementations and ingredient caches may perform mutable or main-thread-only work. The feature therefore defaults off and should only be enabled after testing the target modpack.

### Generated recipe compaction

The anvil and grindstone hooks filter candidates before JEI performs its expensive output or menu
simulation. Selection is stable and keeps distinct item families where possible, so each applicable
enchantment remains discoverable without generating the full item-by-enchantment matrix. Generic
repair examples use separate limits. Setting either legacy `disableAnvil*Recipes` option to `true`
still skips that entire generator and takes precedence over representative selection.

Iron's Spells support is loaded only when its JEI classes are present. Its item-by-spell-level matrix
is reduced from $W \times S$ combinations to $\max(W, S)$ representative recipes while covering all
$W$ eligible items and all $S$ spell levels. No hard dependency on Iron's Spells is added.

### Shared infrastructure

- **Startup executor** (`JeiOptExecutors`) — a single-flight daemon executor for serial JEI startup. Each start has a generation token; stop cancels and interrupts it. Runtime callbacks and publication are generation-checked on the client thread.
- **Worker pool** (`JeiOptExecutors`) — a small fixed pool of daemon threads (`workerThreads`, default 4) for derived off-thread builds, plus a helper for running work back on the main thread.
- **Client-tick work queue** (`JeiOptClientTickQueue` + `ClientTickHookMixin`) — a queue drained a little each client tick under a time budget, used to run main-thread finalize work (such as the index swap) a piece at a time instead of blocking a single frame.
- **Mixin registration** — all hooks are listed in `justenoughthreads.mixins.json`. On Forge they are registered through Architectury Loom's `forge.mixinConfig`; on NeoForge through the `[[mixins]]` entry in `neoforge.mods.toml`. Either way this is what actually loads them in both the development and production environments.

### Diagnostics

`pluginTiming` and `registrationCounts` add per-plugin, per-phase JEI startup timing and registration-count logging. They are off by default and cost nothing when off; the measurement script turns them on to read the numbers.

## License

All Rights Reserved.
