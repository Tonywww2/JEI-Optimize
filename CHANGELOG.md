# Changelog

All notable changes to Just Enough Threads are documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 0.13.1

### Changed

- **Ingredient-filter preparation is now client-tick budgeted, followed by JEI's native batch
  index build.** JEI and mod visibility helpers stay on the client thread, while async startup
  performs the indivisible search build on its dedicated thread. This avoids incrementally growing
  every search storage and prevents the index from overlapping the rest of JEI runtime allocation.
- **Celestial Forge reinforce inputs are pooled before the mod expands its wrapper ingredients.**
  Recipes with the same modifier-type set share one candidate scan, and aggressive mode stops as
  soon as both overflow and a complete representative set have been established.

### Fixed

- **Advanced Loot Info recipe registration waits for queued client-tick integrations before it
  consumes shared loot data.** This prevents its background JEI plugin from racing JEI Tetra's
  scheduled loot-data callback during integrated-server startup.
- **Prefix search never waits for an unfinished preheat index on the Render thread.** Queries use
  the ready snapshot index or immediately fall back to JEI's original search.
- **The startup stall watchdog reports live stacks while a plugin is still stuck.** It emits the
  first stack at the configured threshold and refreshes it every ten seconds, so a call that never
  returns still leaves actionable diagnostics.
- **JEI 15.20.0.119 and newer no longer receive one ingredient at a time from the deferred filter.**
  These versions retain a UID map alongside their search storages; extending all of them over many
  client ticks caused severe peak-memory growth in large packs. The deferred path now invokes
  JEI's own `addAll` or full-search factory exactly once.
- **Celestial Forge aggressive previews no longer discard same-family items from small inputs.**
  Representative filtering now begins only after the complete match count is known to exceed the
  configured limit.

## 0.13.0

### Added

- **Supported JEI versions use a generation-scoped brewing recipe index.** Repeated candidate
  lookups use recipe identity hashing and automatically restore JEI's scan if collection identity,
  size, or lifecycle generation diverges.
- **JEI 15.20 can create large recipe categories one visible page at a time.** A bounded layout
  cache avoids eagerly constructing thousands of widgets; affected categories skip
  bookmark/craftable-first sorting and can opt out independently.
- **Runtime diagnostics identify the installed JEI version and internal generation.** Verified
  boundaries distinguish JEI 15.20 legacy, 15.24-15.48.0.178 intermediate, 15.48.0.179 modern, and
  JEI 19+ paths while ASM member contracts remain the authority for applying mixins.

### Changed

- **Search extraction now runs in bounded client-thread chunks.** Workers coordinate publication
  and parallelize only immutable snapshot strings, so third-party tooltip code is not invoked from
  the pure ForkJoin pool. Failed derived prefixes fall back independently to JEI search, and a
  failed ingredient extraction is retried once on the client thread.
- **Worker execution is automatically sized and less intrusive.** `workerThreads=0` reserves two
  processors (clamped to 1-8), workers inherit the mod class loader and run one priority level below
  normal, and small immutable workloads remain sequential.
- **Supported Forge JEI builds batch hidden anvil and grindstone menu updates.** Intermediate slot
  updates are suppressed through a nested thread-local scope and the complete input pair is
  evaluated once.
- **Hot startup configuration is frozen per JEI lifecycle.** Feature-specific mixins with an
  explicit disabled setting are skipped before application, while runtime reloads receive a fresh
  generation snapshot.

## 0.12.0

### Added

- **Coverage-preserving recipe compaction now covers more JEI and optional-mod categories.** Fuel,
  Generator Galore solid fuel, Mekanism Nutritional Liquifier, Thermal Stirling Dynamo, Iron
  Furnaces generators, Ultimate Car workshops, Embers Dawnstone Anvil, and Tinkers casting recipes
  are compacted only when their complete displayed input/output relationships can be preserved.
- **Repeated optional-mod recipe generation is cached where compaction is not appropriate.**
  Celestial Forge reinforce previews, SFM Falling Anvil recipes, and Productive Trees stripper-tool
  ingredients use generation-scoped caches while retaining their original focus behavior.
- **Celestial Forge, Embers, and SFM have independent aggressive representative modes.** These
  opt-in modes default to off because they can remove direct focus hits for non-representative
  items. Their shared defaults retain three item families per group and 16 generic repair examples.
- **Tinkers' Construct can optionally prefilter high-cardinality item variants before JEI indexing.**
  The default-off filter covers `#tconstruct:modifiable` and `#tconstruct:parts` and accepts
  additional comma-separated item tags.
- **Optional integrations now have released-jar ABI contracts.** Missing mods are skipped quietly;
  an installed mod with an incompatible class, field, constructor, or method shape keeps its
  original JEI behavior instead of applying a partial optimization.

### Changed

- **Iron's Spells Arcane Anvil compaction now preserves parallel item, spell-scroll, and output
  relationships.** Representative recipes are selected before expensive output simulation instead
  of expanding each wrapper into the full item-by-spell-level matrix.
