# ATM9 Startup Remediation

## Baseline

2026-09-17, JET 0.13.8, JEI 15.59.0.212, tooltipSearchIndex=false:
JEI startup 305.35 s; recipe registration 104.22 s; ingredient filter 13.21 s;
runtime callbacks 165.00 s; longest recorded frame interval 165.73 s.
TConstruct (7857 removals, 54.73 s) and KubeJS (14283 removals, 101.9 s)
spend 97% of watchdog samples in IngredientFilter.getElements.

## Plan

- [x] Coalesce only JEI grid layout notifications during unpublished startup runtime callbacks. Keep visibility mutations, cache invalidation, third-party listeners and explicit queries synchronous. Flush before publication; discard on failure/cancellation.
- [x] Route Blue Skies and Delightful plugin callbacks to the client thread. Never retry partially executed registrations or suppress thread assertions. ATM9 debug log confirms both recipe callbacks on Render thread; their previous thread assertion failures did not recur.
- [x] Use the existing client-budgeted index and completion gate when tooltip indexing is disabled. Preserve low-memory and feature-off behavior; never wait on the client thread. Forge15.59 native smoke published2670 entries through the budgeted path.
- [x] Correct frame interval attribution across phase boundaries. Reject benchmark runs with plugin/linkage errors; separate JET-off from tooltip-off. Crossing frames appear in every overlapping window, so window sample counts must not be summed.
- [x] Investigate UtiliTiX missing JEI field separately from threading: JEI removed both RECIPE_GUI_VANILLA and its original texture. A field alias is not a valid fix. UtiliTiX compatibility, Apotheosis recipe errors and MineColonies attribute errors remain unresolved; no exception suppression.
- [x] Run focused tests, both loader builds and supported ABI checks; deploy only the project test artifact to ATM9 with approval and capture one post-fix sample. This is not full stability acceptance or five-pair A/B proof.
- [x] Preserve modern JEI bulk-builder semantics while budgeting extraction: retain stock BakedSubstringIndexBuilder during the initial empty construction and seal it after extraction. Real JEI15.59 differential and Forge/NeoForge smoke tests pass. Final large-pack measurement pending; no performance claim for this addition yet.
- [x] Split MineColonies core caching from its optional Tweaks dependency and atomically gate both Iron's Spells compaction hooks. Synthetic and actual mod-JAR ABI checks pass; compatibility-04-off now verifies both gates in ATM9. Core cache hits were zero, so this is not evidence of a cache speedup.
- [x] Fix duplicate MineColonies attribute application within its JEI recipe callback using vanilla equipment remove-then-add semantics. Forge numerical differential, ABI tests and ATM9 runtime verification pass:104 previous attribute warnings/exceptions reduced to0. This is independent of the zero-hit cache.
- [ ] Resolve remaining third-party ABI/recipe errors and investigate changed query result counts before declaring content equivalence or stability.
- [ ] Repeat controlled A/B with unique verified recordings, then cover reload, disconnect/cancellation and a second world.

## Acceptance

No partial publication, unchanged query results/ingredient visibility, no startup-thread
JEI API assertions, bounded client build steps, and no repeated sidebar sorting per
removed ingredient. Compare identical pack/world/Java/JEI/configuration with the
same benchmark probe. One matched run is regression evidence, not five-pair A/B proof.
Existing unrelated worktree and external instance settings must be preserved.

## Status

Both loader builds/check tasks passed;7 tooltip test entrypoints each. Refresh tests
cover22140 mutations, one refresh, nested scopes, failure, stale generation and thread
isolation. JEI15.59.0.212 actual grid listener ABI verified. Small Forge native smoke:
2670 entries,885ms budgeted build; it does not exercise ATM9's large removal batches.

With user approval, the installed ATM9 JET artifact was backed up under
`build/benchmarks/tooltip-pack/before-remediation` and replaced with the repair candidate.
Other mod JARs were not changed. The first post-fix launch request did not create a
Minecraft process (Prism local socket warning). Its aborted/timeout directory is not
a performance sample; no post-fix pack performance improvement is claimed.
Prism/config changes were restored selectively without reverting concurrent user edits.

## Post-Fix Pack Sample

