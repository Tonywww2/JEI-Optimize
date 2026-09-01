package com.tonywww.jeioptimize.config;

public final class JeiOptConfigSnapshot {
    private static volatile Snapshot current;

    private JeiOptConfigSnapshot() {
    }

    public static void capture() {
        if (!JeiOptConfig.SPEC.isLoaded()) {
            current = null;
            return;
        }
        current = new Snapshot(
            JeiOptConfig.GENERAL_ENABLED.get(),
            JeiOptConfig.CONTENT_INDEXED_BREWING_LOOKUP.get(),
            JeiOptConfig.CONTENT_SKIP_REDUNDANT_MENU_UPDATES.get(),
            JeiOptConfig.SYNC_LAZY_RECIPE_LAYOUTS.get(),
            JeiOptConfig.SYNC_LAZY_RECIPE_LAYOUT_THRESHOLD.get(),
            JeiOptConfig.ASYNC_WORKER_THREADS.get(),
            JeiOptConfig.ASYNC_PARALLEL_THRESHOLD.get(),
            JeiOptConfig.ASYNC_DEFERRED_INGREDIENT_FILTER.get(),
            JeiOptConfig.ASYNC_PARALLEL_INGREDIENT_FILTER.get(),
            JeiOptConfig.ASYNC_PARALLEL_VANILLA_RECIPES.get(),
            JeiOptConfig.ASYNC_STARTUP.get(),
            JeiOptConfig.ASYNC_INGREDIENT_FILTER_BUDGET_MS.get(),
            JeiOptConfig.ASYNC_INGREDIENT_FILTER_CHUNK_SIZE.get()
        );
    }

    public static void clear() {
        current = null;
    }

    static Snapshot current() {
        return current;
    }

    record Snapshot(
        boolean enabled,
        boolean indexedBrewingLookup,
        boolean skipRedundantMenuUpdates,
        boolean lazyRecipeLayouts,
        int lazyRecipeLayoutThreshold,
        int workerThreads,
        int parallelThreshold,
        boolean deferredIngredientFilter,
        boolean asyncIngredientFilter,
        boolean parallelVanillaRecipes,
        boolean asyncStartup,
        int ingredientFilterBudgetMs,
        int ingredientFilterChunkSize
    ) {
    }
}