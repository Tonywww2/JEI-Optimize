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
        return configReady() && JeiOptConfig.GENERAL_ENABLED.get();
    }

    public static boolean pluginTiming() {
        return enabled() && JeiOptConfig.DIAGNOSTICS_PLUGIN_TIMING.get();
    }

    public static boolean registrationCounts() {
        return enabled() && JeiOptConfig.DIAGNOSTICS_REGISTRATION_COUNTS.get();
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

    public static boolean searchPreheat() {
        return enabled() && JeiOptConfig.ASYNC_SEARCH_PREHEAT.get();
    }

    public static boolean snapshotChunking() {
        return enabled() && JeiOptConfig.ASYNC_SNAPSHOT_CHUNKING.get();
    }

    public static boolean sortPreheat() {
        return enabled() && JeiOptConfig.ASYNC_SORT_PREHEAT.get();
    }

    public static boolean recipeFocusPreheat() {
        return enabled() && JeiOptConfig.ASYNC_RECIPE_FOCUS_PREHEAT.get();
    }

    public static boolean catalystPreheat() {
        return enabled() && JeiOptConfig.ASYNC_CATALYST_PREHEAT.get();
    }

    public static int workerThreads() {
        return configReady() ? JeiOptConfig.ASYNC_WORKER_THREADS.get() : DEFAULT_WORKER_THREADS;
    }

    public static int snapshotBudgetMs() {
        return configReady() ? JeiOptConfig.ASYNC_SNAPSHOT_BUDGET_MS.get() : DEFAULT_SNAPSHOT_BUDGET_MS;
    }

    public static boolean deferredIngredientFilter() {
        return enabled() && JeiOptConfig.ASYNC_DEFERRED_INGREDIENT_FILTER.get();
    }

    public static boolean asyncIngredientFilter() {
        return enabled() && JeiOptConfig.ASYNC_PARALLEL_INGREDIENT_FILTER.get();
    }

    public static boolean parallelVanillaRecipes() {
        return enabled() && JeiOptConfig.ASYNC_PARALLEL_VANILLA_RECIPES.get();
    }

    public static int ingredientFilterBudgetMs() {
        return configReady() ? JeiOptConfig.ASYNC_INGREDIENT_FILTER_BUDGET_MS.get() : 10;
    }

    public static int ingredientFilterChunkSize() {
        return configReady() ? JeiOptConfig.ASYNC_INGREDIENT_FILTER_CHUNK_SIZE.get() : 500;
    }
}