`build/benchmarks/tooltip-pack/remediation-02-off`, manual Prism launch,
Java Temurin21.0.8,8GiB heap,New World,JEI15.59.0.212,tooltipSearchIndex=false.
Mod-manifest comparison changes only the JET JAR. Display settings match; the world
was reused, not restored from a frozen snapshot. Two config keys missing in the early
warmup snapshot were present afterward with their existing default true values.

| Metric | Before | After |
|---|---:|---:|
| JEI startup | 305.35s | 218.05s |
| World-ready to sidebar drawn | 304.16s | 215.85s |
| Sending Runtime | 165.00s | 7.361s |
| TConstruct runtime callback | 54.73s | 80.37ms |
| KubeJS runtime callback | 101.9s | 7.184s |
| Longest measured frame interval | 165.73s | 7.49s |
| Native index build wall time | 13.21s synchronous | 64.84s budgeted |
| Query P95,120 scripted queries | 67.61ms | 96.48ms |
| Startup GC count | 396 | 61 |
| Startup GC pause total | 7.21s | 1.98s |
| Initial ingredient count | 89220 | 90313 |
| Empty item-query count | 64521 | 64514 |

The runtime path flushed exactly one grid. Large batch removals no longer trigger
thousands of complete grid sorts. Blue Skies and Delightful registrations now complete
on the client thread. UtiliTiX still reports its missing field/category and Apotheosis
still reports a broken smithing recipe, so the collector correctly returns runtime-error.
No claim that all plugin content is present or that this is a stability pass.

The longest freeze and runtime delivery improved substantially in this single sample,
but startup/index waiting and query P95 are not all improved. Seven fewer empty-query
items and1093 additional initial ingredients must be reconciled against corrected plugin
execution and world-sensitive sources, not dismissed as harmless. Query counts were
stable within each120-query run; cross-run content equivalence has not been proven.

## Recording Incident

Prism reused its in-memory old JVM arguments despite the collector editing instance.cfg.
The post-fix recording overwrote `warmup-02-off/recording.jfr`. The original baseline
JFR is therefore lost; its previously printed measurements and backed-up text logs are
the only surviving baseline evidence. Do not reanalyze that path as the baseline.
The post-fix recording was copied to `remediation-02-off/recording.jfr`, SHA256
`CBF0CED5D8F845BD0B463378C16F6262A854436C84859474FB669A969620D815`.
`remediation-02-off/metrics.csv` contains its current analyzer output.

The collector now requires Prism to be fully closed before preparation, records and
checks the actual process JFR destination, refuses missing recordings, and will not
archive an old game log when no client was launched. Restart Prism before another
sample. Existing warmup JVM arguments must not be reused for future recordings.

## Compatibility Limits

The grid-layout hook matches Forge15.48/15.59. Legacy15.20/Neo19.27 lack this target,
and Neo19.56's listener differs, so the hook stays disabled there rather than injecting
by name alone. Native-budgeted code compiles on both loaders; large-pack results above
apply only to the recorded Forge15.59 environment. Client callbacks remain serial.

## Native Builder Follow-Up

The first remediation's incremental search populated the baked storage's mutable suffix
tree overflow. That changed the initial storage implementation relative to native bulk
construction. `JeiNativeSearchBuilderMixin` now wraps only the exact constructor call
`ISearchStorageBuilder.build(): ISearchStorage`, within a scoped native build capture.
Only the stock `mezz.jei.common.search.BakedSubstringIndexBuilder` is retained. Other
builders/custom factories keep their original behavior; tooltip indexing and differential
diagnostics do not use this capture path.

`DeferredNativeSearchStorage` forwards initial puts to the retained builder on its owner
client thread. It exposes no partial query results before sealing. After extraction,
each retained prefix is sealed on a separate client tick, and the existing completion
gate waits for all prefixes before publishing. Runtime additions and queries then use
the builder's stock baked/mutable storage. Failure or cancellation discards pending
builders on the client thread, with no exception suppression or worker game objects.
One native build() call remains uninterruptible and may exceed the10ms budget; measure
its longest frame, not just total wall time. The wrapper uses public interface reflection
to span the existing compile/runtime JEI versions, with stock-type and call-site gates.