- **Lossless optional integrations are enabled by default, while every focus-reducing optimization
  remains opt-in.** Unproven parallel lists, incomplete coverage, or runtime integration failures
  fall back to the original recipes.

### Fixed

- **Inventory slots remain interactive while JEI loads with JEED installed.** The broad container
  mouse-event block has been replaced by an ABI-checked guard on JEED's effect-click callback. Only
  an effect activation that would read an unpublished JEI runtime is ignored; vanilla clicks,
  right-clicks and drags continue through the normal container path.
- **Celestial Forge cached ingredients can no longer be mutated through third-party preview code.**
  Cached inputs and results are deep-copied, and aggressive modes no longer reuse lossless cache
  entries.
- **Celestial Forge Item Reinforce no longer loses its main input and output slots.** Empty dynamic
  ingredients are treated as cache misses instead of being stored permanently, and cached entries
  are isolated by JEI generation so a reload cannot reuse stale slot contents.
- **Embers Dawnstone Anvil aggressive mode now applies to the actual generated display pages.**
  Legitimate recipes with an empty top ingredient no longer abort the whole compaction pass, and
  the 16-example repair limit is shared across singleton pages with the same recipe ID.

## 0.11.0

### Added

- **Generated anvil recipes now use coverage-preserving representative items by default.** Each
  enchantment retains examples for up to three distinct item families, while generic
  material-repair recipes retain up to 16 examples. Candidates are filtered before output-stack
  simulation to avoid generating the full item-by-enchantment matrix.
- **Generated grindstone recipes now use the same representative selection.** Each removable
  enchantment retains examples for up to three distinct item families, and generic self-repair
  recipes retain up to 16 examples on JEI versions that provide synthetic grindstone recipes.
- **Iron's Spells Arcane Anvil imbuing recipes are compacted when the mod is installed.** The
  item-by-spell-level matrix is reduced to a representative set while preserving every eligible
  item and every spell level. The integration has no hard dependency and falls back to unchanged
  recipes if the installed API is incompatible.
- **Representative recipe limits are configurable.** `optimizeAnvilRepresentatives`,
  `anvilRepresentativesPerEnchantment`, `anvilRepairRepresentatives`,
  `optimizeGrindstoneRepresentatives`, `grindstoneRepresentativesPerEnchantment`,
  `grindstoneRepairRepresentatives`, and `compactIronsSpellsImbuing` are available in the
  `jeiContent` section.

### Changed

- **Generated anvil recipes are retained by default instead of being completely hidden.**
  `disableAnvilRepairRecipes` and `disableAnvilEnchantRecipes` now default to `false`. Enabling
  either option still hides that entire recipe class and takes precedence over representative
  selection.
- **Optional recipe optimizations are selected against the installed class and method shapes.** A
  missing optional integration is skipped without a warning, while an installed but incompatible
  integration keeps its original recipe behavior and reports the incompatibility.

## 0.10.4

A startup interaction and display compatibility patch. Loading remains safe around third-party
screen extensions, and its progress panel now adapts to the available screen space.

### Fixed

- **Clicking JEED effect descriptions while JEI is still loading no longer crashes the client.**
  Mouse button events on container screens are held back until JEI publishes its runtime, preventing
  JEED and similar screen extensions from reading partially initialized JEI state. Normal input
  resumes immediately after startup completes. This broad 0.10.4 guard is replaced by the narrow
  JEED callback guard in 0.12.0.
- **The loading progress panel now remains fully visible across GUI scales and resolutions.** It
  evaluates the space to the right, left, below, and above the open container, chooses the widest
  fitting placement, and falls back to a screen-clamped position when no side can contain it. This
  includes 1920x1080 at high GUI scales and unusually wide modded containers.

## 0.10.3

A Just Enough Resources compatibility patch. JER plugin callbacks now preserve their client-thread
assumptions while JEI starts in the background.

### Fixed

- **Just Enough Resources registration no longer runs on the dedicated JEI startup thread.** JER's
  callbacks are executed synchronously on the client thread while preserving JEI's callback order,
  timing diagnostics, plugin context, and exception propagation. This has been verified with JER
  1.4.0.247 on Forge 1.20.1 and JER 1.6.0.17 on NeoForge 1.21.1.

## 0.10.2

A multiplayer startup rendering patch. JEI overlays now remain dormant until their runtime is
fully published when the client enters a server world.

### Fixed

- **Opening or rendering an inventory while joining a multiplayer server no longer lets JEI's
  bookmark overlay read an unpublished runtime.** JEI GUI initialization, layout updates, and
  overlay rendering now remain inactive while background startup is in progress, then resume on
  the first frame after runtime publication. This fixes `Jei Client Configs have not been created
  yet` crashes from `RecipeBookmarkElement` with JEI 19.27 and JEI++.

## 0.10.1

A startup interaction patch. Window events can no longer expose JEI's recipe GUI between runtime
callbacks and global runtime publication.

### Fixed

