# JEI 15.49.0.199+ investigation and repair plan

Status: production repair implemented and final small-fixture matrix passed on 2026-09-05. A later
SDBF production-log audit found and repaired a Forge SRG-name gate mismatch in `0.13.5`. A fresh
SDBF run then verified that `0.13.6` selected `m_7245_()Z` and applied the guard Mixin in production.
The reported large-pack crash is not locally reproducible, and its retained logs do not contain the
original first caller.

## 1. Scope

This plan covers three separate reports that must not be treated as one bug:

1. Missing ingredients or recipes on official JEI `15.49.0.199+`.
2. JEI startup that stalls or takes several minutes in large modpacks.
3. The shared Forge 1.20.1 client crash in `BlockableEventLoop.pollTask`.

It also defines required regression coverage for Minecraft 1.21.1 / NeoForge and Re:Avaritia.

## 2. Established facts

### 2.1 Shared crash

Environment from the report:

- Minecraft `1.20.1`
- Forge `47.4.23`
- Java `25.0.4.1`
- JEI `15.49.0.199`
- Just Enough Threads `0.13.4`
- RSLS `1.2.1`
- 1,772 loaded mods

The first visible failure in the retained tail is an error storm on the JET startup thread:

```text
[17:04:28.555] [justenoughthreads-start/ERROR]
[BlockableEventLoop/FATAL]: Error executing task on Client
net.minecraft.server.RunningOnDifferentThreadException
```

The retained 25,000-line tail contains:

- 11,460 `RunningOnDifferentThreadException` lines.
- 11,459 matching `Error executing task on Client` lines.
- One Render-thread `NoSuchElementException` from `AbstractQueue.remove` at `17:04:30`.

The Render thread failed in `BlockableEventLoop.pollTask` after the JET thread had already begun
executing Client tasks. This is strong evidence that the crash involves an off-main task-pump path
reached during asynchronous JEI startup. It is not evidence that JET directly clears Minecraft's
queue: the project has no accessor, `remove`, `clear`, iterator, or reflection access to
`BlockableEventLoop.pendingRunnables`.

The exact first caller is still unknown. The retained head ends at `17:02:24`, before JEI starts,
and the retained tail begins after the exception storm has started. The missing interval includes
the active JEI phase and plugin identity. This prevents a caller-specific plugin route, but does not
prevent enforcing the narrower invariant that only the Render thread may consume Minecraft Client
tasks.

RSLS is not the direct queue consumer in this crash. Its `IThreadExecutor` accessor is injected
into the common executor base, but RSLS 1.2.1 uses it only from `SoundExecutor.runTask()` to poll
the sound executor's own queue.

### 2.2 The old `.199` and `.200` local smoke fixture was invalid

JEI changed Gradle publication metadata at `.199`:

| Version | Runtime variant | Bundling | File |
|---|---|---|---|
| `.179` | `modShadeRuntimeElements` | `shadowed` | complete jar |
| `.199-.202` | `reobfRuntimeElements` | `external` | `*-unshaded.jar` |
| `.199-.202` | `modShadeRuntimeElements` | `shadowed` | complete jar |

The ordinary Loom runtime attributes selected `reobfRuntimeElements`, so the old `.199/.200`
smokes loaded `*-unshaded.jar`. Those runs failed in `ForgeGuiPlugin` because the remapped fixture
did not contain JEI's shaded Baked Substring implementation. They cannot support any conclusion
about official JEI startup, recipe completeness, or JET compatibility.

Using `15.49.0.199@jar` bypasses that Gradle variant and resolves the official complete artifact.
The runtime log then names `jei-1.20.1-forge-...-15.49.0.199.jar`, without `-unshaded`.

### 2.3 Official full `.199` local A/B results

All three runs entered a real singleplayer world with the complete official artifact:

| JET mode | JEI thread | JEI time | Plugin errors | Thread/queue errors |
|---|---|---:|---:|---:|
| Default enabled | `justenoughthreads-start` | 5.384 s | 0 | 0 |
| `asyncStartup=false` | Render thread | 1.722 s | 0 | 0 |
| `general.enabled=false` | Render thread | 1.476 s | 0 | 0 |

This proves the complete `.199` artifact can work with JET in the local fixture. It does not
disprove a large-pack plugin interaction or the shared crash.

