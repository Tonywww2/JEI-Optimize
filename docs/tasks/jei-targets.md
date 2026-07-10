# JEI 15.20.0.133 Mixin Target Verification

Owner: agent1  
Task: PA-1 / T0.1  
Scope: Forge 1.20.1, JEI 15.20.0.133, current `jei_optimize` project.

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

## 2. Lifecycle / Runtime Targets

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Generation begin/end hooks | `mezz.jei.library.startup.JeiStarter` | `public void start()`, `public void stop()` | source lines `93`, `164`; `javap` confirms both public methods | Best target for `JeiOptRuntimeState.beginStart()` and `invalidate()/cancel`. |
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

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Batch filter init and async search access | `mezz.jei.gui.ingredients.IngredientFilter` | fields `clientConfig`, `ingredientManager`, `ingredientComparator`, `modIdHelper`, `ingredientVisibility`, `elementPrefixParser`, mutable `elementSearch`, `ingredientListCached`, `listeners`; ctor; private static `createElementSearch`; public `addIngredient`, `invalidateCache`, `rebuildItemFilter`, `getElements`; private `updateHiddenState`, `getIngredientListUncached`, `getSearchResults`, `notifyListenersOfChange` | source lines `49`-`61`, `63`, `102`, `110`, `119`, `123`, `147`, `172`, `192`, `279`, `329`; `javap` confirms | Requires accessors/invokers for private field/methods. Constructor redirect feasibility is to-verify. |
| Search storage replacement / async facade | `mezz.jei.gui.search.ElementSearch` | fields `prefixedSearchables`, `combinedSearchables`, `allElements`; ctor `(ElementPrefixParser)`; `getSearchResults`, `add`, private static `getUid`, `addAll`, `findElement`, `getAllIngredients`, `logStatistics` | source lines `29`-`33`, `43`, `66`, `82`, `88`, `109`, `123`, `128`; `javap` confirms | External async index can implement `IElementSearch`; replacing `IngredientFilter.elementSearch` is likely lower risk than overwriting `ElementSearch`. |
| Prefix metadata | `mezz.jei.gui.search.ElementPrefixParser` | static `NO_PREFIX`; field `map`; ctor; private `addPrefix`; public `allPrefixInfos`, `parseToken`; record `TokenInfo` | source lines `26`, `35`, `37`, `105`, `109`, `115`, `117`; `javap` confirms | `allPrefixInfos()` is public and can feed snapshot/index builders. |
| Ingredient display/search strings | `mezz.jei.gui.ingredients.ListElementInfo` | static `create`, `createFromElement`; protected ctor; getters `getNames`, `getModNames`, `getModIds`, `getTooltipStrings`, `getTagStrings`, `getTagIds`, `getColors`, `getCreativeTabsStrings`, `getResourceLocation`, `getElement`, `getTypedIngredient`, `getCreatedIndex` | source lines `42`, `49`, `99`, `109`, `114`, `120`, `139`, `148`, `155`, `163`, `184`, `189`, `194`, `199`; `javap` confirms | Snapshot extraction should call these only on client thread unless proven safe. |
| Sort entry point | `mezz.jei.gui.ingredients.IngredientSorter` | static `sortIngredients(IClientConfig, ModNameSortingConfig, IngredientTypeSortingConfig, IIngredientManager, List<IListElementInfo<?>>)` | source line `18`; `javap` confirms | Candidate for sort-key precompute or async sort publish. |
| Sort helper internals | `mezz.jei.gui.ingredients.IngredientSorterComparators` | `getComparator(List<IngredientSortStage>)`, `getComparator(IngredientSortStage)`, private `getTagForSorting`, private static `tagCount`, public static `getItemStack` | source lines `42`, `49`, `155`, `163`, `178`; `javap` confirms | Private `tagCount` is valid injection target for one-start tag-count cache. |

