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
import net.minecraftforge.fml.loading.FMLPaths;
//?} else {
/*import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
*///?}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JeiOptConfig {
    private static final String CONFIG_NAME = JeiOptimize.MOD_ID + "-client.toml";
    private static final String LEGACY_CONFIG_NAME = "jei_optimize-client.toml";

    //? if forge {
    public static final ForgeConfigSpec SPEC;
    //?} else {
    /*public static final ModConfigSpec SPEC;
    *///?}

    static final BooleanValue GENERAL_ENABLED;

    static final BooleanValue CONTENT_DISABLE_ANVIL_REPAIR;
    static final BooleanValue CONTENT_DISABLE_ANVIL_ENCHANT;
    static final BooleanValue CONTENT_OPTIMIZE_ANVIL_REPRESENTATIVES;
    static final IntValue CONTENT_ANVIL_REPRESENTATIVES_PER_ENCHANTMENT;
    static final IntValue CONTENT_ANVIL_REPAIR_REPRESENTATIVES;
    static final BooleanValue CONTENT_OPTIMIZE_GRINDSTONE_REPRESENTATIVES;
    static final IntValue CONTENT_GRINDSTONE_REPRESENTATIVES_PER_ENCHANTMENT;
    static final IntValue CONTENT_GRINDSTONE_REPAIR_REPRESENTATIVES;
    static final BooleanValue CONTENT_COMPACT_IRONS_SPELLS_IMBUING;
    static final BooleanValue CONTENT_COMPACT_FUEL_RECIPES;
    static final BooleanValue CONTENT_COMPACT_GENERATOR_GALORE_FUELS;
    static final BooleanValue CONTENT_COMPACT_MEKANISM_NUTRITIONAL_LIQUIFIER;
    static final BooleanValue CONTENT_COMPACT_THERMAL_STIRLING_FUELS;
    static final BooleanValue CONTENT_CACHE_CELESTIAL_FORGE_REINFORCE;
    static final BooleanValue CONTENT_COMPACT_ULTIMATE_CAR_WORKSHOP;
    static final BooleanValue CONTENT_COMPACT_IRON_FURNACES_GENERATOR;
    static final BooleanValue CONTENT_CACHE_SFM_FALLING_ANVIL;
    static final BooleanValue CONTENT_COMPACT_EMBERS_DAWNSTONE_ANVIL;
    static final BooleanValue CONTENT_CACHE_PRODUCTIVE_TREES_STRIPPER_TOOLS;
    static final BooleanValue CONTENT_COMPACT_TINKERS_CASTING;
    static final BooleanValue CONTENT_INDEXED_BREWING_LOOKUP;
    static final BooleanValue CONTENT_SKIP_REDUNDANT_MENU_UPDATES;
    static final BooleanValue CONTENT_AGGRESSIVE_CELESTIAL_FORGE_REINFORCE;
    static final BooleanValue CONTENT_AGGRESSIVE_EMBERS_DAWNSTONE_ANVIL;
    static final BooleanValue CONTENT_AGGRESSIVE_SFM_FALLING_ANVIL;
    static final IntValue CONTENT_AGGRESSIVE_REPRESENTATIVES_PER_GROUP;
    static final IntValue CONTENT_AGGRESSIVE_GENERIC_REPAIR_REPRESENTATIVES;
    static final BooleanValue CONTENT_PREFILTER_TINKERS_INGREDIENTS;
    static final ConfigValue<String> CONTENT_TINKERS_INGREDIENT_FILTER_ADDITIONAL_TAGS;
    static final ConfigValue<String> CONTENT_SKIP_CREATIVE_TABS;

    static final BooleanValue DIAGNOSTICS_PLUGIN_TIMING;
    static final BooleanValue DIAGNOSTICS_REGISTRATION_COUNTS;
    static final BooleanValue DIAGNOSTICS_STALL_WATCHDOG;
    static final IntValue DIAGNOSTICS_STALL_THRESHOLD_SECONDS;

    static final BooleanValue SYNC_CACHE_SCOPE;
    static final BooleanValue SYNC_BATCH_INGREDIENT_FILTER_INIT;
    static final BooleanValue SYNC_SORT_KEY_CACHE;
    static final BooleanValue SYNC_DELAY_COMPACT;
    static final BooleanValue SYNC_BATCH_GTCEU_RECIPE_REGISTRATION;
    static final BooleanValue SYNC_LAZY_RECIPE_LAYOUTS;
    static final IntValue SYNC_LAZY_RECIPE_LAYOUT_THRESHOLD;

    static final BooleanValue ASYNC_SEARCH_PREHEAT;
    static final BooleanValue ASYNC_SNAPSHOT_CHUNKING;
    static final BooleanValue ASYNC_SORT_PREHEAT;
    static final BooleanValue ASYNC_RECIPE_FOCUS_PREHEAT;
    static final BooleanValue ASYNC_CATALYST_PREHEAT;

    static final IntValue ASYNC_WORKER_THREADS;
    static final IntValue ASYNC_PARALLEL_THRESHOLD;
    static final IntValue ASYNC_SNAPSHOT_BUDGET_MS;

    static final BooleanValue ASYNC_DEFERRED_INGREDIENT_FILTER;
    static final IntValue ASYNC_INGREDIENT_FILTER_BUDGET_MS;
    static final IntValue ASYNC_INGREDIENT_FILTER_CHUNK_SIZE;
    static final BooleanValue ASYNC_PARALLEL_INGREDIENT_FILTER;
    static final BooleanValue ASYNC_PARALLEL_VANILLA_RECIPES;
    static final BooleanValue ASYNC_STARTUP;
    static final ConfigValue<List<? extends String>> ASYNC_MAIN_THREAD_PLUGINS;

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
                "Also skips generating them during startup. Overrides anvil representative optimization.")
            .define("disableAnvilRepairRecipes", false);
        CONTENT_DISABLE_ANVIL_ENCHANT = builder
            .comment(
                "Hide JEI's generated anvil enchanting recipes (combining enchanted books on an anvil).",
                "Also skips generating them during startup. Overrides anvil representative optimization.")
            .define("disableAnvilEnchantRecipes", false);
        CONTENT_OPTIMIZE_ANVIL_REPRESENTATIVES = builder
            .comment(
                "Keep a small, coverage-preserving set of JEI anvil examples instead of generating every item combination.",
                "Every enchantment with a compatible generated example remains represented.")
            .define("optimizeAnvilRepresentatives", true);
        CONTENT_ANVIL_REPRESENTATIVES_PER_ENCHANTMENT = builder
            .comment("Maximum number of distinct item families shown for each anvil enchantment.")
            .defineInRange("anvilRepresentativesPerEnchantment", 3, 1, 64);
        CONTENT_ANVIL_REPAIR_REPRESENTATIVES = builder
            .comment("Maximum number of generic material-repair examples retained for the anvil.")
            .defineInRange("anvilRepairRepresentatives", 16, 0, 256);
        CONTENT_OPTIMIZE_GRINDSTONE_REPRESENTATIVES = builder
            .comment(
                "Keep a small, coverage-preserving set of JEI grindstone examples.",
                "Only affects JEI versions that generate synthetic grindstone recipes.")
            .define("optimizeGrindstoneRepresentatives", true);
        CONTENT_GRINDSTONE_REPRESENTATIVES_PER_ENCHANTMENT = builder
            .comment("Maximum number of distinct item families shown for each removable enchantment.")
            .defineInRange("grindstoneRepresentativesPerEnchantment", 3, 1, 64);
        CONTENT_GRINDSTONE_REPAIR_REPRESENTATIVES = builder
            .comment("Maximum number of generic self-repair examples retained for the grindstone.")
            .defineInRange("grindstoneRepairRepresentatives", 16, 0, 256);
        CONTENT_COMPACT_IRONS_SPELLS_IMBUING = builder
            .comment(
                "Compact Iron's Spells Arcane Anvil imbuing combinations while preserving every item and spell level.",
                "Has no effect when Iron's Spells is not installed or its integration API is incompatible.")
            .define("compactIronsSpellsImbuing", true);
        CONTENT_COMPACT_FUEL_RECIPES = builder
            .comment(
                "Merge JEI fuel recipes with the same burn time into one page.",
                "All fuel items remain indexed and discoverable through recipe focus searches.")
            .define("compactFuelRecipes", true);
        CONTENT_COMPACT_GENERATOR_GALORE_FUELS = builder
            .comment(
                "Merge equivalent Generator Galore solid-fuel pages while retaining every fuel input.",
                "Has no effect when Generator Galore is not installed or its integration API is incompatible.")
            .define("compactGeneratorGaloreFuels", true);
        CONTENT_COMPACT_MEKANISM_NUTRITIONAL_LIQUIFIER = builder
            .comment(
                "Merge equivalent Mekanism Nutritional Liquifier pages by paste output.",
                "Every food input remains indexed and discoverable through recipe focus searches.")
            .define("compactMekanismNutritionalLiquifier", true);
        CONTENT_COMPACT_THERMAL_STIRLING_FUELS = builder
            .comment(
                "Merge equivalent Thermal Stirling Dynamo fuel pages while retaining every input.",
                "Has no effect when Thermal Expansion is not installed or its integration API is incompatible.")
            .define("compactThermalStirlingFuels", true);
        CONTENT_CACHE_CELESTIAL_FORGE_REINFORCE = builder
            .comment(
                "Cache Celestial Forge Item Reinforce input and preview scans for each JEI recipe.",
                "Does not remove any matching equipment or change focus results.")
            .define("cacheCelestialForgeReinforce", true);
        CONTENT_COMPACT_ULTIMATE_CAR_WORKSHOP = builder
            .comment(
                "Collapse Ultimate Car Mod workshop combinations into one page per vehicle layout.",
                "All compatible parts remain in JEI input slots; the 3D preview uses one representative vehicle.")
            .define("compactUltimateCarWorkshop", true);
        CONTENT_COMPACT_IRON_FURNACES_GENERATOR = builder
            .comment(
                "Merge equivalent Iron Furnaces Augment: Generator pages by energy value.",
                "All fuel and food inputs remain indexed and discoverable through recipe focus searches.")
            .define("compactIronFurnacesGenerator", true);
        CONTENT_CACHE_SFM_FALLING_ANVIL = builder
            .comment(
                "Cache Super Factory Manager's full Falling Anvil disenchantment display lists.",
                "Focused item and enchantment queries still use SFM's original specialized path.")
            .define("cacheSfmFallingAnvil", true);
        CONTENT_COMPACT_EMBERS_DAWNSTONE_ANVIL = builder
            .comment(
                "Merge equivalent Embers Dawnstone Anvil visual pages while retaining every input/output pair.",
                "Bottom inputs and outputs remain position-linked for JEI focus searches.")
            .define("compactEmbersDawnstoneAnvil", true);
        CONTENT_CACHE_PRODUCTIVE_TREES_STRIPPER_TOOLS = builder
            .comment(
                "Cache Productive Trees' log-stripping tool tag expansion for the current JEI lifecycle.",
                "Does not remove tools, recipes, catalysts, or focus results.")
            .define("cacheProductiveTreesStripperTools", true);
        CONTENT_COMPACT_TINKERS_CASTING = builder
            .comment(
                "Merge Tinkers' Construct casting display pages only when they share one parent recipe and display parameters.",
                "Every cast/output pair remains position-linked; ambiguous recipes keep their original pages.")
            .define("compactTinkersCasting", true);
        CONTENT_INDEXED_BREWING_LOOKUP = builder
            .comment(
                "Use a generation-scoped hash index for JEI's repeated brewing recipe lookup.",
                "If the index ever differs from JEI's recipe collection, this optimization disables itself for that lifecycle.")
            .define("indexedBrewingLookup", true);
        CONTENT_SKIP_REDUNDANT_MENU_UPDATES = builder
            .comment(
                "Suppress redundant hidden anvil and grindstone menu updates while JEI fills both inputs.",
                "The result is computed once after the complete input pair is installed.")
            .define("skipRedundantMenuUpdates", true);
        CONTENT_AGGRESSIVE_CELESTIAL_FORGE_REINFORCE = builder
            .comment(
                "Lossy: limit Celestial Forge Item Reinforce input previews to representative item families.",
                "Disabled by default; omitted items no longer provide direct JEI focus hits for this category.")
            .define("aggressiveCelestialForgeReinforce", false);
        CONTENT_AGGRESSIVE_EMBERS_DAWNSTONE_ANVIL = builder
            .comment(
                "Lossy: limit each compacted Embers Dawnstone Anvil group to representative tools.",
                "Disabled by default; omitted tools no longer provide direct JEI focus hits for this category.")
            .define("aggressiveEmbersDawnstoneAnvil", false);
        CONTENT_AGGRESSIVE_SFM_FALLING_ANVIL = builder
            .comment(
                "Lossy: limit SFM Falling Anvil's unfiltered overview to representative tool families per enchantment.",
                "Disabled by default; focused queries still use SFM's original specialized path.")
            .define("aggressiveSfmFallingAnvil", false);
        CONTENT_AGGRESSIVE_REPRESENTATIVES_PER_GROUP = builder
            .comment("Representative item-family limit used by explicitly enabled aggressive modes.")
            .defineInRange("aggressiveRepresentativesPerGroup", 3, 1, 64);
        CONTENT_AGGRESSIVE_GENERIC_REPAIR_REPRESENTATIVES = builder
            .comment("Example limit for generic repair recipes in explicitly enabled aggressive modes.")
            .defineInRange("aggressiveGenericRepairRepresentatives", 16, 1, 256);
        CONTENT_PREFILTER_TINKERS_INGREDIENTS = builder
            .comment(
                "Lossy: remove item stacks in #tconstruct:modifiable and #tconstruct:parts before JEI builds its global index.",
                "Disabled by default. Filtered variants remain in the game and creative tabs but not JEI's right-side list.")
            .define("prefilterTinkersIngredients", false);
        CONTENT_TINKERS_INGREDIENT_FILTER_ADDITIONAL_TAGS = builder
            .comment(
                "Comma-separated item tags to filter in addition to Tinkers' default modifiable and parts tags.",
                "A leading # is optional. Only used when prefilterTinkersIngredients is enabled.")
            .define("tinkersIngredientFilterAdditionalTags", "");
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
        SYNC_BATCH_GTCEU_RECIPE_REGISTRATION = builder
            .comment(
                "Register large GTCEu recipe categories with JEI in bounded, ordered batches.",
                "Preserves every recipe and focus index while reducing temporary startup allocations.")
            .define("batchGtceuRecipeRegistration", true);
        SYNC_LAZY_RECIPE_LAYOUTS = builder
            .comment(
                "Build large recipe category layouts one visible page at a time on older JEI versions.",
                "Only applies when the installed JEI still eagerly creates every layout.")
            .define("lazyRecipeLayouts", true);
        SYNC_LAZY_RECIPE_LAYOUT_THRESHOLD = builder
            .comment("Recipe count above which legacy JEI uses per-page lazy layouts.")
            .defineInRange("lazyRecipeLayoutThreshold", 200, 0, 1000000);
        builder.pop();

        builder.push("async");
        ASYNC_SEARCH_PREHEAT = builder
            .comment(
                "Retired compatibility key. This option is always disabled.",
                "The old implementation duplicated JEI's search strings and prefix maps, could use",
                "gigabytes in large packs, and was not authoritative for empty search results.")
            .define("searchPreheat", false);
        ASYNC_SNAPSHOT_CHUNKING = builder
            .comment("Retired compatibility key. This option is always disabled.")
            .define("snapshotChunking", false);
        ASYNC_SORT_PREHEAT = builder
            .comment("Retired compatibility key. This option is always disabled.")
            .define("sortPreheat", false);
        ASYNC_RECIPE_FOCUS_PREHEAT = builder
            .comment("Retired compatibility key. This option is always disabled.")
            .define("recipeFocusPreheat", false);
        ASYNC_CATALYST_PREHEAT = builder
            .comment("Retired compatibility key. This option is always disabled.")
            .define("catalystPreheat", false);
        ASYNC_WORKER_THREADS = builder
            .comment(
                "Worker thread count. 0 selects available processors minus two, clamped to 1-8.",
                "Ignored when all async features are disabled.")
            .defineInRange("workerThreads", 0, 0, 8);
        ASYNC_PARALLEL_THRESHOLD = builder
            .comment("Minimum input size before pure indexing and warmup work uses multiple worker threads.")
            .defineInRange("parallelThreshold", 250, 1, 1000000);
        ASYNC_SNAPSHOT_BUDGET_MS = builder
            .comment("Retired compatibility key. This value is ignored.")
            .defineInRange("snapshotBudgetMs", 2, 1, 10);
        ASYNC_DEFERRED_INGREDIENT_FILTER = builder
            .comment("Populate one isolated JEI ingredient search in client-tick-budgeted steps.")
            .define("deferredIngredientFilter", true);
        ASYNC_INGREDIENT_FILTER_BUDGET_MS = builder
            .comment("Per-client-tick budget in milliseconds for isolated ingredient search construction.")
            .defineInRange("ingredientFilterBudgetMs", 10, 1, 40);
        ASYNC_INGREDIENT_FILTER_CHUNK_SIZE = builder
            .comment("Number of ingredients represented by one progress step. The frame deadline is checked after every ingredient.")
            .defineInRange("ingredientFilterChunkSize", 500, 50, 4000);
        ASYNC_PARALLEL_INGREDIENT_FILTER = builder
            .comment(
                "Populate one isolated JEI ingredient search incrementally on client ticks.",
                "Visibility, tooltip, tag, creative-tab, and color helpers stay on the client thread.",
                "JEI waits for the complete index and publishes the sidebar once.",
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
                "Plugin callbacks keep JEI's original order and are never run concurrently. Plugins listed in",
                "mainThreadPlugins, runtime-available callbacks, and publication run on the client thread.",
                "Leaving a world, timing out, or losing the server",
                "cancels an in-progress build before",
                "its runtime can be published. Enabled by default.")
            .define("asyncStartup", true);
        ASYNC_MAIN_THREAD_PLUGINS = builder
            .comment(
                "JEI plugin IDs that must run on the client thread during asynchronous startup.",
                "Use a full plugin ID such as \"theurgy:jei_plugin\", or a mod ID such as \"theurgy\"",
                "to route every JEI plugin from that mod. Add entries when a log reports that a plugin",
                "called a main-thread-only API from justenoughthreads-start. JEI's own creative-tab",
                "enumeration and GUI runtime construction stay on the client thread automatically.")
            .defineListAllowEmpty(
                "mainThreadPlugins",
                JeiMainThreadPluginPolicy.DEFAULT_PLUGIN_IDS,
                JeiMainThreadPluginPolicy::isValidEntry
            );
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
        migrateLegacyConfig();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, CONFIG_NAME);
        registered = true;
    }
    //?} else {
    /*public static void register(ModContainer container) {
        if (registered) {
            return;
        }
        migrateLegacyConfig();
        container.registerConfig(ModConfig.Type.CLIENT, SPEC, CONFIG_NAME);
        registered = true;
    }
    *///?}

    private static void migrateLegacyConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path current = configDir.resolve(CONFIG_NAME);
        Path legacy = configDir.resolve(LEGACY_CONFIG_NAME);
        if (Files.exists(current) || !Files.isRegularFile(legacy)) {
            return;
        }
        try {
            Files.copy(legacy, current);
            JeiOptimize.LOGGER.info("Migrated legacy config {} to {}", LEGACY_CONFIG_NAME, CONFIG_NAME);
        } catch (IOException e) {
            JeiOptimize.LOGGER.warn(
                "Could not migrate legacy config {} to {}; defaults will be used if the new file is absent",
                LEGACY_CONFIG_NAME,
                CONFIG_NAME,
                e
            );
        }
    }
}