Verification: both loader build/check tasks pass with8 test entrypoints. Real JEI15.59
bulk-vs-deferred builder tests cover1200 ordinals, repeated strings, Unicode, empty/short
queries and runtime additions. Failure/owner/cancellation behavior has focused tests.
Small Forge15.59 smoke retained3 builders,16ms aggregate seal,575ms total native build.
Neo19.56 smoke retained3 builders,9ms aggregate seal,2813ms total native build. These
are correctness checks, not matched performance comparisons.

`remediation-03-off` uses the follow-up artifact. Prism was closed before preparation,
reopened by the user, and the collector verified the actual process recording path.
Do not conflate this artifact with the first remediation installed for `remediation-02-off`.

## Compatibility Follow-Up

ATM9 logs showed the core MineColonies cache disabled by the absence of optional
`ToolTypeExtension`. Core plugin/equipment contracts now stand alone; Tweaks hooks
still depend on the complete core plus both Tweaks classes. This does not alter the
attribute calculation that logged duplicate modifiers, or guarantee a cache benefit
on a version without the redundant Tweaks checks.

Iron's Spells3.4.0.11 lacks `ArcaneAnvilJeiRecipe`; previously only the recipe Mixin
was rejected while the maker still entered the compactor and raised ClassNotFoundException.
The complete atomic contract now rejects both before application. Real local Forge and
NeoForge3.16.3 JARs remain eligible. The old version retains native recipes, not a new
old-format optimization. The follow-up candidate passed both builds/check tasks before
deployment. On the subsequent user-requested retest, the candidate was installed only
after Minecraft and Prism had exited; see the compatibility-04-off results below.

## Vanilla Timing Evidence

JEI15.59's own plugin timer uses nanoTime around the callback. The saved03 debug log
starts Vanilla recipe registration at17:08:01.776 and ends at17:09:03.278 (61.502s),
consistent with its61.5s watchdog report. This is elapsed wall time, not a summed timer
or CPU time. This callback directly runs on the startup thread with parallelVanillaRecipes
disabled; it does not wait for the subsequent tooltip/native filter completion gate.

The preserved02 JFR window16:23:07.904-16:24:10.065 (UTC+08:00) spans62.161s. It has1822
startup-thread execution/native samples, all reported RUNNABLE, and17 GC events with
573.078ms summed pauses. No startup ThreadPark/JavaMonitorEnter duration was recorded
in that window; event thresholds and sampling do not prove absence of all waits.
Hot chains include Mantle RetexturableRecipeExtension through ingredient validation and
Forge registry HashBiMap lookups, JEI TypedItemStack cache loading/expiration, and
RecipeMap/IngredientUidIndex row construction. Vanilla registration includes modded
crafting/cooking recipes, not only base Minecraft content. No sample had a JET top frame,
but the observer/startup wrappers are naturally present in caller frames.

There is still no matched JET-off comparison. Startup-thread priority, concurrent world
load, cache behavior and patch overhead may affect elapsed time; the evidence establishes
real work and timer boundaries, not zero JET contribution. Do not claim a Vanilla recipe
speedup or move third-party validation to workers based only on these samples.

## Compatibility Retest: 04

2026-09-17, user-requested ATM9 retest, `compatibility-04-off`:
JET enabled, tooltip indexing and differential diagnostics disabled, JEI15.59.0.212,
Temurin21.0.8,8GiB heap,New World. Only the JET JAR hash changed from remediation-02.
Render distance, simulation distance, VSync, max FPS, GUI scale and focus-pause settings
matched the saved02 sample. The world was reused, not reset to a frozen snapshot.

| Metric | Retest result |
|---|---:|
| JEI startup to runtime publication | 204.216s |
| World-ready to sidebar drawn | 202.258s |
| Native budgeted index | 49.708s,90313 ingredients |
| Native builder sealing | 3 builders,313ms aggregate |
| Sending Runtime | 6.055s,1 grid refresh |
| Longest measured frame interval | 7.987s |
| Startup frame P95 | 18.133ms |
| Query P95 / maximum,120 queries | 67.524ms /129.620ms |
| Startup GC count / total pauses | 58 /2.002s |
| Vanilla recipe registration | approximately65.4s |
| MineColonies entire recipe callback | 14.05s |
| MineColonies tool scan | 349ms,0 hits in878346 classifications,0 Tweaks bypasses |
| Iron's Spells entire recipe callback | 6.710s |