## 5. Recipe / Catalyst Targets

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| Recipe registry write path | `mezz.jei.library.recipes.RecipeManagerInternal` | fields `recipeCategories`, `ingredientManager`, `recipeTypeDataMap`, `recipeMaps`, `pluginManager`, `hiddenRecipeTypes`, `ingredientVisibility`; ctor; `addPlugins`, `addDecorators`, `addRecipes`, private `addRecipe`, `getRecipesStream`, `getRecipeCatalystStream`, `compact`, `isRecipeCatalyst` | source lines `43`-`51`, `57`, `105`, `109`, `113`, `132`, `240`, `245`, `295`, `299`; `javap` confirms | Async recipe index should not call `addRecipe` off-thread. Accessors likely needed for `recipeMaps` / `pluginManager`. |
| Ingredient UID maps | `mezz.jei.library.recipes.collect.RecipeMap` | fields `recipeTable`, `ingredientUidToCategoryMap`, `categoryCatalystUidToRecipeCategoryMap`, `recipeTypeComparator`, `ingredientManager`, `role`; ctor; `getRecipeTypes`, `addCatalystForCategory`, `getRecipes`, `isCatalystForRecipeCategory`, `addRecipe`, `compact`, private `getIngredientUid` | source lines `29`-`34`, `36`, `42`, `50`, `56`, `61`, `67`, `83`, `87`; `javap` confirms | Private `getIngredientUid` must stay on client thread unless helper safety is proven. Worker may build maps only from precomputed UIDs. |
| Query plugin bridge | `mezz.jei.library.recipes.InternalRecipeManagerPlugin` | fields `ingredientManager`, `recipeCategoriesMap`, `recipeMaps`; `getRecipeTypes(IFocus)`, `getRecipes(IRecipeCategory, IFocus)`, `getRecipes(IRecipeCategory)` | source lines `20`-`22`, `35`, `45`, `63`; `javap` confirms | Candidate query interception point for async focus index fallback. |
| Recipe layout ingredient extraction | `mezz.jei.library.util.IngredientSupplierHelper` | static `getIngredientSupplier(T, IRecipeCategory<T>, IIngredientManager)`; source calls `recipeCategory.setRecipe(builder, recipe, FocusGroup.EMPTY)` | source lines `19`, `22`; `javap` confirms | Must remain client thread / JEI thread. Snapshot builder may call it before worker phase. |

## 6. GUI Startup / Reload Targets

| Purpose | Target class | Verified members | Evidence | Notes |
|---|---|---|---|---|
| GUI construction and filter creation timing | `mezz.jei.gui.startup.JeiGuiStarter` | static `start(IRuntimeRegistration)`; source has logged phases `Building ingredient list`, `Building ingredient filter` | source lines `69`, `97`, `101` | Useful for phase timing and scheduling async preheat after filter construction. |
| Resource reload filter rebuild | `mezz.jei.gui.startup.ResourceReloadHandler` | fields `ingredientListOverlay`, `ingredientFilter`; ctor; `onResourceManagerReload(ResourceManager)`; source calls `ingredientFilter.rebuildItemFilter()` | source lines `11`, `12`, `14`, `20`, `22`; source confirms rebuild | Candidate for reload dirty/async rebuild; must be config-gated. |

## 7. Recommended Mixin Targets by Feature

| Feature | Primary target | Support target(s) | Status |
|---|---|---|---|
| Lifecycle generation | `JeiStarter.start`, `JeiStarter.stop` | `StartEventObserver.restart` for optional debounce | verified |
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
| R1-D | Forge 1.20.1 config registration exact imports and generated file name. | PB-4 | Implement `JeiOptConfig`, compile, and confirm `run/config/jei_optimize-client.toml`. |
| R1-E | Whether any target methods are renamed by remapping in runtime mixin environment. | All mixins | Compile + runClient with each mixin enabled one at a time. |

## 9. PA-1 Result

PA-1 acceptance is met for target discovery: planned target classes and members are recorded with source/javap evidence, and unresolved implementation details are explicitly marked to-verify for downstream tasks.