- **Resizing or maximizing the window while JEI is loading no longer lets a replayed click open a
  half-initialized recipe GUI.** Runtime-available callbacks and `Internal.setRuntime` now execute
  in one uninterrupted client-thread task, preserving JEI's callback-before-publication order
  without leaving an event-queue gap. Third-party `RecipesGui` open requests are also ignored
  while startup publication is incomplete. This fixes the FTB Quests + Ixeris crash reporting
  `Jei Client Configs have not been created yet` from `RecipeLayoutDrawableErrored`.

## 0.10.0

A loader identity and packaging consistency release. The mod id now matches the CurseForge slug,
and distribution filenames use that id directly instead of the Stonecutter loader/project suffix.

### Changed

- **The loader mod id is now `justenoughthreads`.** Metadata, JEI plugin UID, Mixin/refmap names,
  assets, translation keys, logger name, config filename, and release artifacts all use the
  CurseForge-aligned identity. Remove the old jar before installing this release so both loader
  identities are not loaded together.
- **Existing client configuration is preserved.** If `justenoughthreads-client.toml` is absent,
  the mod copies `jei_optimize-client.toml` to the new name before config registration. It never
  overwrites the new file or deletes the legacy file.
- **Release jars are now named from `mod.id`.** Forge and NeoForge artifacts use
  `justenoughthreads-<version>+<minecraft>.jar`. Loader identity remains available from the
  containing Stonecutter build directory and the jar's loader metadata.
- **The JEI loading progress panel is now right-aligned.** Its right edge follows the scaled
  screen's right margin instead of centering inside the empty area beside the inventory.

## 0.9.2

A profiler thread-safety patch. Background JEI startup no longer shares Minecraft's mutable
render-thread profiler state.

### Fixed

- **Background JEI startup is isolated from Minecraft's render-thread profiler.** Minecraft passes
  the mutable `ActiveProfiler.entries` map directly to `FilledProfileResults`, which iterates it
  without synchronization. Any startup callback that asks `Minecraft.getProfiler()` off-thread can
  race that iteration and cause a `ConcurrentModificationException`. The dedicated JEI startup
  thread now receives Minecraft's stateless `InactiveProfiler`; render-thread profiling remains
  unchanged. The reported run also showed a render-thread push/pop mismatch, so the crash report
  does not uniquely identify which mod wrote the shared profiler.

## 0.9.1

A startup-interaction safety patch. Opening an inventory while JEI is still indexing remains safe,
including in packs where another mod queues and replays GLFW input on the render thread.

### Fixed

- **Opening a container and pressing a key while JEI is loading no longer crashes the client.**
  JEI registers its screen input listeners before its runtime is published, so an early key or
  mouse event could call `Internal.getJeiRuntime()` and fail with `Jei Client Configs have not been
  created yet`. JEI-specific keyboard, character, click, release, scroll, and drag handlers now
  remain inactive until startup publication finishes; vanilla inventory input is not consumed.
  Verified against the exact JEI 15.21 input ABI and a real Forge screen-key event during active
  indexing. Ixeris can delay the event into this window, but does not duplicate it.

## 0.9.0

A large-list usability release. JEI now reports real ingredient-index chunk progress and publishes
its sidebar once when the completed index is ready, avoiding repeated partial refreshes in packs
with thousands of JEI pages.

### Added

- **A real chunk progress bar for background JEI loading.** The inventory-side loading panel shows
  an indeterminate preparation phase, followed by completed index chunks such as `12 / 47`, and a
  final publication phase. Progress advances only after a whole chunk has been indexed.

### Changed

- **The JEI sidebar is published once, after indexing reaches 100%.** Both worker-thread and
  client-tick filter builders now populate an isolated search index. Intermediate chunks no longer
  invalidate JEI's live sidebar cache; the client swaps the completed index and invalidates once,
  then runs runtime-available plugin callbacks before exposing the final runtime. This avoids a
  cache-refresh storm in packs with thousands of JEI pages.

## 0.8.1

A lifecycle correctness patch. Disconnecting while JEI is still building now stops its background
startup immediately instead of allowing it to continue after the world has closed.

### Fixed

- **JEI startup is cancelled when the client loses its server connection.** Network timeouts,
  server shutdowns, manual disconnects, and server transfers can bypass or delay JEI's own
  `JeiStarter.stop()` callback. Just Enough Threads now also cancels at Minecraft's client teardown
  boundary, invalidates the active generation, clears derived work, and prevents stale runtime
  publication. Verified through the real Forge 1.20.1 and NeoForge 1.21.1 disconnect paths while
  JEI was actively starting.

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
- **Runtime-available plugin callbacks now run on the client thread.** Moving this final callback
  batch off-thread caused JEI's own thread guard to reject runtime ingredient changes from Botania,
  CC: Tweaked, KubeJS, NuclearCraft, and Applied Energistics 2. JEI construction remains serial on
  the dedicated startup thread; `onRuntimeAvailable` callbacks and publication return to the client
  thread without changing plugin order.
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
