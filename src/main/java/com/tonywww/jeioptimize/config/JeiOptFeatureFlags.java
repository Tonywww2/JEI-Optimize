package com.tonywww.jeioptimize.config;

public final class JeiOptFeatureFlags {
    private static final int DEFAULT_WORKER_THREADS = 2;
    private static final int DEFAULT_SNAPSHOT_BUDGET_MS = 2;

    private JeiOptFeatureFlags() {
    }

    private static boolean configReady() {
        return JeiOptConfig.SPEC.isLoaded();
    }

    public static boolean enabled() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return snapshot != null ? snapshot.enabled() : configReady() && JeiOptConfig.GENERAL_ENABLED.get();
    }

    public static boolean pluginTiming() {
        return enabled() && JeiOptConfig.DIAGNOSTICS_PLUGIN_TIMING.get();
    }

    public static boolean registrationCounts() {
        return enabled() && JeiOptConfig.DIAGNOSTICS_REGISTRATION_COUNTS.get();
    }

    public static boolean stallWatchdog() {
        return enabled() && JeiOptConfig.DIAGNOSTICS_STALL_WATCHDOG.get();
    }

    public static int stallThresholdSeconds() {
        return configReady() ? JeiOptConfig.DIAGNOSTICS_STALL_THRESHOLD_SECONDS.get() : 10;
    }

    public static String skipCreativeTabs() {
        return enabled() ? JeiOptConfig.CONTENT_SKIP_CREATIVE_TABS.get() : "";
    }

    public static boolean disableAnvilRepairRecipes() {
        return enabled() && JeiOptConfig.CONTENT_DISABLE_ANVIL_REPAIR.get();
    }

    public static boolean disableAnvilEnchantRecipes() {
        return enabled() && JeiOptConfig.CONTENT_DISABLE_ANVIL_ENCHANT.get();
    }

    public static boolean optimizeAnvilRepresentatives() {
        return enabled() && JeiOptConfig.CONTENT_OPTIMIZE_ANVIL_REPRESENTATIVES.get();
    }

    public static int anvilRepresentativesPerEnchantment() {
        return configReady() ? JeiOptConfig.CONTENT_ANVIL_REPRESENTATIVES_PER_ENCHANTMENT.get() : 3;
    }

    public static int anvilRepairRepresentatives() {
        return configReady() ? JeiOptConfig.CONTENT_ANVIL_REPAIR_REPRESENTATIVES.get() : 16;
    }

    public static boolean optimizeGrindstoneRepresentatives() {
        return enabled() && JeiOptConfig.CONTENT_OPTIMIZE_GRINDSTONE_REPRESENTATIVES.get();
    }

    public static int grindstoneRepresentativesPerEnchantment() {
        return configReady() ? JeiOptConfig.CONTENT_GRINDSTONE_REPRESENTATIVES_PER_ENCHANTMENT.get() : 3;
    }

    public static int grindstoneRepairRepresentatives() {
        return configReady() ? JeiOptConfig.CONTENT_GRINDSTONE_REPAIR_REPRESENTATIVES.get() : 16;
    }

    public static boolean compactIronsSpellsImbuing() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_IRONS_SPELLS_IMBUING.get();
    }

    public static boolean compactFuelRecipes() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_FUEL_RECIPES.get();
    }

    public static boolean compactGeneratorGaloreFuels() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_GENERATOR_GALORE_FUELS.get();
    }

    public static boolean compactMekanismNutritionalLiquifier() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_MEKANISM_NUTRITIONAL_LIQUIFIER.get();
    }

    public static boolean compactThermalStirlingFuels() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_THERMAL_STIRLING_FUELS.get();
    }

    public static boolean cacheCelestialForgeReinforce() {
        return enabled() && JeiOptConfig.CONTENT_CACHE_CELESTIAL_FORGE_REINFORCE.get();
    }

    public static boolean compactUltimateCarWorkshop() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_ULTIMATE_CAR_WORKSHOP.get();
    }

    public static boolean compactIronFurnacesGenerator() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_IRON_FURNACES_GENERATOR.get();
    }

    public static boolean cacheSfmFallingAnvil() {
        return enabled() && JeiOptConfig.CONTENT_CACHE_SFM_FALLING_ANVIL.get();
    }

    public static boolean compactEmbersDawnstoneAnvil() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_EMBERS_DAWNSTONE_ANVIL.get();
    }

    public static boolean cacheProductiveTreesStripperTools() {
        return enabled() && JeiOptConfig.CONTENT_CACHE_PRODUCTIVE_TREES_STRIPPER_TOOLS.get();
    }

    public static boolean compactTinkersCasting() {
        return enabled() && JeiOptConfig.CONTENT_COMPACT_TINKERS_CASTING.get();
    }

    public static boolean indexedBrewingLookup() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.indexedBrewingLookup()
            : JeiOptConfig.CONTENT_INDEXED_BREWING_LOOKUP.get());
    }

    public static boolean skipRedundantMenuUpdates() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.skipRedundantMenuUpdates()
            : JeiOptConfig.CONTENT_SKIP_REDUNDANT_MENU_UPDATES.get());
    }

    public static boolean aggressiveCelestialForgeReinforce() {
        return enabled() && JeiOptConfig.CONTENT_AGGRESSIVE_CELESTIAL_FORGE_REINFORCE.get();
    }

    public static boolean aggressiveEmbersDawnstoneAnvil() {
        return enabled() && JeiOptConfig.CONTENT_AGGRESSIVE_EMBERS_DAWNSTONE_ANVIL.get();
    }

    public static boolean aggressiveSfmFallingAnvil() {
        return enabled() && JeiOptConfig.CONTENT_AGGRESSIVE_SFM_FALLING_ANVIL.get();
    }

    public static int aggressiveRepresentativesPerGroup() {
        return configReady() ? JeiOptConfig.CONTENT_AGGRESSIVE_REPRESENTATIVES_PER_GROUP.get() : 3;
    }

    public static int aggressiveGenericRepairRepresentatives() {
        return configReady() ? JeiOptConfig.CONTENT_AGGRESSIVE_GENERIC_REPAIR_REPRESENTATIVES.get() : 16;
    }

    public static boolean prefilterTinkersIngredients() {
        return enabled() && JeiOptConfig.CONTENT_PREFILTER_TINKERS_INGREDIENTS.get();
    }

    public static String tinkersIngredientFilterAdditionalTags() {
        return enabled() ? JeiOptConfig.CONTENT_TINKERS_INGREDIENT_FILTER_ADDITIONAL_TAGS.get() : "";
    }

    public static boolean cacheScope() {
        return enabled() && JeiOptConfig.SYNC_CACHE_SCOPE.get();
    }

    public static boolean batchIngredientFilterInit() {
        return enabled() && JeiOptConfig.SYNC_BATCH_INGREDIENT_FILTER_INIT.get();
    }

    public static boolean sortKeyCache() {
        return enabled() && JeiOptConfig.SYNC_SORT_KEY_CACHE.get();
    }

    public static boolean delayCompact() {
        return enabled() && JeiOptConfig.SYNC_DELAY_COMPACT.get();
    }

    public static boolean lazyRecipeLayouts() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.lazyRecipeLayouts()
            : JeiOptConfig.SYNC_LAZY_RECIPE_LAYOUTS.get());
    }

    public static int lazyRecipeLayoutThreshold() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return snapshot != null
            ? snapshot.lazyRecipeLayoutThreshold()
            : configReady() ? JeiOptConfig.SYNC_LAZY_RECIPE_LAYOUT_THRESHOLD.get() : 200;
    }

    public static boolean searchPreheat() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null ? snapshot.searchPreheat() : JeiOptConfig.ASYNC_SEARCH_PREHEAT.get());
    }

    public static boolean snapshotChunking() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.snapshotChunking()
            : JeiOptConfig.ASYNC_SNAPSHOT_CHUNKING.get());
    }

    public static boolean sortPreheat() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null ? snapshot.sortPreheat() : JeiOptConfig.ASYNC_SORT_PREHEAT.get());
    }

    public static boolean recipeFocusPreheat() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.recipeFocusPreheat()
            : JeiOptConfig.ASYNC_RECIPE_FOCUS_PREHEAT.get());
    }

    public static boolean catalystPreheat() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.catalystPreheat()
            : JeiOptConfig.ASYNC_CATALYST_PREHEAT.get());
    }

    public static int workerThreads() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        int configured = snapshot != null
            ? snapshot.workerThreads()
            : configReady() ? JeiOptConfig.ASYNC_WORKER_THREADS.get() : DEFAULT_WORKER_THREADS;
        return resolveWorkerThreads(configured, Runtime.getRuntime().availableProcessors());
    }

    public static int parallelThreshold() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return snapshot != null
            ? snapshot.parallelThreshold()
            : configReady() ? JeiOptConfig.ASYNC_PARALLEL_THRESHOLD.get() : 250;
    }

    static int resolveWorkerThreads(int configured, int availableProcessors) {
        if (configured != 0) {
            return Math.max(1, Math.min(8, configured));
        }
        return Math.max(1, Math.min(8, Math.max(1, availableProcessors) - 2));
    }

    public static int snapshotBudgetMs() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return snapshot != null
            ? snapshot.snapshotBudgetMs()
            : configReady() ? JeiOptConfig.ASYNC_SNAPSHOT_BUDGET_MS.get() : DEFAULT_SNAPSHOT_BUDGET_MS;
    }

    public static boolean deferredIngredientFilter() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.deferredIngredientFilter()
            : JeiOptConfig.ASYNC_DEFERRED_INGREDIENT_FILTER.get());
    }

    public static boolean asyncIngredientFilter() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.asyncIngredientFilter()
            : JeiOptConfig.ASYNC_PARALLEL_INGREDIENT_FILTER.get());
    }

    public static boolean parallelVanillaRecipes() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null
            ? snapshot.parallelVanillaRecipes()
            : JeiOptConfig.ASYNC_PARALLEL_VANILLA_RECIPES.get());
    }

    public static boolean asyncStartup() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return enabled() && (snapshot != null ? snapshot.asyncStartup() : JeiOptConfig.ASYNC_STARTUP.get());
    }

    public static int ingredientFilterBudgetMs() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return snapshot != null
            ? snapshot.ingredientFilterBudgetMs()
            : configReady() ? JeiOptConfig.ASYNC_INGREDIENT_FILTER_BUDGET_MS.get() : 10;
    }

    public static int ingredientFilterChunkSize() {
        JeiOptConfigSnapshot.Snapshot snapshot = JeiOptConfigSnapshot.current();
        return snapshot != null
            ? snapshot.ingredientFilterChunkSize()
            : configReady() ? JeiOptConfig.ASYNC_INGREDIENT_FILTER_CHUNK_SIZE.get() : 500;
    }
}