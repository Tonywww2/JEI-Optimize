# JEI 15.20.0.133 Mixin Target Verification

Owner: agent1  
Task: PA-1 / T0.1  
Scope: Forge 1.20.1, JEI 15.20.0.133, current `justenoughthreads` loader mod.

## 1. Evidence Sources

All entries below were checked against local Gradle artifacts from `mezz.jei` version `15.20.0.133`.

| Module | Source jar | Class jar | Status |
|---|---|---|---|
| `jei-1.20.1-gui` | `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/mezz.jei/jei-1.20.1-gui/15.20.0.133/.../jei-1.20.1-gui-15.20.0.133-sources.jar` | `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/mezz.jei/jei-1.20.1-gui/15.20.0.133/.../jei-1.20.1-gui-15.20.0.133.jar` | verified |
| `jei-1.20.1-lib` | `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/mezz.jei/jei-1.20.1-lib/15.20.0.133/.../jei-1.20.1-lib-15.20.0.133-sources.jar` | `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/mezz.jei/jei-1.20.1-lib/15.20.0.133/.../jei-1.20.1-lib-15.20.0.133.jar` | verified |
| `jei-1.20.1-forge` | `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/mezz.jei/jei-1.20.1-forge/15.20.0.133/.../jei-1.20.1-forge-15.20.0.133-sources.jar` | `%USERPROFILE%/.gradle/caches/modules-2/files-2.1/mezz.jei/jei-1.20.1-forge/15.20.0.133/.../jei-1.20.1-forge-15.20.0.133.jar` | verified |

Verification methods used:

- `jar tf <sources.jar>` to confirm source file presence.
- `Select-String` over extracted source for line-level source signatures.
- `javap -classpath <jar> -p <class>` to confirm bytecode-visible fields and methods.

Additional lifecycle verification on 2026-08-07 covered Forge JEI 15.20.0.120 and 15.48.0.179,
plus NeoForge JEI 19.27.0.340. The older/NeoForge starters publish through
`Internal.setRuntime(IJeiRuntime)` without a `running` field; JEI 15.48 publishes the runtime and
then writes `JeiStarter.running=true`.

## 2. Lifecycle / Runtime Targets

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Generation begin/end hooks | `mezz.jei.library.startup.JeiStarter` | `public void start()`, `public void stop()` | source lines `93`, `164`; `javap` confirms both public methods | Best target for `JeiOptRuntimeState.beginStart()` and `invalidate()/cancel`. |
| Async runtime publication | `mezz.jei.library.startup.JeiStarter` | `Internal.setRuntime(IJeiRuntime)`; optional field `running:Z` | `javap` on JEI 15.20, 15.48, and 19.27 | `JeiStarterPublishLegacyMixin` publishes `setRuntime` on the client thread. `JeiStarterPublishModernMixin` atomically publishes runtime and `running=true`; the mixin plugin selects by field presence. |
| Runtime-available callbacks | `mezz.jei.library.startup.JeiStarter` | `PluginCaller.callOnPlugins("Sending Runtime", List, Consumer)` | `javap` on JEI 15.20, 15.48, and 19.27; large-pack JEI 15.21 log | `JeiStarterRuntimeCallbacksMixin` runs the final callback batch on the client thread so JEI runtime mutation APIs pass their thread guard. |
| Runtime build sequence observation | `mezz.jei.library.startup.JeiStarter` | calls `PluginLoader.registerSubtypes`, `registerIngredients`, `createRecipeManager`, `createRecipeTransferManager`, `createGuiScreenHelper`, `PluginCaller.callOnPlugins("Registering Runtime", ...)`, `Internal.setRuntime(...)` | source lines `105`, `106`, `115`, `122`, `130`, `140`, `159` | Useful for diagnostics phase boundaries; do not change plugin call ordering. |
| Runtime unavailable hook | `mezz.jei.library.startup.JeiStarter` | `PluginCaller.callOnPlugins("Sending Runtime Unavailable", ...)`, `Internal.setRuntime(null)` | source lines `167`, `168` | Safe place to clear one-start caches and cancel async tasks. |
| Forge start/restart observer | `mezz.jei.forge.startup.StartEventObserver` | `public void register(PermanentEventSubscriptions)`, private `restart()`, private `transitionState(State)` | source lines `45`, `103`, `112`; `javap` confirms fields `observedEvents`, `startRunnable`, `stopRunnable`, `state` | Useful for restart debounce only after explicit config gate. Private methods require `@Inject` by name/descriptor. |
| Forge GUI runtime subscription cleanup | `mezz.jei.forge.plugins.forge.ForgeGuiPlugin` | `registerRuntime(IRuntimeRegistration)`, `onRuntimeUnavailable()`, static `getResourceReloadHandler()` | source lines `33`, `46`, `52`; `javap` confirms `runtimeSubscriptions`, `resourceReloadHandler` | Useful to detect GUI lifecycle and avoid stale handlers. |

