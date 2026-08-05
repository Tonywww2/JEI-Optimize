# Changelog

All notable changes to Just Enough Threads are documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
