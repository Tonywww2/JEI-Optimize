package com.tonywww.jeioptimize.config;

import com.tonywww.jeioptimize.JeiOptimize;
//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
//?} else {
/*import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
*///?}

public final class JeiOptConfig {
    //? if forge {
    public static final ForgeConfigSpec SPEC;
    //?} else {
    /*public static final ModConfigSpec SPEC;
    *///?}

    static final BooleanValue GENERAL_ENABLED;

    static final BooleanValue CONTENT_DISABLE_ANVIL_REPAIR;
    static final BooleanValue CONTENT_DISABLE_ANVIL_ENCHANT;

    static final BooleanValue DIAGNOSTICS_PLUGIN_TIMING;
    static final BooleanValue DIAGNOSTICS_REGISTRATION_COUNTS;

    static final BooleanValue SYNC_CACHE_SCOPE;
    static final BooleanValue SYNC_BATCH_INGREDIENT_FILTER_INIT;
    static final BooleanValue SYNC_SORT_KEY_CACHE;
    static final BooleanValue SYNC_DELAY_COMPACT;

    static final BooleanValue ASYNC_SEARCH_PREHEAT;
    static final BooleanValue ASYNC_SNAPSHOT_CHUNKING;
    static final BooleanValue ASYNC_SORT_PREHEAT;
    static final BooleanValue ASYNC_RECIPE_FOCUS_PREHEAT;
    static final BooleanValue ASYNC_CATALYST_PREHEAT;

    static final IntValue ASYNC_WORKER_THREADS;
    static final IntValue ASYNC_SNAPSHOT_BUDGET_MS;

    static final BooleanValue ASYNC_DEFERRED_INGREDIENT_FILTER;
    static final IntValue ASYNC_INGREDIENT_FILTER_BUDGET_MS;
    static final IntValue ASYNC_INGREDIENT_FILTER_CHUNK_SIZE;
    static final BooleanValue ASYNC_PARALLEL_INGREDIENT_FILTER;
    static final BooleanValue ASYNC_PARALLEL_VANILLA_RECIPES;

    private static boolean registered;

    static {
        Builder builder = new Builder();

        builder.push("general");
        GENERAL_ENABLED = builder
            .comment("Master switch. If false, all JEI Optimize mixin behavior no-ops or falls back to JEI baseline.")
            .define("enabled", true);
        builder.pop();

        builder.push("jeiContent");
        CONTENT_DISABLE_ANVIL_REPAIR = builder
            .comment(
                "Hide JEI's generated anvil repair recipes (repairing an item with its crafting material).",
                "Also skips generating them during startup, which saves time. Default false.")
            .define("disableAnvilRepairRecipes", false);
        CONTENT_DISABLE_ANVIL_ENCHANT = builder
            .comment(
                "Hide JEI's generated anvil enchanting recipes (combining enchanted books on an anvil).",
                "Also skips generating them during startup, which saves time. Default false.")
            .define("disableAnvilEnchantRecipes", false);
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
            .define("cacheScope", true);
        SYNC_BATCH_INGREDIENT_FILTER_INIT = builder
            .comment("Enable IngredientFilter batch initialization optimization.")
            .define("batchIngredientFilterInit", true);
        SYNC_SORT_KEY_CACHE = builder
            .comment("Enable sort key and tag count short cache.")
            .define("sortKeyCache", true);
        SYNC_DELAY_COMPACT = builder
            .comment("Enable delayed JEI compact scheduling.")
            .define("delayCompact", true);
        builder.pop();

        builder.push("async");
        ASYNC_SEARCH_PREHEAT = builder
            .comment("Enable async search prefix index preheat.")
            .define("searchPreheat", true);
        ASYNC_SNAPSHOT_CHUNKING = builder
            .comment("Enable client-tick chunked snapshot extraction.")
            .define("snapshotChunking", true);
        ASYNC_SORT_PREHEAT = builder
            .comment("Enable async sort computation.")
            .define("sortPreheat", true);
        ASYNC_RECIPE_FOCUS_PREHEAT = builder
            .comment("Enable async recipe focus index preheat.")
            .define("recipeFocusPreheat", true);
        ASYNC_CATALYST_PREHEAT = builder
            .comment("Enable async catalyst index preheat.")
            .define("catalystPreheat", true);
        ASYNC_WORKER_THREADS = builder
            .comment("Worker thread count. Ignored when all async features are disabled.")
            .defineInRange("workerThreads", 4, 1, 8);
        ASYNC_SNAPSHOT_BUDGET_MS = builder
            .comment("Per-client-tick snapshot extraction budget in milliseconds. Ignored when snapshotChunking is disabled.")
            .defineInRange("snapshotBudgetMs", 2, 1, 10);
        ASYNC_DEFERRED_INGREDIENT_FILTER = builder
            .comment("Build the JEI ingredient search filter in the background after entering the world instead of blocking startup. The JEI item list fills in progressively.")
            .define("deferredIngredientFilter", true);
        ASYNC_INGREDIENT_FILTER_BUDGET_MS = builder
            .comment("Per-client-tick budget in milliseconds for the deferred ingredient filter build. Higher fills faster but costs more per tick.")
            .defineInRange("ingredientFilterBudgetMs", 10, 1, 40);
        ASYNC_INGREDIENT_FILTER_CHUNK_SIZE = builder
            .comment("Number of ingredients added per work unit during the deferred ingredient filter build.")
            .defineInRange("ingredientFilterChunkSize", 500, 50, 4000);
        ASYNC_PARALLEL_INGREDIENT_FILTER = builder
            .comment(
                "Build the JEI ingredient search filter on worker threads after entering the world, then atomically swap it in.",
                "This removes the multi-second 'Building ingredient filter' cost from the loading screen. The JEI item list",
                "appears a few seconds after you enter the world. Falls back to a synchronous build if the off-thread build fails.",
                "Enabled by default.")
            .define("asyncIngredientFilter", true);
        ASYNC_PARALLEL_VANILLA_RECIPES = builder
            .comment(
                "Validate JEI's built-in (vanilla) recipes in parallel across CPU cores during startup.",
                "The result is identical to JEI's sequential output; it falls back to sequential validation on any error.",
                "Enabled by default.")
            .define("parallelVanillaRecipes", true);
        builder.pop();

        SPEC = builder.build();
    }

    private JeiOptConfig() {
    }

    //? if forge {
    public static void register() {
        if (registered) {
            return;
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, JeiOptimize.MOD_ID + "-client.toml");
        registered = true;
    }
    //?} else {
    /*public static void register(ModContainer container) {
        if (registered) {
            return;
        }
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        registered = true;
    }
    *///?}
}