## 3. Diagnostics / Plugin Registration Targets

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Per-plugin phase timing | `mezz.jei.library.load.PluginCaller` | `public static void callOnPlugins(String, List<IModPlugin>, Consumer<IModPlugin>)` | source line `16`; `javap` confirms exact signature | Primary target for transparent timing and ThreadLocal plugin context. Must preserve exception behavior. |
| Existing slow-call timer | `mezz.jei.library.load.PluginCallerTimerRunnable` | ctor `(String, ResourceLocation)`, `check()`, `stop()` | source lines `25`, `32`, `41`; `javap` confirms | Can be read as baseline behavior; avoid replacing unless necessary. |
| Ingredient registration count | `mezz.jei.library.load.registration.IngredientManagerBuilder` | `register`, `addExtraIngredients`, `addAlias`, multiple `addAliases`, `build()` | source lines `33`, `54`, `68`, `79`, `89`, `100`, `110`, `123`, `139`, `152`, `177`; `javap` confirms | Many overloads; mixin should cover all alias overloads or count via lower-level `IngredientInfo` if easier. |
| Recipe count | `mezz.jei.library.load.registration.RecipeRegistration` | `<T> void addRecipes(RecipeType<T>, List<T>)` | source line `49`; `javap` confirms | Count `recipes.size()` while preserving call to `RecipeManagerInternal.addRecipes`. |
| Category count | `mezz.jei.library.load.registration.RecipeCategoryRegistration` | `addRecipeCategories(IRecipeCategory<?>...)`, `getRecipeCategories()` | source lines `29`, `56`; `javap` confirms | Count varargs length after null-safe validation. |
| Catalyst count | `mezz.jei.library.load.registration.RecipeCatalystRegistration` | `addRecipeCatalyst`, two `addRecipeCatalysts` overloads, `getRecipeCatalysts()` | source lines `40`, `54`, `67`, `79`; `javap` confirms | Count final typed catalysts, not just input item-like count, if exactness is needed. |

## 4. Ingredient / Search / Sort Targets

Tooltip design Phase 0 adds `ListElementInfoTooltipCaptureMixin` at the RETURN of
`getTooltipStrings(IIngredientFilterConfig, IIngredientManager): Set`. It observes only an active
client-thread capture scope and never changes the return value. The pre-application gate checks
the exact getter, parser/query ABI, detects the tooltip prefix from its mode-getter method handle,
and rejects JEI-Async's deferred tooltip path. Runtime capture and 64 native query comparisons
passed on Forge JEI 15.20.0.120, Forge JEI 15.48.0.179, and NeoForge JEI 19.27.0.340.
JEI-Async coexistence itself has not been exercised in a client.