MineColonies's base cache now actually activates without Tweaks, but provides no reuse
in this fixture. Its349ms tool scan is only a small part of the14.05s whole callback;
do not describe the gate repair as a whole-plugin performance fix.104 duplicate attribute
modifier exceptions remain. Iron's Spells3.4.0.11 is rejected as a complete feature before
Mixin application; neither ClassNotFound compactor fallback nor partial materialization
was logged. Its original recipe registration still runs. Blue Skies and Delightful recipe
callbacks are confirmed on Render thread, with no previous JEI wrong-thread assertion.

All12 fixed query counts match remediation-02 (each repeated10 times within the run,
empty item query64514). This verifies only these counts, not complete ingredient/recipe
identity equivalence. Query P95 is lower than02's96.48ms in this single sample, while
the longest frame remains around8s and Vanilla registration remains over a minute.
No matched JET-off run or five-pair A/B was performed; do not claim general speedup.

The collector completed all120 queries and the game exited normally, but correctly
classified the run as `runtime-error`: UtiliTiX still lacks RECIPE_GUI_VANILLA and its
advanced_brewery category, and Apotheosis socketing still fails recipe layout generation.
Overall stability acceptance is therefore **failed**, not passed with warnings.

Evidence under `build/benchmarks/tooltip-pack/compatibility-04-off` includes latest/debug
logs, environment/config/options/mod manifests, metrics.csv and recording-hash.json.
JET SHA256: `858DC9D99A8D6E8BEB75B79AFBA8695B1C149E85221E19439B0A017B63ED5E1B`.
Archived JFR SHA256: `01CB54B2D08D2AD3CF29C8590020B21A59EA6A7CA924DF1AE6D0F2C57EADFDE9`.
The process wrote recording-active.jfr; the collector preserved a separate recording.jfr
after process exit and its hash was verified after analysis. This sample was not lost.

Prism normalized the JVM argument quoting, so the collector conservatively retained the
changed JvmArgs line. Once both processes had exited, the authorized test arguments were
cleared explicitly and the JET config flags were verified restored. The latest candidate
remains installed; all other mod JARs are unchanged. The collector now avoids unnecessary
inner quoting for paths without spaces, with a matching restoration regression test.

## MineColonies Attribute Repair: 05

The remaining duplicate-attribute failure is now repaired for verified Forge
`ItemStackUtils.getItemStackAttributeValue(ItemStack, Attribute)` during the exact
MineColonies JEI plugin's Registering recipes callback. The patch changes only the
consumer applied to the original modifier collection, calling the temporary instance's
removeModifier(modifier) before its original addTransientModifier. No source collection,
ItemStack, player or citizen attributes are modified. Other call sites, plugins, phases
and unrelated exception handling stay native. The independent startup-snapshotted
`syncOptimizations.fixMineColoniesAttributeModifiers=true` flag permits opting out;
changing the pre-application gate requires restarting the client.

UUID pre-deduplication was rejected after a numerical test demonstrated differences for
duplicate IDs across operation groups. The final implementation delegates the same
operations as Minecraft AttributeMap instead of reimplementing the attribute formula.
Original duplicate failure reproduction,200 mixed-operation/clamped numerical cases,
scope/off/input-preservation tests and the actual mapped MineColonies JAR gate pass.
Both loader build/check tasks pass; the injection is registered only for Forge.

Production evidence under `minecolonies-attributes-05-runtime` confirms the Mixin applied,
MineColonies's878346 equipment checks and recipe callback completed, JEI startup
completed, and no injection error occurred. Both duplicate modifier exceptions and
attribute-computation warnings are0, compared with104 each in compatibility-04.
The installed artifact SHA256 is
`A3173D586030FF88DD47375B0B9D8AFD7F0F2145939026B227A621A5BCA9400D`.

The automated collector did not attach: Prism reported an INI write error and launched
without benchmark JVM arguments. Its `minecolonies-attributes-05-off` directory is not
a performance sample. Runtime logs were archived separately with verification.json;
no JFR or performance comparison is claimed for05. Temporary JVM parameters were cleared
after Prism exited without stopping the non-benchmark game. Other mod JARs were unchanged.
UtiliTiX/Apotheosis failures, full recipe identity equivalence and remaining startup
performance work are outside this targeted exception acceptance and remain unresolved.