### 2.4 JEI source boundaries

The relevant releases are not one homogeneous `.199+` range:

| Boundary | Relevant source change | Likely visible effect |
|---|---|---|
| `.179 -> .199` | 46 Java files; creative tabs are read from registry access and tabs tagged `hidden_from_recipe_viewers` are skipped; plugins whose `getPluginUid()` throws are removed; brewing extensions and transfer APIs change | Intentional ingredient hiding, complete plugin removal after a UID failure, and third-party ABI changes can look like missing recipes |
| `.199 -> .200` | Only `GrindstoneRecipeMaker` | Synthetic grindstone coverage/performance only; the project already has mutually exclusive instruction-checked variants, but the runtime must be retested with a complete jar |
| `.200 -> .201` | Ingredient visibility becomes `UidContext` aware; `IngredientFilter`, `RecipeManagerInternal`, `PluginLoader`, and `JeiStarter` change | Context-specific hidden ingredients or stale visibility caches can affect sidebar and recipe category visibility |
| `.201 -> .202` | Recipe widget, placement, tooltip, and category rendering APIs change | Third-party category rendering and layout compatibility, not initial plugin dispatch |
| `.202 -> .203` | No Java source changes | Packaging or metadata only |
| `.203 -> .207` | 83 Java files; recipe transfer is redesigned, GUI input/bookmark paths change, starter cleanup changes, and grindstone/item-list code changes | Transfer, GUI lifecycle, guard target, and synthetic recipe compatibility must be re-gated |

`PluginCaller` is byte-identical from `.179` through `.207`. `RecipeRegistration` and the core
`ElementSearch` are also byte-identical in the inspected releases. A regression beginning at
`.199` is therefore not explained by a new JEI plugin dispatch loop.

Boundary quick-play smokes with complete official jars also passed before any repair:

| JEI | Startup thread | JEI time | Plugin/thread/queue/shaded-library/critical-Mixin errors |
|---|---|---:|---:|
| `15.49.0.200` | `justenoughthreads-start` | 3.322 s | 0 |
| `15.55.0.201` | `justenoughthreads-start` | 3.787 s | 0 |
| `15.57.0.207` | `justenoughthreads-start` | 3.136 s | 0 |

The `.201` and `.207` runs only logged expected fail-closed notices for optional Thermal and
Tinkers integrations whose target mods were absent. These small-fixture passes show that `.199+`
is not generically incompatible with JET; they do not cover the large-pack interaction.

## 3. Current root-cause classification

### Confirmed

1. The old local `.199/.200` `BakedSubstringIndex` failure is a test-fixture artifact selection
   bug, not a defect in the official shaded JEI jar.
2. Official `.199` intentionally skips creative tabs tagged `hidden_from_recipe_viewers`.
3. Official `.199` removes an entire plugin if `getPluginUid()` throws. JEI logs this explicitly.
4. The shared crash begins with Client tasks executing on `justenoughthreads-start`; the final
   Render-thread empty-queue removal happens later.
5. JET does not directly access Minecraft's pending task queue, and RSLS only polls its own sound
   executor queue.

### Confirmed mechanism; initiating caller unavailable

One JEI or third-party plugin callback running on `justenoughthreads-start` enters a Minecraft
Client task-pump API such as `managedBlock`, `executeBlocking`, or an equivalent indirect path.
Packet tasks then execute on the wrong thread and reschedule or fail repeatedly. Concurrent Render
and JET task consumption creates the `isEmpty`/`remove` race seen in `pollTask`.

A temporary controlled probe called `Minecraft.managedBlock` from `justenoughthreads-start`. The
production guard intercepted exactly one `Minecraft.pollTask` call, left the task queued for Render
thread, and JEI completed in 3.118 seconds with no plugin, wrong-thread, empty-queue, or critical
Mixin error. The temporary probe was then removed. This confirms the unsafe queue-consumption
mechanism and the guard behavior; the shared log gap still prevents naming its original caller.

### Separate possible causes of missing content

- Upstream `hidden_from_recipe_viewers` tagging: expected missing sidebar ingredients.
- A crashing plugin UID: all categories and recipes from that plugin are removed by JEI.
- A plugin exception during `registerCategories` or `registerRecipes`: partial content only.
- An unlisted main-thread-only plugin running on JET's startup thread: timing-sensitive partial
  registration or a startup stall.