`TooltipResourceReloadMixin` observes `ResourceReloadHandler.onResourceManagerReload` at HEAD
to invalidate any in-flight tooltip capture context. Published filter rebuilds keep JEI's native
synchronous rebuild behavior. The optimized startup gate requires this reload hook's ABI.
`accessor.ElementSearchTooltipAccessor` exposes only the existing `prefixedSearchables: Map`
for stock-storage inspection on the client thread; detection never calls a storage factory.

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Batch filter init and async search access | `mezz.jei.gui.ingredients.IngredientFilter` | fields `clientConfig`, `ingredientManager`, `ingredientComparator`, `modIdHelper`, `ingredientVisibility`, `elementPrefixParser`, mutable `elementSearch`, `ingredientListCached`, `listeners`; ctor; private static `createElementSearch`; public `addIngredient`, `invalidateCache`, `rebuildItemFilter`, `getElements`; private `updateHiddenState`, `getIngredientListUncached`, `getSearchResults`, `notifyListenersOfChange` | source lines `49`-`61`, `63`, `102`, `110`, `119`, `123`, `147`, `172`, `192`, `279`, `329`; `javap` confirms | Requires accessors/invokers for private field/methods. Constructor redirect feasibility is to-verify. |
| Search storage replacement / async facade | `mezz.jei.gui.search.ElementSearch` | fields `prefixedSearchables`, `combinedSearchables`, `allElements`; ctor `(ElementPrefixParser)`; `getSearchResults`, `add`, private static `getUid`, `addAll`, `findElement`, `getAllIngredients`, `logStatistics` | source lines `29`-`33`, `43`, `66`, `82`, `88`, `109`, `123`, `128`; `javap` confirms | External async index can implement `IElementSearch`; replacing `IngredientFilter.elementSearch` is likely lower risk than overwriting `ElementSearch`. |
| Prefix metadata | `mezz.jei.gui.search.ElementPrefixParser` | static `NO_PREFIX`; field `map`; ctor; private `addPrefix`; public `allPrefixInfos`, `parseToken`; record `TokenInfo` | source lines `26`, `35`, `37`, `105`, `109`, `115`, `117`; `javap` confirms | `allPrefixInfos()` is public and can feed snapshot/index builders. |
| Ingredient display/search strings | `mezz.jei.gui.ingredients.ListElementInfo` | static `create`, `createFromElement`; protected ctor; getters `getNames`, `getModNames`, `getModIds`, `getTooltipStrings`, `getTagStrings`, `getTagIds`, `getColors`, `getCreativeTabsStrings`, `getResourceLocation`, `getElement`, `getTypedIngredient`, `getCreatedIndex` | source lines `42`, `49`, `99`, `109`, `114`, `120`, `139`, `148`, `155`, `163`, `184`, `189`, `194`, `199`; `javap` confirms | Snapshot extraction should call these only on client thread unless proven safe. |
| Sort entry point | `mezz.jei.gui.ingredients.IngredientSorter` | static `sortIngredients(IClientConfig, ModNameSortingConfig, IngredientTypeSortingConfig, IIngredientManager, List<IListElementInfo<?>>)` | source line `18`; `javap` confirms | Candidate for sort-key precompute or async sort publish. |
| Sort helper internals | `mezz.jei.gui.ingredients.IngredientSorterComparators` | `getComparator(List<IngredientSortStage>)`, `getComparator(IngredientSortStage)`, private `getTagForSorting`, private static `tagCount`, public static `getItemStack` | source lines `42`, `49`, `155`, `163`, `178`; `javap` confirms | Private `tagCount` is valid injection target for one-start tag-count cache. |

## 5. Recipe / Catalyst Targets

Compatibility follow-up (2026-09-17): MineColonies plugin scope/equipment cache use a
core-only atomic contract; the optional Tweaks empty-rule hooks require both core and
Tweaks contracts. Iron's Spells maker/recipe hooks instead share one atomic contract
including fields, tuple, spell accessors and scroll APIs. Missing optional Tweaks must not
disable core caching; missing Iron's Spells materialization APIs must disable both hooks.
Exact released-JAR fixtures and rejection cases are recorded in [validation.md](validation.md).

