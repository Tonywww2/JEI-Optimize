# `latest_tail.log` JEI error investigation

Source: `C:\Users\12044\Desktop\latest_tail.log`, captured on 2026-09-02 with Forge 1.20.1 and
JEI `15.49.0.199`. JEI startup ran from 14:06:35 to 14:12:26. This build routed every plugin
callback through the Render thread, and the log contains no JEI wrong-thread assertion. Therefore,
the errors below are not fixed by adding more plugin IDs to `mainThreadPlugins`.

## Fixed here

| Error | Count | Finding | Action |
|---|---:|---|---|
| `An interpreter is already registered` | 42 | Sophisticated Storage and Storage in Motion both iterate `SubtypeInterpreters.getSubtypeInterpreters()` and register the same storage items. The same 42 errors reproduced with `asyncStartup=true` and `false`. | An ABI-gated Mixin replaces only Storage in Motion's duplicate shared-map read with an empty map. It does not suppress JEI's global duplicate check. |

Released-jar verification used Storage in Motion `0.10.37.355`, Sophisticated Storage
`1.4.86.2131`, Sophisticated Core `1.3.84.2308`, and JEI `15.20.0.134`. Before the fix both async
and synchronous runs logged 42 duplicates. After the fix both modes logged zero duplicates, zero
plugin failures, and zero Mixin injection failures. Evidence:

- `build/benchmarks/jei-compat/forge-15.20.0.134.storage-in-motion-async.debug.log`
- `build/benchmarks/jei-compat/forge-15.20.0.134.storage-in-motion-sync.debug.log`
- `build/benchmarks/jei-compat/forge-15.20.0.134.storage-in-motion-fixed-final.debug.log`
- `build/benchmarks/jei-compat/forge-15.20.0.134.storage-in-motion-fixed-sync.debug.log`
- `build/benchmarks/jei-compat/forge-15.20.0.134.storage-in-motion-exact-gate.debug.log`

## Real third-party errors retained

| Source | Count | Root cause |
|---|---:|---|
| Avaritia Integration creative tab | 2 | Optional Mekanism Generators and Mystical Agradditions modules construct stacks from absent or unregistered items (`infinity_solar_panel` and a null `ItemLike`). JEI only triggers creative-tab enumeration. |
| JEI empty creative-tab entries | 9 | One creative tab emits `ItemStack.EMPTY`; the log records only the tab object's identity, so its owner cannot be proven from this file. |
| Tinkers Jewelry EX | 2 plugin failures | `AbstractMaterialStatsCategory` is absent. Category loading fails first; recipe registration then fails because `tinkers_jewelry_ex:jewelry_stats` has no category. This is a missing/incompatible Tinkers JEI dependency. |
| Adams Ars Plus | 1 plugin failure | It registers a second extension factory for Ars Nouveau `DyeRecipe`. |
| Supplementaries | 1 plugin failure | `BlocksColorAPI.changeColor(...)` returns null while building sack-coloring display recipes. |
| AlmostFluidified | 1 plugin failure | `AlmostFluidifiedRuntime` is null in `onRuntimeAvailable`; the callback already runs on Render thread. |
| Apotheosis socketing | 1 broken recipe | `SocketingRecipe.assemble` indexes an empty ingredient/result list. JEI catches and excludes that page. |
| Just Enough Resources mobs | 5 broken recipes | Projectile entity types from Illage and Spillage, Cataclysm Spellbooks, and Myths and Legends are treated as `LivingEntity`. Minecraft had already reported missing attributes for several of these types before JEI startup. |
| Brewin' and Chewin' | 2 broken recipes | A fermenting display references missing item `farmersrespite:strong_dandelion_tea`. |
| All The Imbaium tooltips | 40 | `StorageFountainItem`/`FarmItem.appendHoverText` divide by zero while JEI extracts searchable tooltip text. |
| Embers repair data | 14 | Seven items from Queen Bee/Mowzie's Mobs have no repair ingredient; Embers reports them while generating Dawnstone Anvil displays. |
| GregTech CEu | 4 | Generated potion recipes have an empty first input. |
| NuclearCraft | 5 | Recipe generation requests absent fluid tags. |
| Apotheosis loot previews | 6 | Ancient loot rules cannot produce previews for the requested vanilla equipment. |

These errors remain visible. Catching or globally suppressing them in Just Enough Threads would
hide invalid content and make the affected JEI pages silently incomplete.

## High-volume errors outside JEI

- 1,141 `Entity ... has no attributes` messages are emitted by Minecraft before JEI starts. They
	include projectile types later mishandled by JER, but are not created by JEI indexing.
- 292 Forge loot-table parse failures and 32 matching event-bus failures are dominated by
	`BirdsNests.DecayLeafEventHandler` calling `substring(7)` on shorter loot-table paths. Three empty
	JSON files from Upgraded Ender Chests and GTBC's Geomancy Plus also fail independently.
- The final JVM termination at 14:15:38 is explicitly logged as caused by the
	`/crash_assistant crash jvm` command, more than three minutes after JEI finished. It is not a JEI
	startup crash.

## Not exceptions

The 42 `PluginCallerTimerRunnable` lines saying a phase "is running and has taken" are JEI's own
slow-call progress reports. They use ERROR severity but contain no thrown exception. The largest
groups are `jei:minecraft` recipe registration (14 reports), `jei:forge_gui` runtime registration
(13), and Create recipe registration (4).

## Timing-sensitive but not reproduced

PneumaticCraft logged 12 `fuel_quality` lookups with no world available. Its JEI plugin passes
`Minecraft.level` to `PneumaticCraftRecipeType.getRecipes`; that method falls back to the integrated
server and logs an error only when both are null. Isolated released-jar runs with PneumaticCraft
`6.0.23` and JEI `15.20.0.134` produced zero such messages with both `asyncStartup=true` and
`false`. This remains a large-pack timing/interaction issue rather than a justified compatibility
patch. Evidence: `forge-15.20.0.134.pneumaticcraft-{async,sync}.debug.log`.

## Main-thread routing verification

Collector's Reap `1.5.5` directly calls JEI's main-thread-only
`removeIngredientsAtRuntime` twice from `registerRecipes`. With its default plugin ID entry, a real
Forge client ran that callback on Render thread and removed 23 item stacks and 21 fluid stacks with
no wrong-thread or plugin failure. JEI's vanilla ingredient registration and Forge GUI runtime
construction also ran on Render thread, while an unlisted Farmer's Delight recipe callback remained
on `justenoughthreads-start`. Evidence:
`build/benchmarks/jei-compat/forge-15.20.0.120.collectors-reap-main-thread.debug.log`.

JEI's built-in `jei:minecraft` ingredient-registration callback is also pinned to Render thread
because it builds creative tabs and invokes third-party loader callbacks. `jei:forge_gui` and
`jei:neoforge_gui` runtime registration are pinned because they construct the ingredient search and
request third-party tooltips. Other phases, including the expensive `jei:minecraft` recipe
registration, remain on the dedicated startup thread. Forge and NeoForge client smokes confirmed
these boundaries with no wrong-thread, plugin, or Mixin failure.

Repository JUnit tests were removed. Compilation checks only bytecode/build validity; behavior is
validated with isolated released-mod client environments when a concrete compatibility issue needs
reproduction.