# Just Enough Threads

**Get into your world faster.** Just Enough Threads moves JEI's heaviest startup work off the main thread and spreads it across your CPU cores, so large modpacks stop freezing on the loading screen while JEI builds its search index.

## What it does

**Off-thread ingredient search index**

JEI normally builds its ingredient filter — the search index over every item and fluid — on the main thread while you wait on the loading screen. In a big pack that can take several seconds. Just Enough Threads builds it on worker threads **after** you enter the world, then swaps the finished index into JEI. The result is identical to JEI's own, and if the off-thread build runs into trouble it falls back to JEI's normal build.

*What you will see:* the JEI item list appears a moment after you spawn, instead of holding up world load.

**Parallel vanilla recipe validation**

JEI validates its built-in recipes (crafting, smelting, stonecutting, and more) one at a time. Just Enough Threads runs that validation across CPU cores and produces an identical result.

## Performance

Measured in a large modpack (roughly 21,000 items and fluids and 34,000 vanilla-type recipes), entering the same world on the same machine with the optimizations off vs on:

| JEI startup | Off | On |
| --- | --- | --- |
| Building runtime | 5.18 s | 0.55 s |
| Starting JEI (total) | 10.7 s | 6.4 s |

The biggest single cost — the ingredient index — no longer blocks loading; it builds in the background once you are already in the world. Numbers vary with hardware and pack size.

## Configuration

Config file: `config/jei_optimize-client.toml`. Every optimization can be toggled independently.

- `enabled` — master switch for the whole mod.
- `asyncIngredientFilter` — build the ingredient search index off-thread after world entry.
- `parallelVanillaRecipes` — validate vanilla recipes across CPU cores.

If you ever run into a problem on world entry, turn off the individual options, or set `enabled = false` to fully restore stock JEI behavior.

## Requirements

- Minecraft 1.20.1
- Forge 47.4.4 or newer
- JEI 15.20.0.133 or newer
- Client-side only. JEI is required.

## Notes

Just Enough Threads hooks JEI's internal classes, so it targets a specific JEI version and may need an update when JEI changes.

Not affiliated with JEI. "Just Enough Items" is a project by mezz.