The Forge-only `MineColoniesAttributeModifiersMixin` targets the single
`Collection.forEach(Consumer): void` call inside
`ItemStackUtils.getItemStackAttributeValue(ItemStack, Attribute): double`. Its gate
requires the static exact descriptor, one temporary AttributeInstance constructor,
one Multimap.get, one forEach, and the named or mapped addTransientModifier consumer
and getValue call. The local temporary instance is captured by type. The hook additionally
requires the JEI plugin callback routing contract and runs only under the independent
repair flag within MineColonies recipe registration. Production ATM9 verification
confirmed injection and zero duplicate-attribute failures; normal gameplay is untouched.

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Grindstone representative generation | `mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker` | JEI 15.49.0.199 calls `IPlatformRecipeHelper.isItemEnchantable(ItemStack, Enchantment)` directly from `getDisenchantRecipes`; 15.49.0.200 calls private static `canEnchant(IPlatformRecipeHelper, ItemStack, Enchantment, ResourceLocation)` instead | Byte-for-byte class inspection of released Forge jars and successful quick-play smoke on both versions | `JeiOptMixinPlugin` verifies the actual call graph. Legacy and modern Mixin variants are mutually exclusive; an unknown or hybrid graph disables the complete optimization instead of silently applying a partial limiter. |
| Recipe registry write path | `mezz.jei.library.recipes.RecipeManagerInternal` | fields `recipeCategories`, `ingredientManager`, `recipeTypeDataMap`, `recipeMaps`, `pluginManager`, `hiddenRecipeTypes`, `ingredientVisibility`; ctor; `addPlugins`, `addDecorators`, `addRecipes`, private `addRecipe`, `getRecipesStream`, `getRecipeCatalystStream`, `compact`, `isRecipeCatalyst` | source lines `43`-`51`, `57`, `105`, `109`, `113`, `132`, `240`, `245`, `295`, `299`; `javap` confirms | Async recipe index should not call `addRecipe` off-thread. Accessors likely needed for `recipeMaps` / `pluginManager`. |
| Ingredient UID maps | `mezz.jei.library.recipes.collect.RecipeMap` | fields `recipeTable`, `ingredientUidToCategoryMap`, `categoryCatalystUidToRecipeCategoryMap`, `recipeTypeComparator`, `ingredientManager`, `role`; ctor; `getRecipeTypes`, `addCatalystForCategory`, `getRecipes`, `isCatalystForRecipeCategory`, `addRecipe`, `compact`, private `getIngredientUid` | source lines `29`-`34`, `36`, `42`, `50`, `56`, `61`, `67`, `83`, `87`; `javap` confirms | Private `getIngredientUid` must stay on client thread unless helper safety is proven. Worker may build maps only from precomputed UIDs. |
| Query plugin bridge | `mezz.jei.library.recipes.InternalRecipeManagerPlugin` | fields `ingredientManager`, `recipeCategoriesMap`, `recipeMaps`; `getRecipeTypes(IFocus)`, `getRecipes(IRecipeCategory, IFocus)`, `getRecipes(IRecipeCategory)` | source lines `20`-`22`, `35`, `45`, `63`; `javap` confirms | Candidate query interception point for async focus index fallback. |
| Recipe layout ingredient extraction | `mezz.jei.library.util.IngredientSupplierHelper` | static `getIngredientSupplier(T, IRecipeCategory<T>, IIngredientManager)`; source calls `recipeCategory.setRecipe(builder, recipe, FocusGroup.EMPTY)` | source lines `19`, `22`; `javap` confirms | Must remain client thread / JEI thread. Snapshot builder may call it before worker phase. |

## 6. GUI Startup / Reload Targets

ATM9 remediation (2026-09-17) adds `JeiStartupGridRefreshMixin` against JEI15.59.0.212
`IngredientGridWithNavigation.lambda$new$0()V`. The gate verifies calls to
`getPageAnchorElement(): IElement` and `updateLayoutKeepingPageAnchorVisible(IElement): void`
before enabling the hook. It coalesces only this grid's own layout refresh during
`JeiOptRuntimePublication`'s initial callback scope; live changes and third-party listeners
are untouched. An unknown listener shape disables this optimization. The actual released
JAR contract is covered by `TooltipAbiTest`, scope semantics by `TooltipUiRefreshBatchTest`.

Both legacy and modern filter constructors now use `canDeferFilter` independently of
`tooltipSearchIndex`. The GUI callback submits a complete native budgeted build, then
returns; only the dedicated startup thread waits on the existing filter completion gate.
The low-memory path is unchanged. Large-pack runtime and cancellation validation are
tracked in [atm9-startup-remediation.md](atm9-startup-remediation.md).

`JeiNativeSearchBuilderMixin` wraps the exact `ISearchStorageBuilder.build(): ISearchStorage`
invocation inside modern `ElementSearch` construction. During scoped native budgeted
startup, only stock `BakedSubstringIndexBuilder` is deferred to retain the initial baked
index; runtime puts must not replace the whole initial index with suffix-tree overflow.
All extraction and final builder sealing stay on the client thread. Real JEI15.59 storage
differential tests and Forge15.59/Neo19.56 client smokes verify this path. Unknown builder
types and tooltip/differential modes keep the previous behavior.

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| GUI construction and filter creation timing | `mezz.jei.gui.startup.JeiGuiStarter` | static `start(IRuntimeRegistration)`; source has logged phases `Building ingredient list`, `Building ingredient filter` | source lines `69`, `97`, `101` | Useful for phase timing and scheduling async preheat after filter construction. |
| Resource reload filter rebuild | `mezz.jei.gui.startup.ResourceReloadHandler` | fields `ingredientListOverlay`, `ingredientFilter`; ctor; `onResourceManagerReload(ResourceManager)`; source calls `ingredientFilter.rebuildItemFilter()` | source lines `11`, `12`, `14`, `20`, `22`; source confirms rebuild | Candidate for reload dirty/async rebuild; must be config-gated. |

