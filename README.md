# Just Enough Threads

A Minecraft Forge mod that speeds up [JEI (Just Enough Items)](https://www.curseforge.com/minecraft/mc-mods/jei) startup by moving its heaviest work off the main thread and across CPU cores.

In large modpacks, JEI spends several seconds building its ingredient search index and processing recipes while the game sits on the loading screen. Just Enough Threads removes the biggest of those costs from the loading screen and parallelizes another, so you get into your world sooner.

## What it does

- **Off-thread ingredient filter build** — `asyncIngredientFilter` (on by default)

  JEI's "Building ingredient filter" step (its search index over every item and fluid) normally runs on the main thread and blocks loading for several seconds in large packs. This mod builds it on worker threads *after* you enter the world, then atomically swaps the finished index into JEI. The result is identical to JEI's own, and if the off-thread build fails for any reason it falls back to JEI's synchronous build.

  Visible effect: the JEI item list is empty for a few seconds after you enter the world, then fills in all at once.

- **Parallel vanilla recipe validation** — `parallelVanillaRecipes` (on by default)

  JEI's built-in plugin validates all vanilla-type recipes (crafting, smelting, stonecutting, and so on) one at a time. This mod runs that validation across CPU cores. The output is identical to JEI's sequential result and falls back to sequential validation on any error.

Every optimization sits behind a config flag and degrades safely to JEI's stock behavior. If anything looks wrong, set `enabled = false` to turn the whole mod off.

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.20.1 |
| Loader | Forge 47.4.4 or newer |
| JEI | 15.20.0.133 or newer |

Client-side only. JEI is a required dependency.

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

Remaining flags in the file are experimental and off by default.

## Measuring the effect

`scripts/measure-optimizations.ps1` helps you run a before/after comparison of JEI startup timings in your own instance. See [docs/optimization-measurement.md](docs/optimization-measurement.md) for the full procedure.

## Building

Gradle must run on JDK 21 (the mod itself targets Java 17):

    ./gradlew build

The built jar lands in `versions/1.20.1-forge/build/libs/`.

## How it works (technical)

The mod is Mixin-based and hooks JEI's internal classes, so it is tied to a specific JEI version.

- The ingredient filter optimization intercepts `IngredientFilter` construction, skips JEI's per-item indexing, and schedules a worker-thread build of a fresh `ElementSearch` using JEI's own `ElementPrefixParser` and `ElementSearch`. A client-tick hook swaps the finished index in.
- The recipe optimization intercepts JEI's `VanillaRecipes` and runs the recipe validator over a parallel stream, preserving recipe order and the handled/special partitioning.

Because it depends on JEI internals, a JEI update may require changes. All mixins are designed to fail safe: if a hook cannot apply or an off-thread task throws, JEI's stock behavior is used instead.

## License

All Rights Reserved.