- JET's representative/compaction features: fewer pages are expected, but focus coverage must
  remain equal to the unoptimized baseline.
- `.201+` context-aware visibility: sidebar visibility and recipe visibility must be counted
  separately.

## 4. Repair stages

### Stage A: make the fixture trustworthy

1. Change the compatibility harness to request the official complete runtime artifact explicitly.
   Prefer a Gradle `shadowed` bundling attribute; `version@jar` is an acceptable fallback.
2. Before launch, fail the test if the resolved runtime basename contains `-unshaded`.
3. Verify the complete jar contains the relocated class
   `mezz/jei/modshade/net/mezzdev/bakedsubstring/BakedSubstringIndex.class`.
4. Record runtime path, artifact SHA-256, JEI version, loader, Java version, and JET config with
   every result.
5. Mark all old `.199/.200` unshaded runtime results invalid in validation documentation.

Gate A: no behavior result is interpreted unless artifact identity checks pass.

Status: complete. Plain version overrides now select `version@jar`; the harness rejects
`-unshaded`, verifies Forge's relocated Baked Substring class, and writes a runtime/config hash
manifest. NeoForge uses its loader-specific mod-discovery path marker for the same manifest.

### Stage B: capture the first off-main Client task pump

Use a diagnostic-only build. Do not catch or suppress `NoSuchElementException`.

1. Add a one-shot detector at the narrow Client `BlockableEventLoop` task-pump entry points.
2. When the current thread is `justenoughthreads-start`, log:
   - the complete stack once;
   - JEI phase title;
   - active plugin UID and class;
   - startup generation;
   - whether shutdown/cancellation has begun.
3. Add a rate-limited counter for later off-main entries without printing thousands of stacks.
4. Preserve the original exception and control flow so the diagnostic cannot make a failing run
   appear successful.
5. Capture thread dumps at the first detector hit and at the stall watchdog threshold.

Gate B: the first caller is identified in at least one reproduction, or the current task-pump
hypothesis is falsified and replaced with evidence.

Status: partially complete because the original report omits the first-call interval and the large
pack is unavailable. Always-on phase/plugin context and first-hit stack capture are now present for
future reports. The controlled probe confirms the task-pump path without claiming the unknown
third-party caller.

### Stage C: isolate the controlling path

For each affected official JEI release, run these modes in the same world and mod set:

| Mode | Purpose |
|---|---|
| `general.enabled=false` | Upstream JEI and third-party baseline; excludes every JET Mixin |
| `general.enabled=true`, `asyncStartup=false` | Keeps synchronous content optimizations; excludes the asynchronous startup control path |
| Default async with all optional lossy features off | Tests JET startup, publication, and plugin routing |

Interpretation:

- Baseline fails: do not patch JET; classify upstream JEI or third-party content failure.
- Baseline passes, sync fails: inspect synchronous filtering, representative, compaction, or ABI
  compatibility Mixins.
- Sync passes, async fails: inspect plugin routing, main-thread waits, publication, and lifecycle
  cancellation.
- Only one plugin fixture fails: route or gate that exact plugin after proving its requirement.

Every run must capture category count, recipe count by type/plugin, ingredient count by UID context,
runtime publication count, and the first thrown plugin exception.

Gate C: one switch boundary and one owning code path explain each reproduced symptom.

### Stage D: implement the smallest evidenced repair

Select exactly one branch after Gate C:

1. **Unsafe plugin callback:** add the exact plugin UID or mod namespace to the main-thread policy.
   Do not route every plugin globally.
2. **JET helper pumps Client tasks off-main:** replace that wait protocol with a future/barrier that
   never calls a Client task-pump API from the startup thread.
3. **Unknown JEI call graph:** strengthen the ASM gate and disable the complete feature. Fall back
   to JEI behavior instead of applying a partial optimization.
4. **Visibility/context drift:** preserve JEI's `UidContext` in caches and compare sidebar and recipe
   visibility independently.
5. **Intentional upstream hidden tab or plugin removal:** document the JEI log/config cause. Do not
   re-add hidden content or suppress the upstream safety rule.