## 7. Recommended Mixin Targets by Feature

| Feature | Primary target | Support target(s) | Status |
|---|---|---|---|
| Lifecycle generation | `JeiStarter.start`, `JeiStarter.stop` | `StartEventObserver.restart` for optional debounce | verified |
| Serial async startup | `JeiStarter.start`, `JeiStarter.stop`, `Internal.setRuntime` | legacy/modern publication variants selected by `running:Z` | verified on JEI 15.20, 15.48, and 19.27 |
| Plugin timing | `PluginCaller.callOnPlugins` | `PluginCallerTimerRunnable` read-only reference | verified |
| Registration counts | `IngredientManagerBuilder`, `RecipeRegistration`, `RecipeCategoryRegistration`, `RecipeCatalystRegistration` | `JeiPluginCallContext` local helper | verified |
| Config gated mixin wiring | Project-owned `JeiOptConfig`, `JeiOptFeatureFlags`; mixins check these before acting | Forge config API still to-verify by compile probe | partly verified |
| Batch IngredientFilter init | `IngredientFilter` constructor / `addIngredient` loop / `elementSearch` field | `IngredientFilterAccessor` | target verified; injection strategy to-verify |
| Async search index | replace or wrap `IngredientFilter.elementSearch` with `IElementSearch` implementation | `ElementSearch`, `ElementPrefixParser`, `ListElementInfo` | target verified; storage construction to-verify |
| Sort key cache / async sort | `IngredientSorter.sortIngredients`, `IngredientSorterComparators.tagCount` | project `SortKey`, cache scope | verified |
| Delayed compact | `RecipeManagerInternal.compact` | `RecipeMap.compact` if narrower control needed | verified |
| Recipe focus async index | `InternalRecipeManagerPlugin.getRecipeTypes`, `InternalRecipeManagerPlugin.getRecipes` | `RecipeManagerInternal`, `RecipeMap` accessors | target verified; replacement feasibility to-verify |
| Catalyst async index | `RecipeMap.isCatalystForRecipeCategory`, `RecipeMap.addCatalystForCategory` | `InternalRecipeManagerPlugin.getRecipes` catalyst branch | target verified; fallback detail to-verify |
| Reload async rebuild | `ResourceReloadHandler.onResourceManagerReload` | `IngredientFilter.rebuildItemFilter` | verified |

## 8. To-Verify Items for Downstream Tasks

| ID | Item | Blocks | How to resolve |
|---|---|---|---|
| R1-A | Exact injection strategy for `IngredientFilter` constructor batch add. | PD-2 | Prototype redirect/inject in dev runtime; if constructor redirect is brittle, use field replacement after constructor or leave disabled. |
| R1-B | Whether JEI `PrefixInfo` / storage classes can be reused externally without private constructor access. | PF-1 | Inspect `jei-1.20.1-core` source/jar and compile `SearchIndexBuilder` probe. |
| R1-C | Safe replacement path for recipe focus maps without corrupting `RecipeManagerInternal` / `PluginManager`. | PG-1, PG-2 | Build accessor prototype; compare R/U results against baseline. |
| R1-D | Forge 1.20.1 config registration exact imports and generated file name. | PB-4 | Implement `JeiOptConfig`, compile, and confirm `run/config/justenoughthreads-client.toml`. |
| R1-E | Whether any target methods are renamed by remapping in runtime mixin environment. | All mixins | Compile + runClient with each mixin enabled one at a time. |

## 9. PA-1 Result

PA-1 acceptance is met for target discovery: planned target classes and members are recorded with source/javap evidence, and unresolved implementation details are explicitly marked to-verify for downstream tasks.
