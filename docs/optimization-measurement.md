# Measuring optimization effect

This guide measures how much Just Enough Threads reduces JEI's startup cost in a real (ideally large) modpack. It relies on the mod's built-in timing logs, so it does not need any profiler.

The helper script `scripts/measure-optimizations.ps1` writes the config profiles and reads the timings for you. It never launches Minecraft; you launch the instance yourself between steps.

## Procedure

Run everything against **one** instance, **one** test world, on the **same** machine. Let `INSTANCE` be your `.minecraft` folder (the one that contains `mods\`, `config\` and `logs\`).

1. Baseline (optimizations off):

        .\scripts\measure-optimizations.ps1 -InstanceDir 'INSTANCE' -Action baseline

2. Launch the instance, enter your test world, wait until the JEI item list is fully populated, then quit to the title screen and close the game.

3. Read the baseline timings and save them:

        .\scripts\measure-optimizations.ps1 -InstanceDir 'INSTANCE' -Action report

4. Optimized (optimizations on):

        .\scripts\measure-optimizations.ps1 -InstanceDir 'INSTANCE' -Action optimized

5. Repeat step 2 (launch, enter the same world, wait, quit).

6. Read the optimized timings:

        .\scripts\measure-optimizations.ps1 -InstanceDir 'INSTANCE' -Action report

7. Compare the two reports.

Run each side two or three times; JEI startup timings vary from run to run, so look at the trend rather than a single number.

## What the numbers mean

- **`Building ingredient filter took ...`** — JEI's own timer for the ingredient search index on the main thread.
  - Baseline: several seconds in a large pack.
  - Optimized: drops to roughly a hundred milliseconds, because the real work now runs off-thread.

- **`async ingredient filter build completed: N indexed (input N) in X ms after world entry`** — the off-thread build (optimized runs only). `indexed` must equal `input`, which confirms every ingredient made it into the index. This time overlaps with normal gameplay, so it does not count against the loading screen.

- **`Registering recipes for jei:minecraft took ...`** — JEI's built-in recipe registration. Lower with `parallelVanillaRecipes` on. Requires `pluginTiming = true` (the script sets it).

- **`parallel crafting recipes: A handled + B special from C total in X ms`** — the parallel recipe validation (optimized runs only).

- **`registration counts for jei:minecraft: RECIPES=N`** — a correctness check. **`N` must be identical** between the baseline and optimized runs. If it differs, the parallel path changed the result and something is wrong; report it.

- **`Starting JEI took ...`** — JEI's overall startup timer, for a top-line before/after figure.

## Correctness checks

- `RECIPES=N` for `jei:minecraft` is identical baseline vs optimized.
- No `async ingredient filter fell back to synchronous build` line (would mean the off-thread ingredient build failed and degraded to JEI's behavior).
- No `falling back to sequential` line for recipes (would mean a recipe threw during parallel validation and degraded to JEI's behavior).
- The JEI item list looks the same and searches (name, `@mod`, `#tooltip`, `$tag`) return the same results in both runs.

If any correctness check fails, set `enabled = false` and open an issue with the log.