6. **Off-main Client task pump with no recoverable initiating caller:** cancel only
   `Minecraft.pollTask()` when it is invoked by the active JET startup thread. Return `false`
   without dequeuing or clearing anything, leaving Render thread as the sole consumer.

For any async safety violation discovered after startup has begun, prefer failing closed for the
current generation. Do not retry a partially executed plugin callback because registration may
already have side effects.

Gate D: the repair is narrow, version/loader gated where necessary, and disabled behavior is the
original JEI path.

Status: branch 6 implemented. The Mixin is gated on the exact `pollTask()Z` member and the global
feature switch. Runtime checks additionally require both the Minecraft singleton receiver and the
JET startup-thread context. It does not catch `NoSuchElementException`, replace the queue, retry a
plugin, or move every plugin to Render thread.

Production follow-up: SDBF loaded Forge's SRG-named `BlockableEventLoop`, where the target method
is `m_7245_()Z`. The `0.13.5` refmap already injected into that name, but the separate ASM gate only
looked for `pollTask()Z` and therefore disabled the Mixin before application. Version `0.13.6`
accepts exactly `pollTask()Z` or `m_7245_()Z`; unknown descriptors and names still fail closed.
In the subsequent production run, the log recorded both the `m_7245_()Z` enable marker and Mixin
application to `BlockableEventLoop`. JEI completed once in 21.59 seconds, the client remained in the
world for about six minutes, and shutdown completed normally. The guard had zero hits and the run
used JEI `15.21.0.148`, so this proves production activation and a clean normal path, not a forced
reproduction of the original JEI `.199` trigger.

### Stage E: strengthen semantic ABI gates

1. `JeiStarter`: verify callback, `Internal.setRuntime`, `running=true`, and stop-cleanup ordering,
   not just field presence.
2. `ItemStackListFactory`: distinguish the `.179` static-tab graph from the `.199+` registry graph;
   disable creative-tab filtering for unknown graphs.
3. `IngredientFilter`: verify the factory invocation count and publication consumer for `.201+`.
4. Grindstone/anvil: retain mutually exclusive complete call-graph gates; add `.201` and `.207`
   shapes only after focus-coverage comparison.
5. GUI/runtime guards: re-verify exact `.207` input, bookmark, transfer, and cleanup targets.

Gate E: an unknown or hybrid graph disables the whole optimization with one clear log line.

## 5. Version and mod matrix

### Forge 1.20.1

Test complete official jars for:

- `15.48.0.179` baseline
- `15.49.0.199`
- `15.49.0.200`
- `15.55.0.201`
- `15.56.0.202`
- `15.56.0.203`
- `15.57.0.207`

Run each required switch mode first with the small fixture, then with isolated interaction sets:

- Re:Avaritia `1.4.1`
- ALI
- AllTheLeaks
- Mixin Helper
- RSLS `1.2.1`
- ALI + AllTheLeaks + Mixin Helper + RSLS
- the reported large pack, when a reproducible instance is available

AllTheLeaks and Mixin Helper must be independent variables because both alter JEI or Mixin behavior
in the shared report. Their presence must never be folded into a generic JEI/JET comparison.

### NeoForge 1.21.1

Keep the existing JEI `19.27.0.340` baseline and run:

- `general.enabled=false`
- `asyncStartup=false`
- default async
- default async + Re:Avaritia `1.4.1`
- connect, disconnect during startup, reconnect, and resource reload

Forge 15.x compatibility patches must not activate on NeoForge unless a separately verified 19.x
ABI requires the same behavior. Shared executor, publication, ingredient filter, and plugin routing
changes require both Java 17 Forge and Java 21 NeoForge validation.

## 6. Re:Avaritia fixture

Pinned official files:

| Loader | File | SHA-512 |
|---|---|---|
| Forge 1.20.1 | `Re-Avaritia-forge-1.20.1-1.4.1-release.jar` | `1E75A8A93B1DE4A5A574EE1D1F652E02060DF25FBE7B0A3E0F9FFCE7D94E9DD0A77265DA450621484FC1888B1C49C02A8C35732FF15A20F3C4FA13A099879DA9` |
| NeoForge 1.21.1 | `Re-Avaritia-neoforge-1.21.1-1.4.1-release.jar` | `34217CB841F149E8AD697FD79BEE3FCBAFAB096731D9498157A887E78FAFEBAA72F52D09E104E7391F5C529B6CA61A6006C98C9FE9204ADF85228AEE519D400F` |

