package com.tonywww.jeioptimize.config;

import com.tonywww.jeioptimize.JeiOptimize;
//? if forge {
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
//?} else {
/*import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
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
    static final ConfigValue<String> CONTENT_SKIP_CREATIVE_TABS;

    static final BooleanValue DIAGNOSTICS_PLUGIN_TIMING;
    static final BooleanValue DIAGNOSTICS_REGISTRATION_COUNTS;
    static final BooleanValue DIAGNOSTICS_STALL_WATCHDOG;
    static final IntValue DIAGNOSTICS_STALL_THRESHOLD_SECONDS;

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
    static final BooleanValue ASYNC_STARTUP;

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
                "Also skips generating them during startup, which saves time. Default true.")
            .define("disableAnvilRepairRecipes", true);
        CONTENT_DISABLE_ANVIL_ENCHANT = builder
            .comment(
                "Hide JEI's generated anvil enchanting recipes (combining enchanted books on an anvil).",
                "Also skips generating them during startup, which saves time. Default true.")
            .define("disableAnvilEnchantRecipes", true);
        CONTENT_SKIP_CREATIVE_TABS = builder
            .comment(
                "Emergency hatch: comma-separated creative tabs to hide from JEI, by tab id or by mod id.",
                "Use this when one mod's creative tab makes JEI's ingredient registration take minutes;",
                "the stall watchdog in the diagnostics section names the mod responsible.",
                "Items from a skipped tab will not appear in JEI at all. Default empty (nothing skipped).",
                "Example: skipCreativeTabs = \"examplemod, othermod:special_tab\"")
            .define("skipCreativeTabs", "");
        builder.pop();

        builder.push("diagnostics");
        DIAGNOSTICS_PLUGIN_TIMING = builder
            .comment("Enable per-plugin and per-stage JEI timing logs.")
            .define("pluginTiming", false);
        DIAGNOSTICS_REGISTRATION_COUNTS = builder
            .comment("Enable JEI registration count diagnostics.")
            .define("registrationCounts", false);
        DIAGNOSTICS_STALL_WATCHDOG = builder
            .comment(
                "Report which code is responsible when a JEI startup phase runs far longer than expected.",
                "Costs nothing until a phase passes stallThresholdSeconds; after that it samples the stack",
                "of the thread running the phase and logs where the time actually went.",
                "Purely observational: it never changes what runs, in what order, or on which thread.")
            .define("stallWatchdog", true);
        DIAGNOSTICS_STALL_THRESHOLD_SECONDS = builder
            .comment(
                "How long one JEI startup phase may run before the stall watchdog starts sampling it.",
                "0 samples every phase, which is noisy but useful when reproducing a report.")
            .defineInRange("stallThresholdSeconds", 10, 0, 300);
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
            .comment(
                "Move JEI's recipe list compaction off the blocking startup path onto a later client tick.",
                "It still runs on the main thread, so JEI never serves queries from a list being trimmed.")
            .define("delayCompact", true);
        builder.pop();

        builder.push("async");
        ASYNC_SEARCH_PREHEAT = builder
            .comment(
                "Async search prefix index preheat. Default OFF (disabled).",
                "Reason: this builds a separate, approximate search index that can override JEI's",
                "own results for prefixed queries (@ mod, # tooltip, $ tag, % tab, ^ color, & id).",
                "It is redundant with asyncIngredientFilter, which already rebuilds JEI's real",
                "ElementSearch off-thread; the shadow index can return incomplete results and may",
                "block the render thread if a prefixed search runs before it finishes building.",
                "Enable only if you specifically need it and accept these trade-offs.")
            .define("searchPreheat", false);
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
                "Resolve recipe ingredient tags on worker threads before JEI validates recipes during startup.",
                "Experimental: modded recipes and lazy ingredient caches are not always thread-safe.",
                "JEI's own validation and registration still run unchanged on the main thread. Default false.")
            .define("parallelVanillaRecipes", false);
        ASYNC_STARTUP = builder
            .comment(
                "Run JEI startup serially on a dedicated background thread so the render thread stays responsive while",
                "JEI builds its item list and recipes after you enter a world. JEI overlays and search simply",
                "appear when startup finishes instead of freezing the loading screen.",
                "Plugin callbacks keep JEI's original order and are never run concurrently. Runtime-available",
                "callbacks and publication run on the client thread. Disable this option if an earlier plugin",
                "registration callback requires the render thread. Leaving a world, timing out, or losing the server",
                "cancels an in-progress build before",
                "its runtime can be published. Enabled by default.")
            .define("asyncStartup", true);
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