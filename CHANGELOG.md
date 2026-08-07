# Changelog

All notable changes to Just Enough Threads are documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.8.0

A responsiveness and correctness release. JEI startup can now run serially on a cancellable
background thread without making third-party plugin callbacks concurrent, and stale startup work
can no longer publish after leaving a world.

### Added

- **Responsive serial JEI startup.** JEI can build on one dedicated background thread while the
  render thread remains responsive. Plugin callbacks retain JEI's original order and are never
  dispatched concurrently. Runtime publication happens atomically on the client thread.
- **Immediate startup cancellation on world exit.** Each JEI start now has a generation token and
  a single-flight task. Leaving the world interrupts the active build, cancels derived work, and
  prevents an obsolete runtime from being published.
- **An in-game background loading indicator.** While JEI is starting asynchronously, container
  screens show a short status message in JEI's usual area to the right of the inventory. It
  disappears automatically when startup finishes or is cancelled.

### Fixed

- **Sophisticated Storage shulker box recipes now work with JEI 15.48.** Its special recipe wrapper
  did not have a JEI category extension, causing `minecraft:shulker_box_from_vanilla_shulker_box`
  and related recipes to be rejected as broken. Just Enough Threads now safely reuses the composed
  vanilla recipe's extension. Verified with JEI 15.48.0.179, Sophisticated Storage 1.4.79.2056,
  and Sophisticated Core 1.3.74.2216.
- Removed PR #5's parallel plugin dispatch, main-thread retry, and incompatibility store. Parallel
  callbacks wrote JEI's shared registration containers concurrently, which could corrupt recipe
  maps, duplicate catalyst registrations, drop recipes, or hang startup.
- Removed the misleading parallel phase-barrier timer. JEI's normal serial phase timer and the
  optional per-plugin diagnostics now report the work that actually ran.
- Restored `parallelVanillaRecipes = false` and `stallWatchdog = true` as shipped defaults.

## 0.7.1

A diagnostics release. When JEI startup drags on, the log names JEI and whichever mod registered the
slow content, so the bug report lands on the wrong project. This release points at the code that is
actually spending the time, and gives you a way to keep playing until that code is fixed.

### Added

- **A stall watchdog that names the code responsible for a slow JEI startup.** Once a phase runs
  longer than `stallThresholdSeconds` (10 by default) the stack of the thread running it is sampled,
  and the frames it kept landing on are written to the log. It is purely observational: it never
  changes what runs, in what order, or on which thread. Turn it off with `stallWatchdog = false`.
