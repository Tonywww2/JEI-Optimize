package com.tonywww.jeioptimize.config;

public final class JeiOptFeatureFlags {
    private JeiOptFeatureFlags() {
    }

    public static boolean enabled() {
        return JeiOptConfig.GENERAL_ENABLED.get();
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
        return JeiOptConfig.ASYNC_WORKER_THREADS.get();
    }

    public static int snapshotBudgetMs() {
        return JeiOptConfig.ASYNC_SNAPSHOT_BUDGET_MS.get();
    }
}