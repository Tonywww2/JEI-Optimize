package com.tonywww.jeioptimize.config;

import com.tonywww.jeioptimize.JeiOptimize;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class JeiOptConfig {
    public static final ForgeConfigSpec SPEC;

    static final ForgeConfigSpec.BooleanValue GENERAL_ENABLED;

    static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_PLUGIN_TIMING;
    static final ForgeConfigSpec.BooleanValue DIAGNOSTICS_REGISTRATION_COUNTS;

    static final ForgeConfigSpec.BooleanValue SYNC_CACHE_SCOPE;
    static final ForgeConfigSpec.BooleanValue SYNC_BATCH_INGREDIENT_FILTER_INIT;
    static final ForgeConfigSpec.BooleanValue SYNC_SORT_KEY_CACHE;
    static final ForgeConfigSpec.BooleanValue SYNC_DELAY_COMPACT;

    static final ForgeConfigSpec.BooleanValue ASYNC_SEARCH_PREHEAT;
    static final ForgeConfigSpec.BooleanValue ASYNC_SNAPSHOT_CHUNKING;
    static final ForgeConfigSpec.BooleanValue ASYNC_SORT_PREHEAT;
    static final ForgeConfigSpec.BooleanValue ASYNC_RECIPE_FOCUS_PREHEAT;
    static final ForgeConfigSpec.BooleanValue ASYNC_CATALYST_PREHEAT;

    static final ForgeConfigSpec.IntValue ASYNC_WORKER_THREADS;
    static final ForgeConfigSpec.IntValue ASYNC_SNAPSHOT_BUDGET_MS;

    private static boolean registered;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");
        GENERAL_ENABLED = builder
            .comment("Master switch. If false, all JEI Optimize mixin behavior no-ops or falls back to JEI baseline.")
            .define("enabled", true);
        builder.pop();

        builder.push("diagnostics");
        DIAGNOSTICS_PLUGIN_TIMING = builder
            .comment("Enable per-plugin and per-stage JEI timing logs.")
            .define("pluginTiming", false);
        DIAGNOSTICS_REGISTRATION_COUNTS = builder
            .comment("Enable JEI registration count diagnostics.")
            .define("registrationCounts", false);
        builder.pop();

        builder.push("syncOptimizations");
        SYNC_CACHE_SCOPE = builder
            .comment("Enable one-start UID/string/sort helper caches.")
            .define("cacheScope", false);
        SYNC_BATCH_INGREDIENT_FILTER_INIT = builder
            .comment("Enable IngredientFilter batch initialization optimization.")
            .define("batchIngredientFilterInit", false);
        SYNC_SORT_KEY_CACHE = builder
            .comment("Enable sort key and tag count short cache.")
            .define("sortKeyCache", false);
        SYNC_DELAY_COMPACT = builder
            .comment("Enable delayed JEI compact scheduling.")
            .define("delayCompact", false);
        builder.pop();

        builder.push("async");
        ASYNC_SEARCH_PREHEAT = builder
            .comment("Enable async search prefix index preheat.")
            .define("searchPreheat", false);
        ASYNC_SNAPSHOT_CHUNKING = builder
            .comment("Enable client-tick chunked snapshot extraction.")
            .define("snapshotChunking", false);
        ASYNC_SORT_PREHEAT = builder
            .comment("Enable async sort computation.")
            .define("sortPreheat", false);
        ASYNC_RECIPE_FOCUS_PREHEAT = builder
            .comment("Enable async recipe focus index preheat.")
            .define("recipeFocusPreheat", false);
        ASYNC_CATALYST_PREHEAT = builder
            .comment("Enable async catalyst index preheat.")
            .define("catalystPreheat", false);
        ASYNC_WORKER_THREADS = builder
            .comment("Worker thread count. Ignored when all async features are disabled.")
            .defineInRange("workerThreads", 2, 1, 8);
        ASYNC_SNAPSHOT_BUDGET_MS = builder
            .comment("Per-client-tick snapshot extraction budget in milliseconds. Ignored when snapshotChunking is disabled.")
            .defineInRange("snapshotBudgetMs", 2, 1, 10);
        builder.pop();

        SPEC = builder.build();
    }

    private JeiOptConfig() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, JeiOptimize.MOD_ID + "-client.toml");
        registered = true;
    }
}