- **`skipCreativeTabs`, an emergency hatch for a creative tab that will not finish building.** Listed
  tabs are left out of JEI's ingredient scan, by tab id (`examplemod:special_tab`) or by mod id
  (`examplemod`, which skips all of that mod's tabs). Their items no longer appear in JEI, so this is
  a way to keep playing while the real fix is made rather than a setting to leave on. An entry that
  matches nothing logs the list of tab ids that do exist.

### Fixed

- **The anvil recipe controls now work with JEI 15.48.** JEI moved its repair and enchanting recipe
  generators to no-argument instance methods; Just Enough Threads now selects a matching control
  variant instead of disabling both settings when it sees the new signatures.

### Changed

- **Generated anvil repair and enchanting recipes are now hidden by default.** Large packs can spend
  minutes generating every item and enchanted-book combination. Set either `disableAnvil...` option
  to `false` to restore that class of JEI recipes.
- **`parallelVanillaRecipes` now defaults to `false`.** Some modded recipes and lazy ingredient
  caches perform mutable or main-thread-only work when their ingredients are first resolved. The
  option remains available for controlled per-pack benchmarking.

## 0.7.0

A compatibility release. Just Enough Threads now adapts to the JEI build it finds instead of
assuming one particular shape, so a JEI update either keeps working or quietly steps aside.

### Added

- **Support for the reworked ingredient filter in newer JEI builds.** The off-thread search index
  has a second implementation that hooks JEI's own search factory instead of its constructor, and
  the matching one is chosen from what the installed JEI actually declares. Verified in game on JEI
  15.20.0.120 and 15.48.0.179 for 1.20.1, and on 19.27.0.340 for 1.21.1.

### Fixed

- **An unsupported JEI build no longer crashes the game.** Every patch is checked against the JEI
  that is actually installed, and any optimization whose target has changed shape is switched off
  with an explanation in the log instead of failing during startup. Reported for JEI 15.48.0.179 on
  1.20.1 and JEI 19.37.0.363 on 1.21.1.
  ([#1](https://github.com/Tonywww2/JEI-Optimize/issues/1))

### Changed

- The ingredient filter now reports the ingredient count next to the number of distinct ingredient
  uids. JEI keys its search index by uid, so the two differ whenever a pack contains ingredients
  that share one, and the old wording made that look like ingredients had been lost.

## 0.6.0

A correctness release. Two defects could make JEI drop recipes or serve data from a previous
world. Both are fixed, and the optimizations responsible were rebuilt on a design that keeps
their measured benefit without touching JEI from a worker thread.

### Fixed

- **Recipes could silently disappear from JEI.** `parallelVanillaRecipes` called JEI's
  `IRecipeCategory.isHandled` from worker threads. That call mutates a per-recipe extension
  cache inside JEI and invokes third-party category extensions, neither of which is thread
  safe, so concurrent access could corrupt the cache. Affected recipes were then dropped
  during registration, leaving `Failed to create recipe extension` errors in the log. Since
  JEI discards everything it classifies as unhandled, the loss could cover a large part of
  the recipe list. ([#2](https://github.com/Tonywww2/JEI-Optimize/issues/2))
- **A background index could leak into the next world.** The off-thread ingredient search
  build was not tied to a JEI runtime lifecycle. Leaving a world while a build was still
  running could publish that result into the JEI instance of the world you entered next.
  Builds now carry a runtime generation, are cancelled when JEI shuts down, and are
  discarded if they no longer belong to the current runtime.
- Client-tick work queued by the mod is now cleared when JEI shuts down, so nothing left
  over from a previous world runs against a new one.
- The client-tick queue no longer drops queued work when unrelated feature flags are
  disabled.

### Changed

- `parallelVanillaRecipes` now pre-resolves recipe ingredient tags on worker threads instead
  of running JEI's validation there. Workers read recipe data only and their results are
  discarded; JEI's own validation, ordering, and registration all run unchanged on the main
  thread after the workers have finished. Ingredient tag resolution is lazy and JEI triggers
  all of it during startup anyway, so doing it up front in parallel is where the saving comes
  from.
- `delayCompact` now runs JEI's recipe list compaction on the main thread on a later client
  tick instead of on a worker thread. Compaction trims the very lists JEI keeps serving
  queries from, so it was never safe off-thread. It still stays off the blocking startup
  path, which is what the option is for.

### Performance

Verified on Forge 1.20.1 and NeoForge 1.21.1 by entering the same world with the mod disabled
versus enabled. Building the ingredient search index remains the dominant win:

| Metric | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| Building ingredient filter | 413 ms to 143 ms (-65%) | 528 ms to 258 ms (-51%) |
| Registering runtime | 705 ms to 579 ms (-18%) | 1016 ms to 734 ms (-28%) |
| Starting JEI (total) | 1918 ms to 1800 ms (-6%) | 2260 ms to 1913 ms (-15%) |

Measured on a small test pack. Savings scale with pack size, so larger packs see a much
bigger absolute reduction. Ingredient counts were identical with the mod on and off in every
run, and no recipes were lost.

`parallelVanillaRecipes` showed no measurable gain at this pack size: pre-resolving all
ingredients took under 15 ms and recipe registration time was unchanged within noise. Turn it
off if you prefer; the ingredient search index optimization is unaffected.

## 0.5.0

### Added

- JEI runtime state tracking, so background work can be tied to the active JEI runtime.

### Changed

- Vanilla recipe validation falls back to a sequential pass after a JEI runtime unload, to
  avoid a deadlock during in-world rebuilds.

## 0.4.0

### Added

- Compatibility with Advanced Loot Info: while it waits on the main thread for server data,
  the client now keeps processing its task queue instead of blocking outright.

### Changed

- `searchPreheat` now defaults to off. It builds a second, approximate search index that can
  override JEI's own results for prefixed queries, and it is redundant with
  `asyncIngredientFilter`, which rebuilds JEI's real search index off-thread.

## 0.3.0

### Fixed

- The off-thread ingredient filter build now reuses the filter's own `ElementPrefixParser`.
  Building with a fresh parser produced a search index whose prefixed storages (`@` mod,
  `$` tag, and so on) could never be matched by the filter's live queries, silently breaking
  prefixed search while plain text search kept working.

## 0.2.0

### Added

- First release of Just Enough Threads: off-thread JEI ingredient search index, parallel
  vanilla recipe validation, deferred recipe list compaction, and per-feature config toggles.
