package com.tonywww.jeioptimize;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.MixinService;

import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skips a mixin when the JEI member it patches is missing or has a different descriptor, so a JEI
 * update switches the affected optimization off instead of crashing the game.
 *
 * <p>Mixin treats a callback descriptor mismatch or a missing {@code @Shadow} member as a fatal
 * error that {@code require = 0} does not suppress, so the check has to run before the mixin is
 * applied. Everything not listed here is left to Mixin's own (now non-fatal) target resolution.
 */
public final class JeiOptMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger(JeiOptimize.MOD_ID);
    private static final String MIXIN_PACKAGE = "com.tonywww.jeioptimize.mixin.";
    private static final String STARTER_PUBLISH_LEGACY_MIXIN = MIXIN_PACKAGE + "JeiStarterPublishLegacyMixin";
    private static final String STARTER_PUBLISH_MODERN_MIXIN = MIXIN_PACKAGE + "JeiStarterPublishModernMixin";
    private static final String CELESTIAL_REINFORCE_MIXIN =
        MIXIN_PACKAGE + "compat.CelestialForgeReinforceRecipeMixin";
    private static final String ULTIMATE_CAR_BUILDER_MIXIN =
        MIXIN_PACKAGE + "compat.UltimateCarRecipeBuilderMixin";
    private static final String ULTIMATE_CAR_CATEGORY_MIXIN =
        MIXIN_PACKAGE + "compat.UltimateCarRecipeCategoryMixin";
    private static final String IRON_FURNACES_PLUGIN_MIXIN =
        MIXIN_PACKAGE + "compat.IronFurnacesJeiPluginMixin";
    private static final String IRON_FURNACES_CATEGORY_MIXIN =
        MIXIN_PACKAGE + "compat.IronFurnacesGeneratorCategoryMixin";
    private static final String EMBERS_PLUGIN_MIXIN = MIXIN_PACKAGE + "compat.EmbersJeiPluginMixin";
    private static final String EMBERS_CATEGORY_MIXIN =
        MIXIN_PACKAGE + "compat.EmbersDawnstoneAnvilCategoryMixin";
    private static final String SFM_FALLING_ANVIL_MIXIN =
        MIXIN_PACKAGE + "compat.SfmFallingAnvilCategoryMixin";
    private static final String JEED_EFFECT_CLICK_MIXIN =
        MIXIN_PACKAGE + "compat.JeedEffectClickGuardMixin";
    private static final String BREWING_INDEX_FORGE_MIXIN = MIXIN_PACKAGE + "BrewingRecipeIndexForgeMixin";
    private static final String BREWING_INDEX_NEO_MIXIN = MIXIN_PACKAGE + "BrewingRecipeIndexNeoMixin";
    private static final String FORGE_ANVIL_BATCH_MIXIN = MIXIN_PACKAGE + "ForgeMenuBatchMixin$Anvil";
    private static final String FORGE_GRINDSTONE_BATCH_MIXIN = MIXIN_PACKAGE + "ForgeMenuBatchMixin$Grindstone";
    private static final String MENU_COMBINER_GUARD_MIXIN = MIXIN_PACKAGE + "MenuSlotUpdateGuardMixin$ItemCombiner";
    private static final String MENU_GRINDSTONE_GUARD_MIXIN = MIXIN_PACKAGE + "MenuSlotUpdateGuardMixin$Grindstone";
    private static final String LEGACY_RECIPE_LAYOUT_MIXIN = MIXIN_PACKAGE + "RecipeGuiLogicLegacyMixin";

    private static final Map<String, Requirement> REQUIREMENTS = Map.ofEntries(
        Map.entry(MIXIN_PACKAGE + "IngredientFilterMixin", Requirement.method(
            "async ingredient filter",
            "<init>",
            "(Lmezz/jei/gui/filter/IFilterTextSource;"
                + "Lmezz/jei/common/config/IClientConfig;"
                + "Lmezz/jei/common/config/IIngredientFilterConfig;"
                + "Lmezz/jei/api/runtime/IIngredientManager;"
                + "Ljava/util/Comparator;"
                + "Ljava/util/List;"
                + "Lmezz/jei/api/helpers/IModIdHelper;"
                + "Lmezz/jei/api/runtime/IIngredientVisibility;"
                + "Lmezz/jei/api/helpers/IColorHelper;"
                + "Lmezz/jei/common/config/IClientToggleState;)V")),
        Map.entry(MIXIN_PACKAGE + "AnvilRecipeControlMixin", Requirement.method(
            "anvil recipe hiding",
            "getRepairRecipes",
            "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                + "Lmezz/jei/api/ingredients/IIngredientHelper;)Ljava/util/stream/Stream;")),
        Map.entry(MIXIN_PACKAGE + "AnvilRecipeControlModernMixin", Requirement.method(
            "anvil recipe hiding",
            "getBookEnchantmentRecipes",
            "()Ljava/util/stream/Stream;")),
        Map.entry(MIXIN_PACKAGE + "AnvilRepresentativeContextMixin", Requirement.method(
            "anvil representative recipes",
            "getAnvilRecipes",
            "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;")),
        Map.entry(MIXIN_PACKAGE + "AnvilEnchantmentRepresentativeMixin", Requirement.method(
            "anvil representative recipes",
            "canEnchant",
            "(Lnet/minecraft/world/item/ItemStack;)Z")),
        Map.entry(MIXIN_PACKAGE + "GrindstoneRepresentativeMixin", Requirement.method(
            "grindstone representative recipes",
            "getGrindstoneRecipes",
            "(Lmezz/jei/api/runtime/IIngredientManager;"
                + "Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/List;")),
        Map.entry(MIXIN_PACKAGE + "FuelRecipeMakerMixin", Requirement.method(
            "fuel recipe compaction",
            "getFuelRecipes",
            "(Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;")),
        Map.entry(BREWING_INDEX_FORGE_MIXIN, Requirement.method(
            "indexed brewing lookup",
            "getNewPotions",
            "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                + "Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/util/Collection;"
                + "Ljava/util/Collection;Ljava/util/Collection;)Ljava/util/List;")),
        Map.entry(BREWING_INDEX_NEO_MIXIN, Requirement.method(
            "indexed brewing lookup",
            "getNewPotions",
            "(Lnet/minecraft/world/item/alchemy/PotionBrewing;"
                + "Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                + "Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/util/Collection;"
                + "Ljava/util/Collection;Ljava/util/Collection;)Ljava/util/List;")),
        Map.entry(FORGE_ANVIL_BATCH_MIXIN, Requirement.method(
            "batched hidden anvil updates",
            "setAnvilMenu",
            "(Lnet/minecraft/world/inventory/AnvilMenu;Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/inventory/AnvilMenu;")),
        Map.entry(FORGE_GRINDSTONE_BATCH_MIXIN, Requirement.method(
            "batched hidden grindstone updates",
            "getGrindstoneResult",
            "(Lnet/minecraft/world/inventory/GrindstoneMenu;Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;")),
        Map.entry(LEGACY_RECIPE_LAYOUT_MIXIN, Requirement.method(
            "legacy lazy recipe layouts",
            "createRecipeLayoutsWithButtons",
            "(Ljava/util/Set;Lmezz/jei/gui/recipes/lookups/IFocusedRecipes;"
                + "Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/entity/player/Player;)"
                + "Lmezz/jei/gui/recipes/layouts/IRecipeLayoutList;")),
        Map.entry(MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilMakerMixin", Requirement.method(
            "Iron's Spells Arcane Anvil compaction",
            "getRecipes")),
        Map.entry(MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilRecipeMixin", Requirement.method(
            "Iron's Spells Arcane Anvil compaction",
            "getRecipeItems")),
        Map.entry(MIXIN_PACKAGE + "compat.GeneratorGaloreJeiPluginMixin", Requirement.method(
            "Generator Galore solid-fuel compaction",
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V")),
        Map.entry(MIXIN_PACKAGE + "compat.MekanismRecipeRegistryHelperMixin", Requirement.method(
            "Mekanism Nutritional Liquifier compaction",
            "register",
            "(Lmezz/jei/api/registration/IRecipeRegistration;"
                + "Lmekanism/client/jei/MekanismJEIRecipeType;Ljava/util/List;)V")),
        Map.entry(MIXIN_PACKAGE + "compat.ThermalExpansionJeiPluginMixin", Requirement.method(
            "Thermal Stirling Dynamo fuel compaction",
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V")),
        Map.entry(CELESTIAL_REINFORCE_MIXIN, Requirement.method(
            "Celestial Forge Item Reinforce caching",
            "input",
            "()Lnet/minecraft/world/item/crafting/Ingredient;")),
        Map.entry(ULTIMATE_CAR_BUILDER_MIXIN, Requirement.method(
            "Ultimate Car Mod workshop compaction",
            "getAllRecipes",
            "()Ljava/util/List;")),
        Map.entry(ULTIMATE_CAR_CATEGORY_MIXIN, Requirement.method(
            "Ultimate Car Mod workshop compaction",
            "setRecipe")),
        Map.entry(IRON_FURNACES_PLUGIN_MIXIN, Requirement.method(
            "Iron Furnaces generator compaction",
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V")),
        Map.entry(IRON_FURNACES_CATEGORY_MIXIN, Requirement.method(
            "Iron Furnaces generator compaction",
            "setRecipe")),
        Map.entry(SFM_FALLING_ANVIL_MIXIN, Requirement.method(
            "SFM Falling Anvil optimization",
            "setRecipeForFallingAnvilDisenchantRecipe",
            "(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lmezz/jei/api/recipe/IFocusGroup;"
                + "Ljava/util/List;)V")),
        Map.entry(JEED_EFFECT_CLICK_MIXIN, Requirement.method(
            "JEED startup effect click guard",
            "onClickedEffect",
            "(Lnet/minecraft/world/effect/MobEffectInstance;DDI)V")),
        Map.entry(EMBERS_PLUGIN_MIXIN, Requirement.method(
            "Embers Dawnstone Anvil compaction",
            "addRecipes")),
        Map.entry(EMBERS_CATEGORY_MIXIN, Requirement.method(
            "Embers Dawnstone Anvil compaction",
            "setRecipe")),
        Map.entry(MIXIN_PACKAGE + "compat.ProductiveTreesLogStrippingCategoryMixin", Requirement.method(
            "Productive Trees log-stripping tool caching",
            "setRecipe")),
        Map.entry(MIXIN_PACKAGE + "compat.TinkersJeiPluginMixin", Requirement.method(
            "Tinkers casting compaction",
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V")),
        Map.entry(STARTER_PUBLISH_MODERN_MIXIN, Requirement.field(
            "async JEI runtime publication",
            "running",
            "Z")),
        Map.entry(MIXIN_PACKAGE + "ElementSearchMixin", Requirement.field(
            "async search preheat",
            "allElements",
            "Ljava/util/Map;")),
        Map.entry(MIXIN_PACKAGE + "RecipeManagerInternalCompactMixin", Requirement.method(
            "delayed recipe list compaction",
            "compact",
            "()V")),
        Map.entry(MIXIN_PACKAGE + "VanillaRecipesMixin", Requirement.field(
            "recipe ingredient pre-resolve",
            "recipeManager",
            null)),
        Map.entry(MIXIN_PACKAGE + "IngredientFilterModernMixin", Requirement.method(
            "async ingredient filter",
            "createElementSearch",
            "(Lmezz/jei/common/config/IClientConfig;"
                + "Lmezz/jei/gui/search/ElementPrefixParser;"
                + "Ljava/util/List;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)"
                + "Lmezz/jei/gui/search/IElementSearch;")),
        Map.entry(MIXIN_PACKAGE + "ItemStackListFactoryMixin", Requirement.method(
            "creative tab skipping",
            "create")),
        Map.entry(MIXIN_PACKAGE + "JeiRecipesGuiGuardMixin", Requirement.method(
            "startup recipe GUI guard",
            "show",
            "(Ljava/util/List;)V")),
        Map.entry(MIXIN_PACKAGE + "JeiGuiRenderGuardMixin", Requirement.method(
            "startup JEI render guard",
            "onDrawScreenPost",
            "(Lnet/minecraft/client/gui/screens/Screen;"
                + "Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    );
    private static final Map<String, ConfigGate> CONFIG_GATES = Map.ofEntries(
        Map.entry(BREWING_INDEX_FORGE_MIXIN, new ConfigGate("indexedBrewingLookup", true)),
        Map.entry(BREWING_INDEX_NEO_MIXIN, new ConfigGate("indexedBrewingLookup", true)),
        Map.entry(FORGE_ANVIL_BATCH_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(FORGE_GRINDSTONE_BATCH_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(MENU_COMBINER_GUARD_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(MENU_GRINDSTONE_GUARD_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(LEGACY_RECIPE_LAYOUT_MIXIN, new ConfigGate("lazyRecipeLayouts", true)),
        Map.entry(MIXIN_PACKAGE + "ElementSearchMixin", new ConfigGate("searchPreheat", false)),
        Map.entry(MIXIN_PACKAGE + "VanillaRecipesMixin", new ConfigGate("parallelVanillaRecipes", false))
    );
    private static final Map<String, Boolean> EARLY_CONFIG_VALUES = new HashMap<>();

    private static final AtomicFeature CELESTIAL_REINFORCE_FEATURE = new AtomicFeature(
        "Celestial Forge Item Reinforce caching",
        List.of(new TargetRequirement(
            "com.xiaoyue.celestial_forge.compat.ReinforceRecipeWrapper",
            List.of(
                Requirement.method("", "input", "()Lnet/minecraft/world/item/crafting/Ingredient;"),
                Requirement.method("", "result", "()Lnet/minecraft/world/item/crafting/Ingredient;")
            )
        ))
    );
    private static final AtomicFeature ULTIMATE_CAR_FEATURE = new AtomicFeature(
        "Ultimate Car Mod workshop compaction",
        List.of(
            new TargetRequirement(
                "de.maxhenkel.car.integration.jei.CarRecipeBuilder",
                List.of(Requirement.method("", "getAllRecipes", "()Ljava/util/List;"))
            ),
            new TargetRequirement(
                "de.maxhenkel.car.integration.jei.CarRecipeCategory",
                List.of(Requirement.method("", "setRecipe"))
            )
        )
    );
    private static final AtomicFeature IRON_FURNACES_FEATURE = new AtomicFeature(
        "Iron Furnaces generator compaction",
        List.of(
            new TargetRequirement(
                "ironfurnaces.jei.IronFurnacesJEIPlugin",
                List.of(Requirement.method(
                    "", "registerRecipes", "(Lmezz/jei/api/registration/IRecipeRegistration;)V"))
            ),
            new TargetRequirement(
                "ironfurnaces.jei.RecipeCategoryGeneratorRegular",
                List.of(Requirement.method("", "setRecipe"))
            ),
            new TargetRequirement(
                "ironfurnaces.jei.RecipeCategoryGeneratorSmoking",
                List.of(Requirement.method("", "setRecipe"))
            )
        )
    );
    private static final AtomicFeature EMBERS_DAWNSTONE_ANVIL_FEATURE = new AtomicFeature(
        "Embers Dawnstone Anvil compaction",
        List.of(
            new TargetRequirement(
                "com.rekindled.embers.compat.jei.JEIPlugin",
                List.of(Requirement.method("", "addRecipes"))
            ),
            new TargetRequirement(
                "com.rekindled.embers.compat.jei.DawnstoneAnvilCategory",
                List.of(Requirement.method("", "setRecipe"))
            ),
            new TargetRequirement(
                "com.rekindled.embers.recipe.AnvilDisplayRecipe",
                List.of(
                    Requirement.method(
                        "",
                        "<init>",
                        "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;Ljava/util/List;"
                            + "Lnet/minecraft/world/item/crafting/Ingredient;)V"
                    ),
                    Requirement.field("", "id", "Lnet/minecraft/resources/ResourceLocation;"),
                    Requirement.field("", "outputs", "Ljava/util/List;"),
                    Requirement.field("", "inputs", "Ljava/util/List;"),
                    Requirement.field("", "ingredient", "Lnet/minecraft/world/item/crafting/Ingredient;")
                )
            )
        )
    );
    private static final AtomicFeature SFM_FALLING_ANVIL_FEATURE = new AtomicFeature(
        "SFM Falling Anvil optimization",
        List.of(
            new TargetRequirement(
                "ca.teamdman.sfm.client.jei.FallingAnvilJEICategory",
                List.of(Requirement.method(
                    "",
                    "setRecipeForFallingAnvilDisenchantRecipe",
                    "(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lmezz/jei/api/recipe/IFocusGroup;"
                        + "Ljava/util/List;)V"
                ))
            ),
            new TargetRequirement(
                "ca.teamdman.sfm.common.enchantment.SFMEnchantmentKey",
                List.of(Requirement.method(
                    "",
                    "canEnchant",
                    "(Lnet/minecraft/world/item/ItemStack;)Z"
                ))
            )
        )
    );
    private static final AtomicFeature JEED_EFFECT_CLICK_FEATURE = new AtomicFeature(
        "JEED startup effect click guard",
        List.of(new TargetRequirement(
            "net.mehvahdjukaar.jeed.plugin.jei.JEIPlugin",
            List.of(
                Requirement.method(
                    "",
                    "onClickedEffect",
                    "(Lnet/minecraft/world/effect/MobEffectInstance;DDI)V"
                ),
                Requirement.field("", "JEI_HELPERS", "Lmezz/jei/api/helpers/IJeiHelpers;"),
                Requirement.field("", "JEI_RUNTIME", "Lmezz/jei/api/runtime/IJeiRuntime;")
            )
        ))
    );
    private static final AtomicFeature BREWING_INDEX_FORGE_FEATURE = new AtomicFeature(
        "indexed brewing lookup",
        List.of(
            new TargetRequirement(
                "mezz.jei.library.util.BrewingRecipeMakerCommon",
                List.of(Requirement.method(
                    "",
                    "getNewPotions",
                    "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                        + "Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/util/Collection;"
                        + "Ljava/util/Collection;Ljava/util/Collection;)Ljava/util/List;"
                ))
            ),
            new TargetRequirement(
                "mezz.jei.library.plugins.vanilla.anvil.AnvilHelper",
                List.of(Requirement.method("", "setAnvilMenu"))
            )
        )
    );
    private static final AtomicFeature BREWING_INDEX_NEO_FEATURE = new AtomicFeature(
        "indexed brewing lookup",
        List.of(new TargetRequirement(
            "mezz.jei.library.util.BrewingRecipeMakerCommon",
            List.of(Requirement.method(
                "",
                "getNewPotions",
                "(Lnet/minecraft/world/item/alchemy/PotionBrewing;"
                    + "Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                    + "Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/util/Collection;"
                    + "Ljava/util/Collection;Ljava/util/Collection;)Ljava/util/List;"
            ))
        ))
    );
    private static final AtomicFeature FORGE_MENU_BATCH_FEATURE = new AtomicFeature(
        "batched hidden menu updates",
        List.of(
            new TargetRequirement(
                "mezz.jei.library.plugins.vanilla.anvil.AnvilHelper",
                List.of(Requirement.method("", "setAnvilMenu"))
            ),
            new TargetRequirement(
                "mezz.jei.forge.platform.RecipeHelper",
                List.of(Requirement.method("", "getGrindstoneResult"))
            ),
            new TargetRequirement(
                "net.minecraft.world.inventory.ItemCombinerMenu",
                List.of(Requirement.method(
                    "",
                    "slotsChanged",
                    "(Lnet/minecraft/world/Container;)V"
                ))
            ),
            new TargetRequirement(
                "net.minecraft.world.inventory.GrindstoneMenu",
                List.of(Requirement.method(
                    "",
                    "slotsChanged",
                    "(Lnet/minecraft/world/Container;)V"
                ))
            )
        )
    );
    private static final Map<String, AtomicFeature> ATOMIC_FEATURES = Map.ofEntries(
        Map.entry(CELESTIAL_REINFORCE_MIXIN, CELESTIAL_REINFORCE_FEATURE),
        Map.entry(ULTIMATE_CAR_BUILDER_MIXIN, ULTIMATE_CAR_FEATURE),
        Map.entry(ULTIMATE_CAR_CATEGORY_MIXIN, ULTIMATE_CAR_FEATURE),
        Map.entry(IRON_FURNACES_PLUGIN_MIXIN, IRON_FURNACES_FEATURE),
        Map.entry(IRON_FURNACES_CATEGORY_MIXIN, IRON_FURNACES_FEATURE),
        Map.entry(EMBERS_PLUGIN_MIXIN, EMBERS_DAWNSTONE_ANVIL_FEATURE),
        Map.entry(EMBERS_CATEGORY_MIXIN, EMBERS_DAWNSTONE_ANVIL_FEATURE),
        Map.entry(SFM_FALLING_ANVIL_MIXIN, SFM_FALLING_ANVIL_FEATURE),
        Map.entry(JEED_EFFECT_CLICK_MIXIN, JEED_EFFECT_CLICK_FEATURE),
        Map.entry(BREWING_INDEX_FORGE_MIXIN, BREWING_INDEX_FORGE_FEATURE),
        Map.entry(BREWING_INDEX_NEO_MIXIN, BREWING_INDEX_NEO_FEATURE),
        Map.entry(FORGE_ANVIL_BATCH_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(FORGE_GRINDSTONE_BATCH_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(MENU_COMBINER_GUARD_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(MENU_GRINDSTONE_GUARD_MIXIN, FORGE_MENU_BATCH_FEATURE)
    );

    private final Map<String, ClassNode> targetCache = new HashMap<>();
    private final Map<AtomicFeature, Boolean> atomicFeatureCompatibility = new HashMap<>();

    /** Types a mixin names in its own signatures; Mixin fails hard if one of them has moved. */
    private static final Map<String, RequiredClass> REQUIRED_CLASSES = Map.of(
        MIXIN_PACKAGE + "ElementSearchMixin",
        new RequiredClass("async search preheat", "mezz.jei.core.search.PrefixInfo")
    );

    /** One of these covers each JEI generation, so the one that does not match is not a problem. */
    private static final Set<String> VARIANTS = Set.of(
        MIXIN_PACKAGE + "AnvilRecipeControlMixin",
        MIXIN_PACKAGE + "AnvilRecipeControlModernMixin",
        MIXIN_PACKAGE + "IngredientFilterMixin",
        MIXIN_PACKAGE + "IngredientFilterModernMixin",
        MIXIN_PACKAGE + "GrindstoneRepresentativeMixin",
        BREWING_INDEX_FORGE_MIXIN,
        BREWING_INDEX_NEO_MIXIN,
        LEGACY_RECIPE_LAYOUT_MIXIN,
        FORGE_ANVIL_BATCH_MIXIN,
        FORGE_GRINDSTONE_BATCH_MIXIN,
        MENU_COMBINER_GUARD_MIXIN,
        MENU_GRINDSTONE_GUARD_MIXIN,
        STARTER_PUBLISH_LEGACY_MIXIN,
        STARTER_PUBLISH_MODERN_MIXIN
    );

    private static final Set<String> OPTIONAL_MIXINS = Set.of(
        MIXIN_PACKAGE + "GrindstoneRepresentativeMixin",
        MIXIN_PACKAGE + "compat.CelestialForgeReinforceRecipeMixin",
        EMBERS_CATEGORY_MIXIN,
        EMBERS_PLUGIN_MIXIN,
        MIXIN_PACKAGE + "compat.GeneratorGaloreJeiPluginMixin",
        MIXIN_PACKAGE + "compat.IronFurnacesGeneratorCategoryMixin",
        MIXIN_PACKAGE + "compat.IronFurnacesJeiPluginMixin",
        MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilMakerMixin",
        MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilRecipeMixin",
        JEED_EFFECT_CLICK_MIXIN,
        MIXIN_PACKAGE + "compat.MekanismRecipeRegistryHelperMixin",
        MIXIN_PACKAGE + "compat.ProductiveTreesLogStrippingCategoryMixin",
        MIXIN_PACKAGE + "compat.SfmFallingAnvilCategoryMixin",
        MIXIN_PACKAGE + "compat.ThermalExpansionJeiPluginMixin",
        MIXIN_PACKAGE + "compat.TinkersJeiPluginMixin",
        MIXIN_PACKAGE + "compat.UltimateCarRecipeBuilderMixin",
        MIXIN_PACKAGE + "compat.UltimateCarRecipeCategoryMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!readEarlyBoolean("enabled", true)) {
            return false;
        }
        ConfigGate configGate = CONFIG_GATES.get(mixinClassName);
        if (configGate != null && !readEarlyBoolean(configGate.key(), configGate.defaultValue())) {
            LOGGER.debug(
                "JEI Optimize skipped {} because {} is disabled before mixin application.",
                mixinClassName,
                configGate.key()
            );
            return false;
        }
        if (STARTER_PUBLISH_LEGACY_MIXIN.equals(mixinClassName)) {
            ClassNode target = readTarget(targetClassName);
            if (target == null) {
                LOGGER.warn(
                    "JEI Optimize could not read {}, so its async runtime publication stays off.",
                    targetClassName);
                return false;
            }
            boolean hasRunningField = Requirement.field("", "running", "Z").isPresentIn(target);
            if (!hasRunningField) {
                return true;
            }
            LOGGER.debug(
                "JEI Optimize skipped {}: this JEI build uses the running-field publication variant.",
                mixinClassName);
            return false;
        }

        AtomicFeature atomicFeature = ATOMIC_FEATURES.get(mixinClassName);
        if (atomicFeature != null
            && !isAtomicFeatureCompatible(atomicFeature, VARIANTS.contains(mixinClassName))) {
            return false;
        }

        RequiredClass requiredClass = REQUIRED_CLASSES.get(mixinClassName);
        if (requiredClass != null && readTarget(requiredClass.className()) == null) {
            LOGGER.warn(
                "JEI Optimize turned off its {} optimization: this JEI build no longer has {}. "
                    + "JEI keeps its normal behavior; the mod needs an update for this JEI version.",
                requiredClass.feature(), requiredClass.className());
            return false;
        }

        Requirement requirement = REQUIREMENTS.get(mixinClassName);
        if (requirement == null) {
            return true;
        }

        ClassNode target = readTarget(targetClassName);
        if (target == null) {
            if (OPTIONAL_MIXINS.contains(mixinClassName)) {
                LOGGER.debug(
                    "JEI Optimize skipped optional {} because {} is not installed in this runtime.",
                    mixinClassName, targetClassName);
                return false;
            }
            LOGGER.warn(
                "JEI Optimize could not read {}, so its {} optimization stays off and JEI keeps its normal behavior.",
                targetClassName, requirement.feature());
            return false;
        }
        if (requirement.isPresentIn(target)) {
            return true;
        }

        if (VARIANTS.contains(mixinClassName)) {
            LOGGER.debug(
                "JEI Optimize skipped {}: this JEI build's {} does not declare {}; another variant should cover it.",
                mixinClassName, targetClassName, requirement.describe());
            return false;
        }

        LOGGER.warn(
            "JEI Optimize turned off its {} optimization: this JEI build's {} no longer declares {}. "
                + "JEI keeps its normal behavior; the mod needs an update for this JEI version.",
            requirement.feature(), targetClassName, requirement.describe());
        return false;
    }

    private boolean isAtomicFeatureCompatible(AtomicFeature feature, boolean variant) {
        Boolean cached = atomicFeatureCompatibility.get(feature);
        if (cached != null) {
            return cached;
        }

        boolean sawTarget = false;
        String missing = null;
        for (TargetRequirement targetRequirement : feature.targets()) {
            ClassNode target = readTarget(targetRequirement.className());
            if (target == null) {
                if (missing == null) {
                    missing = "class " + targetRequirement.className();
                }
                continue;
            }
            sawTarget = true;
            for (Requirement requirement : targetRequirement.requirements()) {
                if (!requirement.isPresentIn(target) && missing == null) {
                    missing = targetRequirement.className() + " " + requirement.describe();
                }
            }
        }

        boolean compatible = missing == null;
        atomicFeatureCompatibility.put(feature, compatible);
        if (!compatible) {
            if (variant) {
                LOGGER.debug(
                    "JEI Optimize skipped optional {} variant because its complete ABI contract is missing {}.",
                    feature.name(), missing);
            } else if (sawTarget) {
                LOGGER.warn(
                    "JEI Optimize turned off its {} optimization because its complete ABI contract is missing {}. "
                        + "The target mod keeps its normal behavior.",
                    feature.name(), missing);
            } else {
                LOGGER.debug(
                    "JEI Optimize skipped optional {} because its target mod is not installed.",
                    feature.name());
            }
        }
        return compatible;
    }

    private ClassNode readTarget(String targetClassName) {
        if (targetCache.containsKey(targetClassName)) {
            return targetCache.get(targetClassName);
        }
        ClassNode node = null;
        try {
            IClassBytecodeProvider provider = MixinService.getService().getBytecodeProvider();
            try {
                node = provider.getClassNode(targetClassName);
            } catch (ClassNotFoundException e) {
                node = provider.getClassNode(targetClassName.replace('.', '/'));
            }
        } catch (Exception | LinkageError e) {
            LOGGER.debug("JEI Optimize could not read target class {}", targetClassName, e);
        }
        targetCache.put(targetClassName, node);
        return node;
    }

    private static boolean readEarlyBoolean(String key, boolean defaultValue) {
        Boolean cached = EARLY_CONFIG_VALUES.get(key);
        if (cached != null) {
            return cached;
        }
        boolean value = defaultValue;
        Path config = Path.of("config", JeiOptimize.MOD_ID + "-client.toml");
        if (Files.isReadable(config)) {
            try {
                for (String line : Files.readAllLines(config)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int equals = trimmed.indexOf('=');
                    if (equals < 0 || !trimmed.substring(0, equals).trim().equals(key)) {
                        continue;
                    }
                    String raw = trimmed.substring(equals + 1).split("#", 2)[0].trim();
                    if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                        value = Boolean.parseBoolean(raw);
                    }
                    break;
                }
            } catch (Exception error) {
                LOGGER.debug("JEI Optimize could not pre-read {} from {}", key, config, error);
            }
        }
        EARLY_CONFIG_VALUES.put(key, value);
        return value;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private record RequiredClass(String feature, String className) {
    }

    private record AtomicFeature(String name, List<TargetRequirement> targets) {
    }

    private record ConfigGate(String key, boolean defaultValue) {
    }

    private record TargetRequirement(String className, List<Requirement> requirements) {
    }

    private record Requirement(String feature, String memberName, String descriptor, boolean isField) {
        static Requirement method(String feature, String name, String descriptor) {
            return new Requirement(feature, name, descriptor, false);
        }

        /** A null descriptor matches on name alone, for methods whose shape differs across JEI versions. */
        static Requirement method(String feature, String name) {
            return new Requirement(feature, name, null, false);
        }

        /** A null descriptor matches on name alone, for members whose type is remapped at runtime. */
        static Requirement field(String feature, String name, String descriptor) {
            return new Requirement(feature, name, descriptor, true);
        }

        boolean isPresentIn(ClassNode target) {
            if (isField) {
                for (FieldNode field : target.fields) {
                    if (memberName.equals(field.name) && (descriptor == null || descriptor.equals(field.desc))) {
                        return true;
                    }
                }
                return false;
            }
            for (MethodNode method : target.methods) {
                if (memberName.equals(method.name) && (descriptor == null || descriptor.equals(method.desc))) {
                    return true;
                }
            }
            return false;
        }

        String describe() {
            return (isField ? "field " : "method ") + memberName + (descriptor == null ? "" : descriptor);
        }
    }
}
