package com.tonywww.jeioptimize;

import com.tonywww.jeioptimize.runtime.JeiOptCompatibilityState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
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
import java.util.function.Function;

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
    private static final String STARTER_MIXIN = MIXIN_PACKAGE + "JeiStarterMixin";
    private static final String PLUGIN_CALLER_MIXIN = MIXIN_PACKAGE + "PluginCallerMixin";
    private static final String STARTER_PUBLISH_LEGACY_MIXIN = MIXIN_PACKAGE + "JeiStarterPublishLegacyMixin";
    private static final String STARTER_PUBLISH_MODERN_MIXIN = MIXIN_PACKAGE + "JeiStarterPublishModernMixin";
    private static final String CLIENT_TASK_PUMP_GUARD_MIXIN =
        MIXIN_PACKAGE + "ClientTaskPumpGuardMixin";
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
    private static final String IRONS_SPELLS_MAKER_MIXIN =
        MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilMakerMixin";
    private static final String IRONS_SPELLS_RECIPE_MIXIN =
        MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilRecipeMixin";
    private static final String IRONS_SPELLS_RECIPE_CLASS =
        "io.redspace.ironsspellbooks.jei.ArcaneAnvilJeiRecipe";
    private static final String GENERATOR_GALORE_PLUGIN_MIXIN =
        MIXIN_PACKAGE + "compat.GeneratorGaloreJeiPluginMixin";
    private static final String MEKANISM_RECIPE_REGISTRY_MIXIN =
        MIXIN_PACKAGE + "compat.MekanismRecipeRegistryHelperMixin";
    private static final String GTCEU_RECIPE_REGISTRATION_MIXIN =
        MIXIN_PACKAGE + "compat.GtceuRecipeRegistrationMixin";
    private static final String GTCEU_RECIPE_CATEGORY_CLASS =
        "com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeJEICategory";
    private static final String MINECOLONIES_JEI_PLUGIN_MIXIN =
        MIXIN_PACKAGE + "compat.MineColoniesJeiPluginMixin";
    private static final String MINECOLONIES_EQUIPMENT_TYPE_MIXIN =
        MIXIN_PACKAGE + "compat.MineColoniesEquipmentTypeEntryMixin";
    private static final String MINECOLONIES_ATTRIBUTE_MODIFIERS_MIXIN =
        MIXIN_PACKAGE + "compat.MineColoniesAttributeModifiersMixin";
    private static final String MINECOLONIES_TWEAKS_EXTENSION_MIXIN =
        MIXIN_PACKAGE + "compat.MineColoniesTweaksToolTypeExtensionMixin";
    private static final String MINECOLONIES_TWEAKS_TAGS_MIXIN =
        MIXIN_PACKAGE + "compat.MineColoniesTweaksToolTypeTagsMixin";
    private static final String MINECOLONIES_JEI_PLUGIN_CLASS =
        "com.minecolonies.core.compatibility.jei.JEIPlugin";
    private static final String MINECOLONIES_TOOLS_ANALYZER_CLASS =
        "com.minecolonies.core.colony.crafting.ToolsAnalyzer";
    private static final String MINECOLONIES_EQUIPMENT_TYPE_CLASS =
        "com.minecolonies.api.equipment.registry.EquipmentTypeEntry";
    private static final String MINECOLONIES_TWEAKS_EXTENSION_CLASS =
        "steve_gall.minecolonies_tweaks.api.common.tool.ToolTypeExtension";
    private static final String MINECOLONIES_TWEAKS_TAGS_CLASS =
        "steve_gall.minecolonies_tweaks.api.common.tool.ToolTypeTags";
    private static final String THERMAL_EXPANSION_PLUGIN_MIXIN =
        MIXIN_PACKAGE + "compat.ThermalExpansionJeiPluginMixin";
    private static final String TINKERS_PLUGIN_MIXIN = MIXIN_PACKAGE + "compat.TinkersJeiPluginMixin";
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
    private static final String ANVIL_REPRESENTATIVE_CONTEXT_MIXIN =
        MIXIN_PACKAGE + "AnvilRepresentativeContextMixin";
    private static final String ANVIL_ENCHANTMENT_REPRESENTATIVE_MIXIN =
        MIXIN_PACKAGE + "AnvilEnchantmentRepresentativeMixin";
    private static final String GRINDSTONE_REPRESENTATIVE_MIXIN = MIXIN_PACKAGE + "GrindstoneRepresentativeMixin";
    private static final String GRINDSTONE_DISENCHANT_LEGACY_MIXIN =
        MIXIN_PACKAGE + "GrindstoneDisenchantLegacyMixin";
    private static final String GRINDSTONE_DISENCHANT_MODERN_MIXIN =
        MIXIN_PACKAGE + "GrindstoneDisenchantModernMixin";
    private static final String RECIPE_MANAGER_SAFE_DIAGNOSTIC_MIXIN =
        MIXIN_PACKAGE + "RecipeManagerSafeDiagnosticMixin";
    private static final String EXTENDABLE_RECIPE_HELPER_MIXIN =
        MIXIN_PACKAGE + "ExtendableRecipeCategoryHelperMixin";
    private static final String STORAGE_IN_MOTION_SUBTYPE_MIXIN =
        MIXIN_PACKAGE + "compat.StorageInMotionSubtypeMixin";
    private static final String SOPHISTICATED_SHULKER_ACCESSOR_MIXIN =
        MIXIN_PACKAGE + "accessor.SophisticatedStorageShulkerRecipeAccessor";
    private static final String EXTENDABLE_RECIPE_HELPER_CLASS =
        "mezz.jei.library.recipes.ExtendableRecipeCategoryHelper";
    private static final String SOPHISTICATED_SHULKER_RECIPE_CLASS =
        "net.p3pp3rf1y.sophisticatedstorage.crafting.ShulkerBoxFromVanillaShapelessRecipe";
    private static final String SOPHISTICATED_STORAGE_JEI_PLUGIN_CLASS =
        "net.p3pp3rf1y.sophisticatedstorage.compat.recipeviewers.jei.StorageJeiPlugin";
    private static final String STORAGE_IN_MOTION_JEI_PLUGIN_CLASS =
        "net.p3pp3rf1y.sophisticatedstorageinmotion.compat.recipeviewers.jei.StorageInMotionJeiPlugin";
    private static final String ANVIL_RECIPE_MAKER_CLASS =
        "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker";
    private static final String ANVIL_ENCHANTMENT_DATA_CLASS = ANVIL_RECIPE_MAKER_CLASS + "$EnchantmentData";
    private static final Requirement ANVIL_REPRESENTATIVE_ENTRY_POINT = Requirement.method(
        "anvil representative recipes",
        "getAnvilRecipes",
        "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
            + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;"
    );
    private static final Requirement ANVIL_REPRESENTATIVE_CAN_ENCHANT = Requirement.method(
        "anvil representative recipes",
        "canEnchant",
        "(Lnet/minecraft/world/item/ItemStack;)Z"
    );
    private static final InvocationRequirement RECIPE_DEBUG_INVOCATION = new InvocationRequirement(
        "addRecipe",
        "(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Ljava/util/Set;)Z",
        "mezz/jei/library/util/RecipeDebugUtil",
        "getDebugInfoFromRecipe",
        "(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;"
            + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/lang/String;"
    );
    private static final String UNHANDLED_RECIPE_DEBUG_MESSAGE =
        "Recipe not added because the recipe category cannot handle it: {}";
    private static final InvocationRequirement PLUGIN_CALLBACK_INVOCATION = new InvocationRequirement(
        "callOnPlugins",
        "(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V",
        "java/util/function/Consumer",
        "accept",
        "(Ljava/lang/Object;)V"
    );
    private static final List<Requirement> CLIENT_TASK_PUMP_GUARD_REQUIREMENTS = List.of(
        Requirement.method("off-main client task-pump guard", "pollTask", "()Z"),
        Requirement.method("off-main client task-pump guard", "m_7245_", "()Z")
    );
    private static final Requirement SOPHISTICATED_REGISTER_ITEM_SUBTYPES = Requirement.method(
        "Sophisticated Storage subtype registration",
        "registerItemSubtypes",
        "(Lmezz/jei/api/registration/ISubtypeRegistration;)V"
    );
    private static final InvocationRequirement SOPHISTICATED_SUBTYPE_TABLE_INVOCATION =
        new InvocationRequirement(
            "registerItemSubtypes",
            "(Lmezz/jei/api/registration/ISubtypeRegistration;)V",
            "net/p3pp3rf1y/sophisticatedstorage/compat/recipeviewers/common/subtypes/SubtypeInterpreters",
            "getSubtypeInterpreters",
            "()Ljava/util/Map;"
        );
    private static final Requirement THERMAL_REGISTER_RECIPES = Requirement.method(
        "Thermal Stirling Dynamo fuel compaction",
        "registerRecipes",
        "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
    );
    private static final InvocationRequirement THERMAL_ADD_RECIPES_INVOCATION = new InvocationRequirement(
        "registerRecipes",
        "(Lmezz/jei/api/registration/IRecipeRegistration;)V",
        "mezz/jei/api/registration/IRecipeRegistration",
        "addRecipes",
        "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
    );
    private static final Requirement THERMAL_STIRLING_CONSTRUCTOR = Requirement.method(
        "Thermal Stirling Dynamo fuel compaction",
        "<init>",
        "(Lnet/minecraft/resources/ResourceLocation;ILjava/util/List;Ljava/util/List;)V"
    );
    private static final List<Requirement> THERMAL_FUEL_ACCESSORS = List.of(
        Requirement.method("", "getInputItems", "()Ljava/util/List;"),
        Requirement.method("", "getInputFluids", "()Ljava/util/List;"),
        Requirement.method("", "getEnergy", "()I")
    );
    private static final Requirement GENERATOR_GALORE_REGISTER_LAMBDA = Requirement.method(
        "Generator Galore solid-fuel compaction",
        "lambda$registerRecipes$14",
        "(Lmezz/jei/api/registration/IRecipeRegistration;Ljava/util/List;Ljava/util/List;"
            + "Ljava/util/List;Ljava/util/List;Lnet/minecraft/resources/ResourceLocation;"
            + "Lcy/jdkdigital/generatorgalore/util/GeneratorObject;)V"
    );
    private static final InvocationRequirement GENERATOR_GALORE_ADD_RECIPES_INVOCATION =
        new InvocationRequirement(
            GENERATOR_GALORE_REGISTER_LAMBDA.memberName(),
            GENERATOR_GALORE_REGISTER_LAMBDA.descriptor(),
            "mezz/jei/api/registration/IRecipeRegistration",
            "addRecipes",
            "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
        );
    private static final Requirement MEKANISM_REGISTER_RECIPES = Requirement.method(
        "Mekanism Nutritional Liquifier compaction",
        "register",
        "(Lmezz/jei/api/registration/IRecipeRegistration;"
            + "Lmekanism/client/jei/MekanismJEIRecipeType;Ljava/util/List;)V"
    );
    private static final InvocationRequirement MEKANISM_ADD_RECIPES_INVOCATION = new InvocationRequirement(
        MEKANISM_REGISTER_RECIPES.memberName(),
        MEKANISM_REGISTER_RECIPES.descriptor(),
        "mezz/jei/api/registration/IRecipeRegistration",
        "addRecipes",
        "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
    );
    private static final Requirement GTCEU_REGISTER_RECIPES = Requirement.method(
        "GTCEu recipe registration batching",
        "registerRecipes",
        "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
    );
    private static final InvocationRequirement GTCEU_COPY_RECIPES_INVOCATION = new InvocationRequirement(
        GTCEU_REGISTER_RECIPES.memberName(),
        GTCEU_REGISTER_RECIPES.descriptor(),
        "java/util/List",
        "copyOf",
        "(Ljava/util/Collection;)Ljava/util/List;"
    );
    private static final InvocationRequirement GTCEU_ADD_RECIPES_INVOCATION = new InvocationRequirement(
        GTCEU_REGISTER_RECIPES.memberName(),
        GTCEU_REGISTER_RECIPES.descriptor(),
        "mezz/jei/api/registration/IRecipeRegistration",
        "addRecipes",
        "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
    );
    private static final Requirement MINECOLONIES_REGISTER_RECIPES = Requirement.method(
        "MineColonies JEI tool scan caching",
        "registerRecipes",
        "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
    );
    private static final InvocationRequirement MINECOLONIES_FIND_TOOLS_INVOCATION = new InvocationRequirement(
        MINECOLONIES_REGISTER_RECIPES.memberName(),
        MINECOLONIES_REGISTER_RECIPES.descriptor(),
        "com/minecolonies/core/compatibility/jei/ToolRecipeCategory",
        "findRecipes",
        "()Ljava/util/List;"
    );
    private static final Requirement MINECOLONIES_FIND_TOOLS = Requirement.method(
        "MineColonies JEI tool scan caching",
        "findTools",
        "()Ljava/util/List;"
    );
    private static final Requirement MINECOLONIES_TRY_ADDING_TOOL = Requirement.method(
        "MineColonies JEI tool scan caching",
        "tryAddingToolWithLevel",
        "(Ljava/util/Map;Lcom/minecolonies/api/equipment/registry/EquipmentTypeEntry;"
            + "Lnet/minecraft/world/item/ItemStack;)V"
    );
    private static final Requirement MINECOLONIES_CHECK_IS_EQUIPMENT = Requirement.method(
        "MineColonies JEI tool scan caching",
        "checkIsEquipment",
        "(Lnet/minecraft/world/item/ItemStack;)Z"
    );
    private static final Requirement MINECOLONIES_GET_MINING_LEVEL = Requirement.method(
        "MineColonies JEI tool scan caching",
        "getMiningLevel",
        "(Lnet/minecraft/world/item/ItemStack;)I"
    );
    private static final InvocationRequirement MINECOLONIES_CHECK_IS_EQUIPMENT_INVOCATION =
        new InvocationRequirement(
            MINECOLONIES_FIND_TOOLS.memberName(),
            MINECOLONIES_FIND_TOOLS.descriptor(),
            "com/minecolonies/api/equipment/registry/EquipmentTypeEntry",
            MINECOLONIES_CHECK_IS_EQUIPMENT.memberName(),
            MINECOLONIES_CHECK_IS_EQUIPMENT.descriptor()
        );
    private static final InvocationRequirement MINECOLONIES_GET_MINING_LEVEL_INVOCATION =
        new InvocationRequirement(
            MINECOLONIES_TRY_ADDING_TOOL.memberName(),
            MINECOLONIES_TRY_ADDING_TOOL.descriptor(),
            "com/minecolonies/api/equipment/registry/EquipmentTypeEntry",
            MINECOLONIES_GET_MINING_LEVEL.memberName(),
            MINECOLONIES_GET_MINING_LEVEL.descriptor()
        );
    private static final List<Requirement> MINECOLONIES_TWEAKS_EXTENSION_REQUIREMENTS = List.of(
        Requirement.method(
            "MineColonies JEI tool scan caching",
            "isCustomTool",
            "(Lnet/minecraft/world/item/ItemStack;)Z"
        ),
        Requirement.method(
            "MineColonies JEI tool scan caching",
            "getTagLevel",
            "(Lnet/minecraft/world/item/ItemStack;)I"
        ),
        Requirement.method(
            "MineColonies JEI tool scan caching",
            "getCustomLevel",
            "(Lnet/minecraft/world/item/ItemStack;)I"
        )
    );
    private static final Requirement MINECOLONIES_TWEAKS_BLACKLIST = Requirement.method(
        "MineColonies JEI tool scan caching",
        "isInBlacklist",
        "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;)Z"
    );
    private static final Requirement TINKERS_REGISTER_RECIPES = Requirement.method(
        "Tinkers casting compaction",
        "registerRecipes",
        "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
    );
    private static final InvocationRequirement TINKERS_ADD_RECIPES_INVOCATION = new InvocationRequirement(
        TINKERS_REGISTER_RECIPES.memberName(),
        TINKERS_REGISTER_RECIPES.descriptor(),
        "mezz/jei/api/registration/IRecipeRegistration",
        "addRecipes",
        "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
    );
    private static final Requirement IRON_FURNACES_REGISTER_RECIPES = Requirement.method(
        "Iron Furnaces generator compaction",
        "registerRecipes",
        "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
    );
    private static final Requirement IRON_FURNACES_SET_RECIPE = Requirement.method(
        "Iron Furnaces generator compaction",
        "setRecipe",
        "(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;"
            + "Lironfurnaces/recipes/SimpleGeneratorRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V"
    );
    private static final InvocationRequirement IRON_FURNACES_ADD_RECIPES_INVOCATION = new InvocationRequirement(
        IRON_FURNACES_REGISTER_RECIPES.memberName(),
        IRON_FURNACES_REGISTER_RECIPES.descriptor(),
        "mezz/jei/api/registration/IRecipeRegistration",
        "addRecipes",
        "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
    );

    private static final Requirement GRINDSTONE_ENTRY_POINT = Requirement.method(
        "grindstone representative recipes",
        "getGrindstoneRecipes",
        "(Lmezz/jei/api/runtime/IIngredientManager;"
            + "Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/List;"
    );
    private static final Requirement GRINDSTONE_MODERN_CAN_ENCHANT = Requirement.method(
        "",
        "canEnchant",
        "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/enchantment/Enchantment;"
            + "Lnet/minecraft/resources/ResourceLocation;)Z"
    );
    private static final InvocationRequirement GRINDSTONE_MODERN_INVOCATION = new InvocationRequirement(
        "getDisenchantRecipes",
        "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
        "mezz/jei/library/plugins/vanilla/grindstone/GrindstoneRecipeMaker",
        "canEnchant",
        "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/enchantment/Enchantment;"
            + "Lnet/minecraft/resources/ResourceLocation;)Z"
    );
    private static final List<InvocationRequirement> GRINDSTONE_LEGACY_INVOCATIONS = List.of(
        new InvocationRequirement(
            "getDisenchantRecipes",
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            "mezz/jei/common/platform/IPlatformRecipeHelper",
            "isItemEnchantable",
            "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        ),
        new InvocationRequirement(
            "getDisenchantRecipes",
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/stream/Stream;",
            "mezz/jei/common/platform/IPlatformRecipeHelper",
            "isItemEnchantable",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;)Z"
        )
    );

    private static final Map<String, Requirement> REQUIREMENTS = Map.ofEntries(
        Map.entry(PLUGIN_CALLER_MIXIN, Requirement.method(
            "client-thread plugin callbacks",
            "callOnPlugins",
            "(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V")),
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
        Map.entry(ANVIL_REPRESENTATIVE_CONTEXT_MIXIN, ANVIL_REPRESENTATIVE_ENTRY_POINT),
        Map.entry(ANVIL_ENCHANTMENT_REPRESENTATIVE_MIXIN, ANVIL_REPRESENTATIVE_CAN_ENCHANT),
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
        Map.entry(GENERATOR_GALORE_PLUGIN_MIXIN, GENERATOR_GALORE_REGISTER_LAMBDA),
        Map.entry(MEKANISM_RECIPE_REGISTRY_MIXIN, MEKANISM_REGISTER_RECIPES),
        Map.entry(GTCEU_RECIPE_REGISTRATION_MIXIN, GTCEU_REGISTER_RECIPES),
        Map.entry(THERMAL_EXPANSION_PLUGIN_MIXIN, THERMAL_REGISTER_RECIPES),
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
        Map.entry(IRON_FURNACES_PLUGIN_MIXIN, IRON_FURNACES_REGISTER_RECIPES),
        Map.entry(IRON_FURNACES_CATEGORY_MIXIN, IRON_FURNACES_SET_RECIPE),
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
        Map.entry(TINKERS_PLUGIN_MIXIN, TINKERS_REGISTER_RECIPES),
        Map.entry(STARTER_PUBLISH_MODERN_MIXIN, Requirement.field(
            "async JEI runtime publication",
            "running",
            "Z")),
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
                + "Lnet/minecraft/client/gui/GuiGraphics;II)V")),
        Map.entry(MIXIN_PACKAGE + "JeiGuiBackgroundRenderGuardLegacyMixin", Requirement.method(
            "startup JEI background render guard",
            "onDrawBackgroundPost",
            "(Lnet/minecraft/client/gui/screens/Screen;"
                + "Lnet/minecraft/client/gui/GuiGraphics;)V")),
        Map.entry(MIXIN_PACKAGE + "JeiGuiBackgroundRenderGuardModernMixin", Requirement.method(
            "startup JEI background render guard",
            "onDrawBackgroundPost",
            "(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    );
    private static final Map<String, ConfigGate> CONFIG_GATES = Map.ofEntries(
        Map.entry(BREWING_INDEX_FORGE_MIXIN, new ConfigGate("indexedBrewingLookup", true)),
        Map.entry(BREWING_INDEX_NEO_MIXIN, new ConfigGate("indexedBrewingLookup", true)),
        Map.entry(FORGE_ANVIL_BATCH_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(FORGE_GRINDSTONE_BATCH_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(MENU_COMBINER_GUARD_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(MENU_GRINDSTONE_GUARD_MIXIN, new ConfigGate("skipRedundantMenuUpdates", true)),
        Map.entry(LEGACY_RECIPE_LAYOUT_MIXIN, new ConfigGate("lazyRecipeLayouts", true)),
        Map.entry(GTCEU_RECIPE_REGISTRATION_MIXIN, new ConfigGate("batchGtceuRecipeRegistration", true)),
        Map.entry(MINECOLONIES_JEI_PLUGIN_MIXIN, new ConfigGate("cacheMineColoniesToolScan", true)),
        Map.entry(MINECOLONIES_EQUIPMENT_TYPE_MIXIN, new ConfigGate("cacheMineColoniesToolScan", true)),
        Map.entry(MINECOLONIES_ATTRIBUTE_MODIFIERS_MIXIN, new ConfigGate("fixMineColoniesAttributeModifiers", true)),
        Map.entry(MINECOLONIES_TWEAKS_EXTENSION_MIXIN, new ConfigGate("cacheMineColoniesToolScan", true)),
        Map.entry(MINECOLONIES_TWEAKS_TAGS_MIXIN, new ConfigGate("cacheMineColoniesToolScan", true)),
        Map.entry(IRONS_SPELLS_MAKER_MIXIN, new ConfigGate("compactIronsSpellsImbuing", true)),
        Map.entry(IRONS_SPELLS_RECIPE_MIXIN, new ConfigGate("compactIronsSpellsImbuing", true)),
        Map.entry(MIXIN_PACKAGE + "VanillaRecipesMixin", new ConfigGate("parallelVanillaRecipes", false))
    );
    private static final Map<String, Boolean> EARLY_CONFIG_VALUES = new HashMap<>();

    private static final AtomicFeature ANVIL_REPRESENTATIVE_FEATURE = new AtomicFeature(
        "anvil representative recipes",
        List.of(
            new TargetRequirement(
                ANVIL_RECIPE_MAKER_CLASS,
                List.of(ANVIL_REPRESENTATIVE_ENTRY_POINT)
            ),
            new TargetRequirement(
                ANVIL_ENCHANTMENT_DATA_CLASS,
                List.of(ANVIL_REPRESENTATIVE_CAN_ENCHANT)
            )
        )
    );
    private static final AtomicFeature STORAGE_IN_MOTION_SUBTYPE_FEATURE = new AtomicFeature(
        "Storage in Motion duplicate subtype registration guard",
        List.of(
            new TargetRequirement(
                SOPHISTICATED_STORAGE_JEI_PLUGIN_CLASS,
                List.of(SOPHISTICATED_REGISTER_ITEM_SUBTYPES)
            ),
            new TargetRequirement(
                STORAGE_IN_MOTION_JEI_PLUGIN_CLASS,
                List.of(SOPHISTICATED_REGISTER_ITEM_SUBTYPES)
            )
        ),
        List.of(
            new InvocationTargetRequirement(
                SOPHISTICATED_STORAGE_JEI_PLUGIN_CLASS,
                SOPHISTICATED_SUBTYPE_TABLE_INVOCATION,
                1
            ),
            new InvocationTargetRequirement(
                STORAGE_IN_MOTION_JEI_PLUGIN_CLASS,
                SOPHISTICATED_SUBTYPE_TABLE_INVOCATION,
                1
            )
        )
    );
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
    private static final AtomicFeature IRONS_SPELLS_FEATURE = new AtomicFeature(
        "Iron's Spells Arcane Anvil compaction",
        List.of(
            new TargetRequirement("io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeMaker", List.of(
                Requirement.method("", "getRecipes", "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                    + "Lio/redspace/ironsspellbooks/jei/JeiPlugin$ItemFinder;)Ljava/util/List;")
            )),
            new TargetRequirement(IRONS_SPELLS_RECIPE_CLASS, List.of(
                Requirement.method("", "getRecipeItems", "()Lio/redspace/ironsspellbooks/jei/ArcaneAnvilJeiRecipe$Tuple;"),
                Requirement.field("", "leftItem", "Lnet/minecraft/world/item/Item;"),
                Requirement.field("", "rightItem", "Lnet/minecraft/world/item/Item;")
            )),
            new TargetRequirement(IRONS_SPELLS_RECIPE_CLASS + "$Tuple", List.of(
                Requirement.method("", "<init>", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")
            )),
            new TargetRequirement("io.redspace.ironsspellbooks.api.registry.SpellRegistry", List.of(
                Requirement.method("", "getEnabledSpells", "()Ljava/util/List;")
            )),
            new TargetRequirement("io.redspace.ironsspellbooks.api.spells.AbstractSpell", List.of(
                Requirement.method("", "getSpellId", "()Ljava/lang/String;"),
                Requirement.method("", "getMinLevel", "()I"),
                Requirement.method("", "getMaxLevel", "()I")
            )),
            new TargetRequirement("io.redspace.ironsspellbooks.api.spells.ISpellContainer", List.of(
                Requirement.method("", "createScrollContainer", "(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;"
                    + "ILnet/minecraft/world/item/ItemStack;)Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;")
            )),
            new TargetRequirement("io.redspace.ironsspellbooks.registries.ItemRegistry", List.of(
                Requirement.field("", "SCROLL", null)
            ))
        )
    );
    private static final AtomicFeature IRON_FURNACES_FEATURE = new AtomicFeature(
        "Iron Furnaces generator compaction",
        List.of(
            new TargetRequirement(
                "ironfurnaces.jei.IronFurnacesJEIPlugin",
                List.of(IRON_FURNACES_REGISTER_RECIPES)
            ),
            new TargetRequirement(
                "ironfurnaces.jei.RecipeCategoryGeneratorRegular",
                List.of(IRON_FURNACES_SET_RECIPE)
            ),
            new TargetRequirement(
                "ironfurnaces.jei.RecipeCategoryGeneratorSmoking",
                List.of(IRON_FURNACES_SET_RECIPE)
            ),
            new TargetRequirement(
                "ironfurnaces.recipes.SimpleGeneratorRecipe",
                List.of(
                    Requirement.method("", "<init>", "(ILnet/minecraft/world/item/ItemStack;)V"),
                    Requirement.method("", "getEnergy", "()I"),
                    Requirement.method(
                        "", "getIngredient", "()Lnet/minecraft/world/item/ItemStack;")
                )
            )
        ),
        List.of(new InvocationTargetRequirement(
            "ironfurnaces.jei.IronFurnacesJEIPlugin",
            IRON_FURNACES_ADD_RECIPES_INVOCATION
        ))
    );
    private static final AtomicFeature GENERATOR_GALORE_FEATURE = new AtomicFeature(
        "Generator Galore solid-fuel compaction",
        List.of(
            new TargetRequirement(
                "cy.jdkdigital.generatorgalore.integrations.JeiPlugin",
                List.of(GENERATOR_GALORE_REGISTER_LAMBDA)
            ),
            new TargetRequirement(
                "cy.jdkdigital.generatorgalore.common.recipe.SolidFuelRecipe",
                List.of(
                    Requirement.method(
                        "",
                        "<init>",
                        "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;"
                            + "Lnet/minecraft/world/item/crafting/Ingredient;FI)V"
                    ),
                    Requirement.method("", "id", "()Lnet/minecraft/resources/ResourceLocation;"),
                    Requirement.method("", "fuels", "()Ljava/util/List;"),
                    Requirement.method(
                        "", "generator", "()Lnet/minecraft/world/item/crafting/Ingredient;"),
                    Requirement.method("", "rate", "()F"),
                    Requirement.method("", "burnTime", "()I")
                )
            )
        ),
        List.of(new InvocationTargetRequirement(
            "cy.jdkdigital.generatorgalore.integrations.JeiPlugin",
            GENERATOR_GALORE_ADD_RECIPES_INVOCATION
        ))
    );
    private static final AtomicFeature MEKANISM_NUTRITIONAL_FEATURE = new AtomicFeature(
        "Mekanism Nutritional Liquifier compaction",
        List.of(
            new TargetRequirement(
                "mekanism.client.jei.RecipeRegistryHelper",
                List.of(MEKANISM_REGISTER_RECIPES)
            ),
            new TargetRequirement(
                "mekanism.common.recipe.impl.NutritionalLiquifierIRecipe",
                List.of(Requirement.method(
                    "",
                    "<init>",
                    "(Lnet/minecraft/world/item/Item;Lmekanism/api/recipes/ingredients/ItemStackIngredient;"
                        + "Lnet/minecraftforge/fluids/FluidStack;)V"
                ))
            ),
            new TargetRequirement(
                "mekanism.api.recipes.ItemStackToFluidRecipe",
                List.of(
                    Requirement.method(
                        "", "getInput", "()Lmekanism/api/recipes/ingredients/ItemStackIngredient;"),
                    Requirement.method("", "getOutputDefinition", "()Ljava/util/List;")
                )
            ),
            new TargetRequirement(
                "mekanism.api.recipes.ingredients.InputIngredient",
                List.of(Requirement.method("", "getRepresentations", "()Ljava/util/List;"))
            ),
            new TargetRequirement(
                "mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess",
                List.of(Requirement.method(
                    "",
                    "item",
                    "()Lmekanism/api/recipes/ingredients/creator/IItemStackIngredientCreator;"
                ))
            ),
            new TargetRequirement(
                "mekanism.api.recipes.ingredients.creator.IItemStackIngredientCreator",
                List.of(Requirement.method(
                    "",
                    "from",
                    "(Lnet/minecraft/world/item/crafting/Ingredient;I)"
                        + "Lmekanism/api/recipes/ingredients/ItemStackIngredient;"
                ))
            ),
            new TargetRequirement(
                "net.minecraftforge.fluids.FluidStack",
                List.of(
                    Requirement.method("", "getFluid", "()Lnet/minecraft/world/level/material/Fluid;"),
                    Requirement.method("", "getAmount", "()I")
                )
            )
        ),
        List.of(new InvocationTargetRequirement(
            "mekanism.client.jei.RecipeRegistryHelper",
            MEKANISM_ADD_RECIPES_INVOCATION
        ))
    );
    private static final AtomicFeature GTCEU_RECIPE_REGISTRATION_FEATURE = new AtomicFeature(
        "GTCEu recipe registration batching",
        List.of(new TargetRequirement(
            GTCEU_RECIPE_CATEGORY_CLASS,
            List.of(GTCEU_REGISTER_RECIPES)
        )),
        List.of(
            new InvocationTargetRequirement(
                GTCEU_RECIPE_CATEGORY_CLASS,
                GTCEU_COPY_RECIPES_INVOCATION,
                1
            ),
            new InvocationTargetRequirement(
                GTCEU_RECIPE_CATEGORY_CLASS,
                GTCEU_ADD_RECIPES_INVOCATION,
                1
            )
        )
    );
    private static final AtomicFeature MINECOLONIES_TOOL_SCAN_FEATURE = new AtomicFeature(
        "MineColonies JEI tool scan caching",
        List.of(
            new TargetRequirement(
                MINECOLONIES_JEI_PLUGIN_CLASS,
                List.of(MINECOLONIES_REGISTER_RECIPES)
            ),
            new TargetRequirement(
                MINECOLONIES_TOOLS_ANALYZER_CLASS,
                List.of(MINECOLONIES_FIND_TOOLS, MINECOLONIES_TRY_ADDING_TOOL)
            ),
            new TargetRequirement(
                MINECOLONIES_EQUIPMENT_TYPE_CLASS,
                List.of(MINECOLONIES_CHECK_IS_EQUIPMENT, MINECOLONIES_GET_MINING_LEVEL)
            )
        ),
        List.of(
            new InvocationTargetRequirement(
                MINECOLONIES_JEI_PLUGIN_CLASS,
                MINECOLONIES_FIND_TOOLS_INVOCATION,
                1
            ),
            new InvocationTargetRequirement(
                MINECOLONIES_TOOLS_ANALYZER_CLASS,
                MINECOLONIES_CHECK_IS_EQUIPMENT_INVOCATION,
                1
            ),
            new InvocationTargetRequirement(
                MINECOLONIES_TOOLS_ANALYZER_CLASS,
                MINECOLONIES_GET_MINING_LEVEL_INVOCATION,
                1
            )
        )
    );
    private static final AtomicFeature MINECOLONIES_TWEAKS_FEATURE = new AtomicFeature(
        "MineColonies Tweaks empty tool rule bypass",
        java.util.stream.Stream.concat(
            MINECOLONIES_TOOL_SCAN_FEATURE.targets().stream(),
            java.util.stream.Stream.of(
                new TargetRequirement(MINECOLONIES_TWEAKS_EXTENSION_CLASS, MINECOLONIES_TWEAKS_EXTENSION_REQUIREMENTS),
                new TargetRequirement(MINECOLONIES_TWEAKS_TAGS_CLASS, List.of(MINECOLONIES_TWEAKS_BLACKLIST))
            )
        ).toList(),
        MINECOLONIES_TOOL_SCAN_FEATURE.invocations()
    );
    private static final AtomicFeature TINKERS_CASTING_FEATURE = new AtomicFeature(
        "Tinkers casting compaction",
        List.of(
            new TargetRequirement(
                "slimeknights.tconstruct.plugin.jei.JEIPlugin",
                List.of(TINKERS_REGISTER_RECIPES)
            ),
            new TargetRequirement(
                "slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe",
                List.of(
                    Requirement.method(
                        "",
                        "<init>",
                        "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Ljava/util/List;Ljava/util/List;Ljava/util/List;IZ)V"
                    ),
                    Requirement.method("", "getRecipeId", "()Lnet/minecraft/resources/ResourceLocation;"),
                    Requirement.method("", "getType", "()Lnet/minecraft/world/item/crafting/RecipeType;"),
                    Requirement.method("", "getCastItems", "()Ljava/util/List;"),
                    Requirement.method("", "getFluids", "()Ljava/util/List;"),
                    Requirement.method("", "getOutputs", "()Ljava/util/List;"),
                    Requirement.method("", "getCoolingTime", "()I"),
                    Requirement.method("", "isConsumed", "()Z")
                )
            ),
            new TargetRequirement(
                "mezz.jei.api.forge.ForgeTypes",
                List.of(Requirement.field(
                    "", "FLUID_STACK", "Lmezz/jei/api/ingredients/IIngredientTypeWithSubtypes;"))
            ),
            new TargetRequirement(
                "net.minecraftforge.fluids.FluidStack",
                List.of(
                    Requirement.method("", "getAmount", "()I"),
                    Requirement.method("", "getTag", "()Lnet/minecraft/nbt/CompoundTag;")
                )
            )
        ),
        List.of(new InvocationTargetRequirement(
            "slimeknights.tconstruct.plugin.jei.JEIPlugin",
            TINKERS_ADD_RECIPES_INVOCATION
        ))
    );
    private static final AtomicFeature THERMAL_STIRLING_FEATURE = new AtomicFeature(
        "Thermal Stirling Dynamo fuel compaction",
        List.of(
            new TargetRequirement(
                "cofh.thermal.expansion.compat.jei.TExpJeiPlugin",
                List.of(THERMAL_REGISTER_RECIPES)
            ),
            new TargetRequirement(
                "cofh.thermal.core.util.recipes.dynamo.StirlingFuel",
                List.of(THERMAL_STIRLING_CONSTRUCTOR)
            ),
            new TargetRequirement(
                "cofh.thermal.lib.util.recipes.ThermalFuel",
                THERMAL_FUEL_ACCESSORS
            )
        ),
        List.of(new InvocationTargetRequirement(
            "cofh.thermal.expansion.compat.jei.TExpJeiPlugin",
            THERMAL_ADD_RECIPES_INVOCATION
        ))
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
        List.of(new TargetRequirement(
            "mezz.jei.library.util.BrewingRecipeMakerCommon",
            List.of(Requirement.method(
                "",
                "getNewPotions",
                "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                    + "Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/util/Collection;"
                    + "Ljava/util/Collection;Ljava/util/Collection;)Ljava/util/List;"
            ))
        ))
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
    private static final AtomicFeature MODERN_INGREDIENT_FILTER_FEATURE = new AtomicFeature(
        "async ingredient filter",
        List.of(
            new TargetRequirement(
                "mezz.jei.gui.ingredients.IngredientFilter",
                List.of(Requirement.method(
                    "",
                    "createElementSearch",
                    "(Lmezz/jei/common/config/IClientConfig;"
                        + "Lmezz/jei/gui/search/ElementPrefixParser;"
                        + "Ljava/util/List;"
                        + "Lmezz/jei/api/runtime/IIngredientManager;)"
                        + "Lmezz/jei/gui/search/IElementSearch;"
                ))
            )
        )
    );
    private static final Map<String, AtomicFeature> ATOMIC_FEATURES = Map.ofEntries(
        Map.entry(ANVIL_REPRESENTATIVE_CONTEXT_MIXIN, ANVIL_REPRESENTATIVE_FEATURE),
        Map.entry(ANVIL_ENCHANTMENT_REPRESENTATIVE_MIXIN, ANVIL_REPRESENTATIVE_FEATURE),
        Map.entry(CELESTIAL_REINFORCE_MIXIN, CELESTIAL_REINFORCE_FEATURE),
        Map.entry(ULTIMATE_CAR_BUILDER_MIXIN, ULTIMATE_CAR_FEATURE),
        Map.entry(ULTIMATE_CAR_CATEGORY_MIXIN, ULTIMATE_CAR_FEATURE),
        Map.entry(GENERATOR_GALORE_PLUGIN_MIXIN, GENERATOR_GALORE_FEATURE),
        Map.entry(IRON_FURNACES_PLUGIN_MIXIN, IRON_FURNACES_FEATURE),
        Map.entry(IRON_FURNACES_CATEGORY_MIXIN, IRON_FURNACES_FEATURE),
        Map.entry(IRONS_SPELLS_MAKER_MIXIN, IRONS_SPELLS_FEATURE),
        Map.entry(IRONS_SPELLS_RECIPE_MIXIN, IRONS_SPELLS_FEATURE),
        Map.entry(MEKANISM_RECIPE_REGISTRY_MIXIN, MEKANISM_NUTRITIONAL_FEATURE),
        Map.entry(GTCEU_RECIPE_REGISTRATION_MIXIN, GTCEU_RECIPE_REGISTRATION_FEATURE),
        Map.entry(MINECOLONIES_JEI_PLUGIN_MIXIN, MINECOLONIES_TOOL_SCAN_FEATURE),
        Map.entry(MINECOLONIES_EQUIPMENT_TYPE_MIXIN, MINECOLONIES_TOOL_SCAN_FEATURE),
        Map.entry(MINECOLONIES_TWEAKS_EXTENSION_MIXIN, MINECOLONIES_TWEAKS_FEATURE),
        Map.entry(MINECOLONIES_TWEAKS_TAGS_MIXIN, MINECOLONIES_TWEAKS_FEATURE),
        Map.entry(THERMAL_EXPANSION_PLUGIN_MIXIN, THERMAL_STIRLING_FEATURE),
        Map.entry(TINKERS_PLUGIN_MIXIN, TINKERS_CASTING_FEATURE),
        Map.entry(EMBERS_PLUGIN_MIXIN, EMBERS_DAWNSTONE_ANVIL_FEATURE),
        Map.entry(EMBERS_CATEGORY_MIXIN, EMBERS_DAWNSTONE_ANVIL_FEATURE),
        Map.entry(SFM_FALLING_ANVIL_MIXIN, SFM_FALLING_ANVIL_FEATURE),
        Map.entry(STORAGE_IN_MOTION_SUBTYPE_MIXIN, STORAGE_IN_MOTION_SUBTYPE_FEATURE),
        Map.entry(JEED_EFFECT_CLICK_MIXIN, JEED_EFFECT_CLICK_FEATURE),
        Map.entry(BREWING_INDEX_FORGE_MIXIN, BREWING_INDEX_FORGE_FEATURE),
        Map.entry(BREWING_INDEX_NEO_MIXIN, BREWING_INDEX_NEO_FEATURE),
        Map.entry(FORGE_ANVIL_BATCH_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(FORGE_GRINDSTONE_BATCH_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(MENU_COMBINER_GUARD_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(MENU_GRINDSTONE_GUARD_MIXIN, FORGE_MENU_BATCH_FEATURE),
        Map.entry(MIXIN_PACKAGE + "IngredientFilterModernMixin", MODERN_INGREDIENT_FILTER_FEATURE)
    );

    private final Map<String, ClassNode> targetCache = new HashMap<>();
    private final Map<AtomicFeature, Boolean> atomicFeatureCompatibility = new HashMap<>();
    private Boolean sophisticatedShulkerCompatibility;

    /** One of these covers each JEI generation, so the one that does not match is not a problem. */
    private static final Set<String> VARIANTS = Set.of(
        MIXIN_PACKAGE + "AnvilRecipeControlMixin",
        MIXIN_PACKAGE + "AnvilRecipeControlModernMixin",
        MIXIN_PACKAGE + "IngredientFilterMixin",
        MIXIN_PACKAGE + "IngredientFilterModernMixin",
        BREWING_INDEX_FORGE_MIXIN,
        BREWING_INDEX_NEO_MIXIN,
        LEGACY_RECIPE_LAYOUT_MIXIN,
        FORGE_ANVIL_BATCH_MIXIN,
        FORGE_GRINDSTONE_BATCH_MIXIN,
        MENU_COMBINER_GUARD_MIXIN,
        MENU_GRINDSTONE_GUARD_MIXIN,
        STARTER_PUBLISH_LEGACY_MIXIN,
        STARTER_PUBLISH_MODERN_MIXIN,
        MIXIN_PACKAGE + "JeiGuiBackgroundRenderGuardLegacyMixin",
        MIXIN_PACKAGE + "JeiGuiBackgroundRenderGuardModernMixin"
    );

    private static final Set<String> OPTIONAL_MIXINS = Set.of(
        MIXIN_PACKAGE + "compat.CelestialForgeReinforceRecipeMixin",
        EMBERS_CATEGORY_MIXIN,
        EMBERS_PLUGIN_MIXIN,
        MIXIN_PACKAGE + "compat.GeneratorGaloreJeiPluginMixin",
        MIXIN_PACKAGE + "compat.IronFurnacesGeneratorCategoryMixin",
        MIXIN_PACKAGE + "compat.IronFurnacesJeiPluginMixin",
        MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilMakerMixin",
        MIXIN_PACKAGE + "compat.IronsSpellsArcaneAnvilRecipeMixin",
        JEED_EFFECT_CLICK_MIXIN,
        GTCEU_RECIPE_REGISTRATION_MIXIN,
        MINECOLONIES_JEI_PLUGIN_MIXIN,
        MINECOLONIES_EQUIPMENT_TYPE_MIXIN,
        MINECOLONIES_TWEAKS_EXTENSION_MIXIN,
        MINECOLONIES_TWEAKS_TAGS_MIXIN,
        MIXIN_PACKAGE + "compat.MekanismRecipeRegistryHelperMixin",
        MIXIN_PACKAGE + "compat.ProductiveTreesLogStrippingCategoryMixin",
        MIXIN_PACKAGE + "compat.SfmFallingAnvilCategoryMixin",
        STORAGE_IN_MOTION_SUBTYPE_MIXIN,
        THERMAL_EXPANSION_PLUGIN_MIXIN,
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
        if ((MIXIN_PACKAGE + "ClientTickHookMixin").equals(mixinClassName) && Boolean.getBoolean("jet.benchmark")) {
            return true;
        }
        if ((MIXIN_PACKAGE + "TooltipBenchmarkOverlayMixin").equals(mixinClassName)) {
            ClassNode overlay = readTarget(targetClassName);
            return Boolean.getBoolean("jet.benchmark") && overlay != null
                && Requirement.method("", "drawScreen", "(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IIF)V").isPresentIn(overlay);
        }
        if (!readEarlyBoolean("enabled", true)) {
            return false;
        }
        if (mixinClassName.equals(MIXIN_PACKAGE + "accessor.JeiRuntimeAccessor")) {
            return hasRuntimeAccessContract(readTarget(targetClassName));
        }
        if ((mixinClassName.equals(MIXIN_PACKAGE + "JeiGuiRenderGuardMixin")
            || mixinClassName.equals(MIXIN_PACKAGE + "JeiGuiBackgroundRenderGuardLegacyMixin")
            || mixinClassName.equals(MIXIN_PACKAGE + "JeiGuiBackgroundRenderGuardModernMixin"))
            && !hasRuntimeAccessContract(readTarget("mezz.jei.common.Internal"))) {
            LOGGER.warn("JEI startup render guard disabled: nullable runtime field ABI is unavailable");
            return false;
        }
        if (MINECOLONIES_ATTRIBUTE_MODIFIERS_MIXIN.equals(mixinClassName)) {
            return readEarlyBoolean("fixMineColoniesAttributeModifiers", true)
                && hasPluginCallbackRoutingContract(readTarget("mezz.jei.library.load.PluginCaller"))
                && hasMineColoniesAttributeContract(readTarget(targetClassName));
        }
        if ((MIXIN_PACKAGE + "JeiStartupGridRefreshMixin").equals(mixinClassName)) {
            return hasStartupGridRefreshContract(readTarget(targetClassName));
        }
        if ((MIXIN_PACKAGE + "JeiNativeSearchBuilderMixin").equals(mixinClassName)) {
            return hasNativeSearchBuilderContract(readTarget(targetClassName));
        }
        if ((MIXIN_PACKAGE + "ListElementInfoTooltipCaptureMixin").equals(mixinClassName)) {
            return shouldApplyTooltipCapture(targetClassName);
        }
        if ((MIXIN_PACKAGE + "TooltipResourceReloadMixin").equals(mixinClassName)) {
            ClassNode reload = readTarget(targetClassName);
            return readEarlyBoolean("tooltipSearchIndex", false) && hasTooltipReloadContract(reload);
        }
        if ((MIXIN_PACKAGE + "accessor.ElementSearchTooltipAccessor").equals(mixinClassName)) {
            ClassNode search = readTarget(targetClassName);
            return readEarlyBoolean("tooltipSearchIndex", false) && search != null
                && Requirement.field("", "prefixedSearchables", "Ljava/util/Map;").isPresentIn(search);
        }
        if ((MIXIN_PACKAGE + "IngredientFilterMixin").equals(mixinClassName)
            || (MIXIN_PACKAGE + "IngredientFilterModernMixin").equals(mixinClassName)) {
            ClassNode filter = readTarget(targetClassName);
            if (filter == null || !Requirement.method("", "notifyListenersOfChange", "()V").isPresentIn(filter)
                || !Requirement.method("", "rebuildItemFilter", "()V").isPresentIn(filter)) {
                return false;
            }
        }
        if ((MIXIN_PACKAGE + "IngredientFilterModernMixin").equals(mixinClassName)
            && !hasTooltipLowMemoryContract(readTarget("mezz.jei.common.config.IClientConfig"))) {
            LOGGER.info("JEI tooltip index bypass: unrecognized low-memory setting ABI");
            return false;
        }
        if (CLIENT_TASK_PUMP_GUARD_MIXIN.equals(mixinClassName)) {
            return shouldApplyClientTaskPumpGuard(targetClassName);
        } else if (STARTER_MIXIN.equals(mixinClassName)) {
            updateAsyncStartupCompatibility();
        } else if (PLUGIN_CALLER_MIXIN.equals(mixinClassName)) {
            boolean compatible = hasPluginCallbackRoutingContract(readTarget(targetClassName));
            JeiOptCompatibilityState.setAsyncStartupSupported(compatible);
            if (!compatible) {
                LOGGER.warn(
                    "JEI Optimize disabled asynchronous startup because PluginCaller no longer has the verified callback ABI."
                );
            }
            return compatible;
        }
        if (isGrindstoneRepresentativeMixin(mixinClassName)) {
            return shouldApplyGrindstoneRepresentativeMixin(targetClassName, mixinClassName);
        }
        if (RECIPE_MANAGER_SAFE_DIAGNOSTIC_MIXIN.equals(mixinClassName)) {
            ClassNode target = readTarget(targetClassName);
            return target != null && hasUnsafeRecipeDiagnostics(target);
        }
        if (EXTENDABLE_RECIPE_HELPER_MIXIN.equals(mixinClassName)
            || SOPHISTICATED_SHULKER_ACCESSOR_MIXIN.equals(mixinClassName)) {
            return isSophisticatedShulkerCompatible();
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

    private boolean shouldApplyTooltipCapture(String targetClassName) {
        JeiOptCompatibilityState.setTooltipContract((char) 0, false);
        if (!readEarlyBoolean("tooltipSearchMetrics", false) && !readEarlyBoolean("tooltipSearchIndex", false)) {
            return false;
        }
        ClassNode target = readTarget(targetClassName);
        ClassNode search = readTarget("mezz.jei.gui.search.ElementSearch");
        ClassNode parser = readTarget("mezz.jei.gui.search.ElementPrefixParser");
        if (target == null || search == null || parser == null) {
            return false;
        }
        if (search.methods.stream().anyMatch(method -> method.name.equals("processDeferredTooltips"))
            || search.fields.stream().anyMatch(field -> field.desc.contains("SearchStringCache"))) {
            LOGGER.info("JEI tooltip shadow disabled: JEI-Async owns deferred tooltips");
            return false;
        }
        char prefix = detectTooltipPrefix(parser);
        boolean compatible = prefix != 0
            && hasTooltipSettingsContract(readTarget("mezz.jei.common.config.IIngredientFilterConfig"))
            && Requirement.method("", "getTooltipStrings", "(Lmezz/jei/common/config/IIngredientFilterConfig;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/Set;").isPresentIn(target)
            && Requirement.method("", "getSearchResults", "(Lmezz/jei/gui/search/ElementPrefixParser$TokenInfo;)"
                + "Ljava/util/Set;").isPresentIn(search)
            && Requirement.method("", "parseToken", "(Ljava/lang/String;)Ljava/util/Optional;").isPresentIn(parser);
        if (readEarlyBoolean("tooltipSearchIndex", false)) {
            ClassNode reload = readTarget("mezz.jei.gui.startup.ResourceReloadHandler");
            compatible &= hasTooltipReloadContract(reload)
                && Requirement.field("", "prefixedSearchables", "Ljava/util/Map;").isPresentIn(search)
                && Requirement.method("", "add", "(Lmezz/jei/gui/ingredients/IListElementInfo;Lmezz/jei/api/runtime/IIngredientManager;)V").isPresentIn(search)
                && Requirement.method("", "getAllIngredients", "()Ljava/util/Collection;").isPresentIn(search);
        }
        if (!compatible) {
            LOGGER.warn("JEI tooltip shadow disabled: unrecognized getter/parser/query ABI");
            return false;
        }
        boolean trimStrings = search.methods.stream().anyMatch(method -> method.name.equals("putIfNotBlank"));
        JeiOptCompatibilityState.setTooltipContract(prefix, trimStrings);
        LOGGER.info("JEI tooltip shadow ABI selected: prefix={}, trimStrings={}", prefix, trimStrings);
        return true;
    }

    static boolean hasRuntimeAccessContract(ClassNode internal) {
        return internal != null && internal.fields.stream().anyMatch(field ->
            field.name.equals("jeiRuntime") && field.desc.equals("Lmezz/jei/api/runtime/IJeiRuntime;")
                && (field.access & Opcodes.ACC_STATIC) != 0);
    }

    static boolean hasTooltipReloadContract(ClassNode reload) {
        String descriptor = "(Lnet/minecraft/server/packs/resources/ResourceManager;)V";
        return reload != null && (Requirement.method("", "onResourceManagerReload", descriptor).isPresentIn(reload)
            || Requirement.method("", "m_6213_", descriptor).isPresentIn(reload));
    }

    static boolean hasStartupGridRefreshContract(ClassNode grid) {
        if (grid == null) {
            return false;
        }
        String owner = "mezz/jei/gui/overlay/ingredients/IngredientGridWithNavigation";
        for (MethodNode method : grid.methods) {
            if (!method.name.equals("lambda$new$0") || !method.desc.equals("()V")) {
                continue;
            }
            boolean readsAnchor = false;
            boolean updatesLayout = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation && invocation.owner.equals(owner)) {
                    readsAnchor |= invocation.name.equals("getPageAnchorElement")
                        && invocation.desc.equals("()Lmezz/jei/gui/overlay/elements/IElement;");
                    updatesLayout |= invocation.name.equals("updateLayoutKeepingPageAnchorVisible")
                        && invocation.desc.equals("(Lmezz/jei/gui/overlay/elements/IElement;)V");
                }
            }
            return readsAnchor && updatesLayout;
        }
        return false;
    }

    static boolean hasNativeSearchBuilderContract(ClassNode search) {
        if (search == null) { return false; }
        for (MethodNode method : search.methods) {
            if (!method.name.equals("<init>")) { continue; }
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation
                    && invocation.owner.equals("mezz/jei/api/search/ISearchStorageBuilder")
                    && invocation.name.equals("build") && invocation.desc.equals("()Lmezz/jei/api/search/ISearchStorage;")) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasMineColoniesAttributeContract(ClassNode target) {
        if (target == null) { return false; }
        String descriptor = "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ai/attributes/Attribute;)D";
        for (MethodNode method : target.methods) {
            if (!method.name.equals("getItemStackAttributeValue") || !method.desc.equals(descriptor)
                || (method.access & Opcodes.ACC_STATIC) == 0) {
                continue;
            }
            int modifierCollections = 0;
            int iterations = 0;
            int instances = 0;
            boolean addsTransient = false;
            boolean readsValue = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invocation) {
                    if (invocation.owner.equals("net/minecraft/world/entity/ai/attributes/AttributeInstance")
                        && invocation.name.equals("<init>")
                        && invocation.desc.equals("(Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/util/function/Consumer;)V")) {
                        instances++;
                    }
                    if (invocation.owner.equals("com/google/common/collect/Multimap") && invocation.name.equals("get")
                        && invocation.desc.equals("(Ljava/lang/Object;)Ljava/util/Collection;")) {
                        modifierCollections++;
                    }
                    if (invocation.owner.equals("java/util/Collection") && invocation.name.equals("forEach")
                        && invocation.desc.equals("(Ljava/util/function/Consumer;)V")) {
                        iterations++;
                    }
                    readsValue |= invocation.owner.equals("net/minecraft/world/entity/ai/attributes/AttributeInstance")
                        && (invocation.name.equals("getValue") || invocation.name.equals("m_22135_"))
                        && invocation.desc.equals("()D");
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle) {
                            addsTransient |= handle.getOwner().equals("net/minecraft/world/entity/ai/attributes/AttributeInstance")
                                && (handle.getName().equals("addTransientModifier") || handle.getName().equals("m_22118_"))
                                && handle.getDesc().equals("(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V");
                        }
                    }
                }
            }
            return instances == 1 && modifierCollections == 1 && iterations == 1 && addsTransient && readsValue;
        }
        return false;
    }

    static boolean hasTooltipSettingsContract(ClassNode config) {
        return config != null && (Requirement.method("", "getSearchAdvancedTooltips", "()Z").isPresentIn(config)
            || Requirement.method("", "searchAdvancedTooltips", "()Lnet/mezzdev/config/api/value/IConfigValue;").isPresentIn(config));
    }

    static boolean hasTooltipLowMemoryContract(ClassNode config) {
        return config != null && (Requirement.method("", "isLowMemorySlowSearchEnabled", "()Z").isPresentIn(config)
            || Requirement.method("", "lowMemorySlowSearchEnabled", "()Lnet/mezzdev/config/api/value/IConfigValue;").isPresentIn(config));
    }

    static char detectTooltipPrefix(ClassNode parser) {
        char found = 0;
        for (MethodNode method : parser.methods) {
            if (!method.name.equals("<init>")) {
                continue;
            }
            int candidate = -1;
            boolean tooltipConfigValue = false;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof IntInsnNode integer && integer.getOpcode() == Opcodes.BIPUSH) {
                    candidate = integer.operand;
                    tooltipConfigValue = false;
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle
                            && ((handle.getOwner().equals("mezz/jei/common/config/IIngredientFilterConfig")
                                && handle.getName().equals("getTooltipSearchMode"))
                                || (tooltipConfigValue && dynamic.name.equals("getMode")
                                    && dynamic.desc.equals("(Lnet/mezzdev/config/api/value/IConfigValue;)Lmezz/jei/common/search/PrefixInfo$IModeGetter;")
                                    && handle.getOwner().equals("net/mezzdev/config/api/value/IConfigValue")
                                    && handle.getName().equals("get") && handle.getDesc().equals("()Ljava/lang/Object;")))) {
                            if (found != 0 || candidate < 33 || candidate > 126) {
                                return 0;
                            }
                            found = (char) candidate;
                        }
                    }
                    tooltipConfigValue = false;
                } else if (instruction instanceof MethodInsnNode invocation) {
                    if (invocation.owner.equals("mezz/jei/common/config/IIngredientFilterConfig")) {
                        tooltipConfigValue = invocation.name.equals("tooltipSearchMode")
                            && invocation.desc.equals("()Lnet/mezzdev/config/api/value/IConfigValue;");
                    }
                    if (invocation.name.equals("addPrefix")) {
                        candidate = -1;
                        tooltipConfigValue = false;
                    }
                }
            }
        }
        return found;
    }

    private boolean shouldApplyClientTaskPumpGuard(String targetClassName) {
        ClassNode target = readTarget(targetClassName);
        if (target != null) {
            for (Requirement requirement : CLIENT_TASK_PUMP_GUARD_REQUIREMENTS) {
                if (requirement.isPresentIn(target)) {
                    LOGGER.debug(
                        "JEI Optimize enabled its off-main client task-pump guard against {} {}.",
                        targetClassName,
                        requirement.describe()
                    );
                    return true;
                }
            }
        }
        LOGGER.warn(
            "JEI Optimize turned off its off-main client task-pump guard: {} declares neither verified "
                + "pollTask()Z nor m_7245_()Z. JEI keeps its normal behavior; the mod needs an update for this "
                + "Minecraft version.",
            targetClassName
        );
        return false;
    }

    private void updateAsyncStartupCompatibility() {
        ClassNode pluginCaller = readTarget("mezz.jei.library.load.PluginCaller");
        boolean compatible = hasPluginCallbackRoutingContract(pluginCaller);
        JeiOptCompatibilityState.setAsyncStartupSupported(compatible);
        if (!compatible) {
            LOGGER.warn(
                "JEI Optimize disabled asynchronous startup because plugin callbacks cannot be routed safely to the client thread."
            );
        }
    }

    static boolean hasPluginCallbackRoutingContract(ClassNode pluginCaller) {
        return pluginCaller != null && PLUGIN_CALLBACK_INVOCATION.countIn(pluginCaller) == 1;
    }

    private static boolean isGrindstoneRepresentativeMixin(String mixinClassName) {
        return GRINDSTONE_REPRESENTATIVE_MIXIN.equals(mixinClassName)
            || GRINDSTONE_DISENCHANT_LEGACY_MIXIN.equals(mixinClassName)
            || GRINDSTONE_DISENCHANT_MODERN_MIXIN.equals(mixinClassName);
    }

    private boolean shouldApplyGrindstoneRepresentativeMixin(
        String targetClassName,
        String mixinClassName
    ) {
        ClassNode target = readTarget(targetClassName);
        if (target == null) {
            LOGGER.debug(
                "JEI Optimize skipped optional {} because {} is not available.",
                mixinClassName,
                targetClassName
            );
            return false;
        }

        GrindstoneVariant variant = detectGrindstoneVariant(target);
        boolean compatible = GRINDSTONE_ENTRY_POINT.isPresentIn(target)
            && hasExpectedGrindstoneRepairMethod(target)
            && variant != GrindstoneVariant.NONE;
        if (!compatible) {
            if (GRINDSTONE_REPRESENTATIVE_MIXIN.equals(mixinClassName)) {
                LOGGER.warn(
                    "JEI Optimize turned off grindstone representative recipes: {} has no supported complete ABI. "
                        + "JEI keeps its original grindstone recipes.",
                    targetClassName
                );
            }
            return false;
        }
        if (GRINDSTONE_DISENCHANT_LEGACY_MIXIN.equals(mixinClassName)) {
            return variant == GrindstoneVariant.LEGACY;
        }
        if (GRINDSTONE_DISENCHANT_MODERN_MIXIN.equals(mixinClassName)) {
            return variant == GrindstoneVariant.MODERN;
        }
        return true;
    }

    static GrindstoneVariant detectGrindstoneVariant(ClassNode target) {
        boolean modern = GRINDSTONE_MODERN_CAN_ENCHANT.isPresentIn(target)
            && GRINDSTONE_MODERN_INVOCATION.isPresentIn(target);
        boolean legacy = GRINDSTONE_LEGACY_INVOCATIONS.stream()
            .anyMatch(requirement -> requirement.isPresentIn(target));
        if (modern == legacy) {
            return GrindstoneVariant.NONE;
        }
        if (modern) {
            return GrindstoneVariant.MODERN;
        }
        return GrindstoneVariant.LEGACY;
    }

    private static boolean hasExpectedGrindstoneRepairMethod(ClassNode target) {
        //? if forge {
        return Requirement.method(
            "",
            "getRepairRecipes",
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lmezz/jei/api/runtime/IIngredientManager;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;"
        ).isPresentIn(target);
        //?} else {
        /*return Requirement.method(
            "",
            "getRepairRecipes",
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/stream/Stream;"
        ).isPresentIn(target);
        *///?}
    }

    static boolean hasUnsafeRecipeDiagnostics(ClassNode target) {
        if (target == null) {
            return false;
        }
        for (MethodNode method : target.methods) {
            if (!RECIPE_DEBUG_INVOCATION.enclosingMethod().equals(method.name)
                || !RECIPE_DEBUG_INVOCATION.enclosingDescriptor().equals(method.desc)) {
                continue;
            }
            int invocationCount = 0;
            boolean markerAfterSecondInvocation = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (RECIPE_DEBUG_INVOCATION.matches(instruction)) {
                    invocationCount++;
                } else if (invocationCount == 2
                    && instruction instanceof LdcInsnNode constant
                    && UNHANDLED_RECIPE_DEBUG_MESSAGE.equals(constant.cst)) {
                    markerAfterSecondInvocation = true;
                }
            }
            return invocationCount == 3 && markerAfterSecondInvocation;
        }
        return false;
    }

    private boolean isSophisticatedShulkerCompatible() {
        if (sophisticatedShulkerCompatibility != null) {
            return sophisticatedShulkerCompatibility;
        }
        ClassNode wrapper = readTarget(SOPHISTICATED_SHULKER_RECIPE_CLASS);
        if (wrapper == null) {
            sophisticatedShulkerCompatibility = false;
            return false;
        }
        ClassNode helper = readTarget(EXTENDABLE_RECIPE_HELPER_CLASS);
        boolean compatible = hasSophisticatedShulkerContract(helper, wrapper);
        sophisticatedShulkerCompatibility = compatible;
        if (!compatible) {
            LOGGER.warn(
                "JEI Optimize disabled Sophisticated Storage shulker recipe extension reuse because its complete ABI changed."
            );
        }
        return compatible;
    }

    static boolean hasSophisticatedShulkerContract(ClassNode helper, ClassNode wrapper) {
        return helper != null
            && wrapper != null
            && Requirement.field("", "cache", "Ljava/util/Map;").isPresentIn(helper)
            && Requirement.method(
                "",
                "getOptionalRecipeExtension",
                "(Ljava/lang/Object;)Ljava/util/Optional;"
            ).isPresentIn(helper)
            && Requirement.field(
                "",
                "compose",
                "Lnet/minecraft/world/item/crafting/ShapelessRecipe;"
            ).isPresentIn(wrapper);
    }

    private boolean isAtomicFeatureCompatible(AtomicFeature feature, boolean variant) {
        Boolean cached = atomicFeatureCompatibility.get(feature);
        if (cached != null) {
            return cached;
        }

        AtomicFeatureCheck check = checkAtomicFeature(feature, this::readTarget);
        boolean compatible = check.compatible();
        boolean sawTarget = check.sawTarget();
        String missing = check.missing();
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

    private static AtomicFeatureCheck checkAtomicFeature(
        AtomicFeature feature,
        Function<String, ClassNode> targetResolver
    ) {
        boolean sawTarget = false;
        String missing = null;
        for (TargetRequirement targetRequirement : feature.targets()) {
            ClassNode target = targetResolver.apply(targetRequirement.className());
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
        for (InvocationTargetRequirement invocationTarget : feature.invocations()) {
            ClassNode target = targetResolver.apply(invocationTarget.className());
            if (target == null) {
                if (missing == null) {
                    missing = "class " + invocationTarget.className();
                }
                continue;
            }
            sawTarget = true;
            int invocationCount = invocationTarget.requirement().countIn(target);
            boolean invocationCompatible = invocationTarget.expectedCount() == 0
                ? invocationCount > 0
                : invocationCount == invocationTarget.expectedCount();
            if (!invocationCompatible && missing == null) {
                missing = invocationTarget.className() + " " + invocationTarget.requirement().describe()
                    + (invocationTarget.expectedCount() == 0
                        ? ""
                        : " exactly " + invocationTarget.expectedCount() + " time(s)");
            }
        }
        if (feature == IRONS_SPELLS_FEATURE && missing == null
            && !hasIronsSpellsScrollHolder(targetResolver.apply("io.redspace.ironsspellbooks.registries.ItemRegistry"))) {
            missing = "public static SCROLL with a supported Forge or NeoForge registry holder";
        }
        if (feature == IRONS_SPELLS_FEATURE && missing == null && !hasIronsSpellsAccess(targetResolver)) {
            missing = "callable Iron's Spells factories, instance recipe fields and public accessors";
        }
        return new AtomicFeatureCheck(missing == null, sawTarget, missing);
    }

    private static boolean hasIronsSpellsAccess(Function<String, ClassNode> targetResolver) {
        for (TargetRequirement targetRequirement : IRONS_SPELLS_FEATURE.targets()) {
            ClassNode target = targetResolver.apply(targetRequirement.className());
            for (Requirement requirement : targetRequirement.requirements()) {
                if (requirement.isField()) {
                    if (!requirement.memberName().equals("SCROLL") && target.fields.stream().anyMatch(field ->
                        field.name.equals(requirement.memberName()) && (field.access & Opcodes.ACC_STATIC) != 0)) {
                        return false;
                    }
                    continue;
                }
                String name = requirement.memberName();
                boolean staticMethod = name.equals("getRecipes") || name.equals("getEnabledSpells") || name.equals("createScrollContainer");
                for (MethodNode method : target.methods) {
                    if (!name.equals(method.name) || !requirement.descriptor().equals(method.desc)) {
                        continue;
                    }
                    if (((method.access & Opcodes.ACC_STATIC) != 0) != staticMethod
                        || (!name.equals("getRecipes") && (method.access & Opcodes.ACC_PUBLIC) == 0)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasIronsSpellsScrollHolder(ClassNode registry) {
        int requiredAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        return registry != null && registry.fields.stream().anyMatch(field -> field.name.equals("SCROLL")
            && (field.access & requiredAccess) == requiredAccess
            && (field.desc.equals("Lnet/minecraftforge/registries/RegistryObject;")
                || field.desc.equals("Lnet/neoforged/neoforge/registries/DeferredHolder;")));
    }

    static boolean hasCompatibilityContract(String mixinName, Function<String, ClassNode> targetResolver) {
        AtomicFeature feature = ATOMIC_FEATURES.get(MIXIN_PACKAGE + mixinName);
        return feature != null && checkAtomicFeature(feature, targetResolver).compatible();
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

    private record AtomicFeature(
        String name,
        List<TargetRequirement> targets,
        List<InvocationTargetRequirement> invocations
    ) {
        private AtomicFeature(String name, List<TargetRequirement> targets) {
            this(name, targets, List.of());
        }
    }

    private record ConfigGate(String key, boolean defaultValue) {
    }

    private record TargetRequirement(String className, List<Requirement> requirements) {
    }

    private record InvocationTargetRequirement(
        String className,
        InvocationRequirement requirement,
        int expectedCount
    ) {
        private InvocationTargetRequirement(String className, InvocationRequirement requirement) {
            this(className, requirement, 0);
        }
    }

    private record AtomicFeatureCheck(boolean compatible, boolean sawTarget, String missing) {
    }

    enum GrindstoneVariant {
        LEGACY,
        MODERN,
        NONE
    }

    private record InvocationRequirement(
        String enclosingMethod,
        String enclosingDescriptor,
        String owner,
        String invokedMethod,
        String invokedDescriptor
    ) {
        boolean isPresentIn(ClassNode target) {
            return countIn(target) > 0;
        }

        int countIn(ClassNode target) {
            int count = 0;
            for (MethodNode method : target.methods) {
                if (!enclosingMethod.equals(method.name) || !enclosingDescriptor.equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (matches(instruction)) {
                        count++;
                    }
                }
            }
            return count;
        }

        boolean matches(AbstractInsnNode instruction) {
            return instruction instanceof MethodInsnNode invocation
                && owner.equals(invocation.owner)
                && invokedMethod.equals(invocation.name)
                && invokedDescriptor.equals(invocation.desc);
        }

        String describe() {
            return "invocation " + owner + '.' + invokedMethod + invokedDescriptor
                + " in " + enclosingMethod + enclosingDescriptor;
        }
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