Both jars declare only Minecraft and the corresponding loader as required dependencies. The JEI
plugin UID is `avaritia:jei_plugin`.

Initial evidence, before any repair:

| Runtime | Thread for Re:Avaritia categories/recipes | JEI time | Result |
|---|---|---:|---|
| Forge 1.20.1, JEI full `.199` | `justenoughthreads-start` | 3.235 s | 0 plugin, thread, queue, or shaded-library errors |
| NeoForge 1.21.1, JEI 19.27 | `justenoughthreads-start` | 6.005 s | 0 plugin, Mixin, thread, or queue errors |

Do not add Re:Avaritia to `mainThreadPlugins` without a failing call stack. Its current released
plugin completed on the startup thread on both loaders.

### 6.1 Final production-only matrix

All runs entered their quick-play singleplayer world with the temporary probe removed:

| Runtime | JEI time | Guard hits | Plugin/thread/queue/Baked/critical-Mixin errors |
|---|---:|---:|---:|
| Forge JEI `15.49.0.199` | 3.073 s | 0 | 0 |
| Forge JEI `15.57.0.207` | 3.677 s | 0 | 0 |
| Forge JEI `15.49.0.199` + Re:Avaritia `1.4.1` | 3.353 s | 0 | 0 |
| NeoForge JEI `19.27.0.340` | 5.625 s | 0 | 0 |
| NeoForge JEI `19.27.0.340` + Re:Avaritia `1.4.1` | 5.916 s | 0 | 0 |

Each log contained exactly one JEI completion marker. Loaded remapped JEI SHA-256 values were
`8DC48E23211B42FF47660C72A03B3F13A3DD734D6DD237B7FC1CFDE114BFA596` for `.199`,
`CF252568CC15D10C6DF8A598143C2F85AA2839FAF0768227A97848EF2BFDD237` for `.207`, and
`1343FD994F411CB53C430DA8674EC31064A7EB6724A6B4A7AF76949AA3E0E13E` for NeoForge JEI 19.27.
Both Re:Avaritia runs identified `avaritia:jei_plugin` and completed its callbacks on the JET
startup thread, so it remains absent from `mainThreadPlugins`.

These five runs used Loom's named development runtime. A subsequent production Forge audit is the
reason for the `0.13.6` SRG gate correction above; the matrix remains valid for behavior and
cross-loader compilation but no longer serves as evidence that `0.13.5` applied the guard in a
production Forge instance.

## 7. Acceptance criteria

A repair is ready only when all applicable checks pass:

- No runtime file contains `-unshaded`; artifact hash is recorded.
- No `BakedSubstringIndex` linkage error.
- No `RunningOnDifferentThreadException` from `justenoughthreads-start`.
- No `NoSuchElementException` in `BlockableEventLoop`.
- Zero new `Caught an error from mod plugin` lines.
- JEI start, runtime callbacks, and runtime publication each complete exactly once per generation.
- Disconnect/reload cancels the old generation and no old runtime publishes afterward.
- Ingredient counts match the `general.enabled=false` baseline by `UidContext`, except explicitly
  documented upstream hidden-tab behavior.
- Recipe categories and R/U focus sets match baseline. Representative compaction may reduce pages,
  but not focus coverage.
- Plain, `@`, `#`, `$`, `%`, `&`, and `^` searches return equivalent sets.
- Re:Avaritia compressor, tiered crafting tables, catalysts, transfer handlers, and subtype lookup
  are present on both loaders.
- Three repeated world enter/exit cycles complete without retained startup work.
- Forge 1.20.1 compiles/runs on Java 17 bytecode; NeoForge 1.21.1 compiles/runs on Java 21.

## 8. Explicit non-fixes

- Do not catch `NoSuchElementException` in Minecraft's event loop.
- Do not synchronize or replace Minecraft's task queue from this project.
- Do not suppress JEI plugin exceptions or slow-phase reports.
- Do not globally force all plugins onto the Render thread.
- Do not retry a plugin after partial registration.
- Do not restore content intentionally hidden by JEI's `hidden_from_recipe_viewers` tag.
- Do not claim `.199+` compatibility from compile success or an unshaded development runtime.