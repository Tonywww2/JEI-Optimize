package com.tonywww.jeioptimize;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiOptMixinPluginTest {
    private static final String PLATFORM_HELPER = "mezz/jei/common/platform/IPlatformRecipeHelper";

    @Test
    void detectsForgeLegacyDisenchantInvocation() {
        ClassNode target = classWithInvocation(
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        );

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.LEGACY,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void detectsNeoForgeLegacyDisenchantInvocation() {
        ClassNode target = classWithInvocation(
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/stream/Stream;",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;)Z"
        );

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.LEGACY,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void detectsModernCanEnchantHelper() {
        ClassNode target = classWithModernHelper();

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.MODERN,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void rejectsMixedModernAndLegacyCallGraph() {
        ClassNode target = classWithInvocation(
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        );
        addModernHelper(target, false);

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.NONE,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void rejectsUncalledModernHelper() {
        ClassNode target = new ClassNode();
        addModernHelper(target, true);

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.NONE,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void rejectsMethodWithoutSupportedHelperOrInvocation() {
        ClassNode target = new ClassNode();
        target.methods.add(new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "getDisenchantRecipes",
            "()Ljava/util/stream/Stream;",
            null,
            null
        ));

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.NONE,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void acceptsOnlyTheVerifiedThreeCallRecipeDiagnosticLayout() {
        assertEquals(true, JeiOptMixinPlugin.hasUnsafeRecipeDiagnostics(recipeDiagnosticClass(3)));
        assertEquals(false, JeiOptMixinPlugin.hasUnsafeRecipeDiagnostics(recipeDiagnosticClass(2)));
        assertEquals(false, JeiOptMixinPlugin.hasUnsafeRecipeDiagnostics(recipeDiagnosticClass(4)));
        assertEquals(false, JeiOptMixinPlugin.hasUnsafeRecipeDiagnostics(recipeDiagnosticClass(3, false)));
    }

    @Test
    void pluginCallbackRoutingRequiresExactlyOneConsumerInvocation() {
        assertEquals(true, JeiOptMixinPlugin.hasPluginCallbackRoutingContract(pluginCallerClass(1)));
        assertEquals(false, JeiOptMixinPlugin.hasPluginCallbackRoutingContract(pluginCallerClass(0)));
        assertEquals(false, JeiOptMixinPlugin.hasPluginCallbackRoutingContract(pluginCallerClass(2)));
        assertEquals(false, JeiOptMixinPlugin.hasPluginCallbackRoutingContract(null));
    }

    @Test
    void sophisticatedShulkerContractRequiresHelperAndAccessorMembersTogether() {
        ClassNode helper = new ClassNode();
        helper.fields.add(new FieldNode(0, "cache", "Ljava/util/Map;", null, null));
        helper.methods.add(new MethodNode(
            0,
            "getOptionalRecipeExtension",
            "(Ljava/lang/Object;)Ljava/util/Optional;",
            null,
            null
        ));
        ClassNode wrapper = new ClassNode();
        wrapper.fields.add(new FieldNode(
            0,
            "compose",
            "Lnet/minecraft/world/item/crafting/ShapelessRecipe;",
            null,
            null
        ));

        assertEquals(true, JeiOptMixinPlugin.hasSophisticatedShulkerContract(helper, wrapper));
        helper.fields.clear();
        assertEquals(false, JeiOptMixinPlugin.hasSophisticatedShulkerContract(helper, wrapper));
        assertEquals(false, JeiOptMixinPlugin.hasSophisticatedShulkerContract(null, wrapper));
    }

    @Test
    void anvilRepresentativeContractRequiresBothMixinTargets() {
        ClassNode recipeMaker = new ClassNode();
        recipeMaker.methods.add(new MethodNode(
            0,
            "getAnvilRecipes",
            "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;",
            null,
            null
        ));
        ClassNode enchantmentData = new ClassNode();
        enchantmentData.methods.add(new MethodNode(
            0,
            "canEnchant",
            "(Lnet/minecraft/world/item/ItemStack;)Z",
            null,
            null
        ));

        assertEquals(true, JeiOptMixinPlugin.hasAnvilRepresentativeContract(recipeMaker, enchantmentData));
        enchantmentData.methods.clear();
        assertEquals(false, JeiOptMixinPlugin.hasAnvilRepresentativeContract(recipeMaker, enchantmentData));
        assertEquals(false, JeiOptMixinPlugin.hasAnvilRepresentativeContract(recipeMaker, null));
    }

    @Test
    void thermalStirlingContractRequiresConstructorAndInheritedAccessors() {
        ClassNode plugin = classWithMethod(
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
        );
        plugin.methods.get(0).instructions.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "mezz/jei/api/registration/IRecipeRegistration",
            "addRecipes",
            "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V",
            true
        ));
        ClassNode stirlingFuel = classWithMethod(
            "<init>",
            "(Lnet/minecraft/resources/ResourceLocation;ILjava/util/List;Ljava/util/List;)V"
        );
        ClassNode thermalFuel = classWithMethod("getInputItems", "()Ljava/util/List;");
        thermalFuel.methods.add(new MethodNode(0, "getInputFluids", "()Ljava/util/List;", null, null));
        thermalFuel.methods.add(new MethodNode(0, "getEnergy", "()I", null, null));
        Map<String, ClassNode> targets = Map.of(
            "cofh.thermal.expansion.compat.jei.TExpJeiPlugin", plugin,
            "cofh.thermal.core.util.recipes.dynamo.StirlingFuel", stirlingFuel,
            "cofh.thermal.lib.util.recipes.ThermalFuel", thermalFuel
        );

        assertEquals(true, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.ThermalExpansionJeiPluginMixin", targets));
        plugin.methods.get(0).instructions.clear();
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.ThermalExpansionJeiPluginMixin", targets));
        plugin.methods.get(0).instructions.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "mezz/jei/api/registration/IRecipeRegistration",
            "addRecipes",
            "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V",
            true
        ));
        thermalFuel.methods.remove(thermalFuel.methods.size() - 1);
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.ThermalExpansionJeiPluginMixin", targets));
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.ThermalExpansionJeiPluginMixin", Map.of()));
    }

    @Test
    void generatorGaloreContractRequiresLambdaCallGraphAndRecordMembers() {
        ClassNode plugin = classWithMethod(
            "lambda$registerRecipes$14",
            "(Lmezz/jei/api/registration/IRecipeRegistration;Ljava/util/List;Ljava/util/List;"
                + "Ljava/util/List;Ljava/util/List;Lnet/minecraft/resources/ResourceLocation;"
                + "Lcy/jdkdigital/generatorgalore/util/GeneratorObject;)V"
        );
        addRecipesInvocation(plugin.methods.get(0));
        ClassNode recipe = classWithMethods(
            "<init>",
            "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/List;"
                + "Lnet/minecraft/world/item/crafting/Ingredient;FI)V",
            "id", "()Lnet/minecraft/resources/ResourceLocation;",
            "fuels", "()Ljava/util/List;",
            "generator", "()Lnet/minecraft/world/item/crafting/Ingredient;",
            "rate", "()F",
            "burnTime", "()I"
        );
        Map<String, ClassNode> targets = Map.of(
            "cy.jdkdigital.generatorgalore.integrations.JeiPlugin", plugin,
            "cy.jdkdigital.generatorgalore.common.recipe.SolidFuelRecipe", recipe
        );

        assertEquals(true, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.GeneratorGaloreJeiPluginMixin", targets));
        plugin.methods.get(0).instructions.clear();
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.GeneratorGaloreJeiPluginMixin", targets));
    }

    @Test
    void mekanismContractRequiresInheritedInputAndCreatorMembers() {
        ClassNode plugin = classWithMethod(
            "register",
            "(Lmezz/jei/api/registration/IRecipeRegistration;"
                + "Lmekanism/client/jei/MekanismJEIRecipeType;Ljava/util/List;)V"
        );
        addRecipesInvocation(plugin.methods.get(0));
        ClassNode creator = classWithMethod(
            "from",
            "(Lnet/minecraft/world/item/crafting/Ingredient;I)"
                + "Lmekanism/api/recipes/ingredients/ItemStackIngredient;"
        );
        Map<String, ClassNode> targets = Map.of(
            "mekanism.client.jei.RecipeRegistryHelper", plugin,
            "mekanism.common.recipe.impl.NutritionalLiquifierIRecipe", classWithMethod(
                "<init>",
                "(Lnet/minecraft/world/item/Item;Lmekanism/api/recipes/ingredients/ItemStackIngredient;"
                    + "Lnet/minecraftforge/fluids/FluidStack;)V"
            ),
            "mekanism.api.recipes.ItemStackToFluidRecipe", classWithMethods(
                "getInput", "()Lmekanism/api/recipes/ingredients/ItemStackIngredient;",
                "getOutputDefinition", "()Ljava/util/List;"
            ),
            "mekanism.api.recipes.ingredients.InputIngredient", classWithMethod(
                "getRepresentations", "()Ljava/util/List;"),
            "mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess", classWithMethod(
                "item", "()Lmekanism/api/recipes/ingredients/creator/IItemStackIngredientCreator;"),
            "mekanism.api.recipes.ingredients.creator.IItemStackIngredientCreator", creator,
            "net.minecraftforge.fluids.FluidStack", classWithMethods(
                "getFluid", "()Lnet/minecraft/world/level/material/Fluid;",
                "getAmount", "()I"
            )
        );

        assertEquals(true, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.MekanismRecipeRegistryHelperMixin", targets));
        creator.methods.clear();
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.MekanismRecipeRegistryHelperMixin", targets));
    }

    @Test
    void tinkersContractRequiresDisplayRecipeAndForgeFluidMembers() {
        ClassNode plugin = classWithMethod(
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
        );
        addRecipesInvocation(plugin.methods.get(0));
        ClassNode displayRecipe = classWithMethods(
            "<init>",
            "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/world/item/crafting/RecipeType;"
                + "Ljava/util/List;Ljava/util/List;Ljava/util/List;IZ)V",
            "getRecipeId", "()Lnet/minecraft/resources/ResourceLocation;",
            "getType", "()Lnet/minecraft/world/item/crafting/RecipeType;",
            "getCastItems", "()Ljava/util/List;",
            "getFluids", "()Ljava/util/List;",
            "getOutputs", "()Ljava/util/List;",
            "getCoolingTime", "()I",
            "isConsumed", "()Z"
        );
        ClassNode forgeTypes = new ClassNode();
        forgeTypes.fields.add(new FieldNode(
            0,
            "FLUID_STACK",
            "Lmezz/jei/api/ingredients/IIngredientTypeWithSubtypes;",
            null,
            null
        ));
        Map<String, ClassNode> targets = Map.of(
            "slimeknights.tconstruct.plugin.jei.JEIPlugin", plugin,
            "slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe", displayRecipe,
            "mezz.jei.api.forge.ForgeTypes", forgeTypes,
            "net.minecraftforge.fluids.FluidStack", classWithMethods(
                "getAmount", "()I",
                "getTag", "()Lnet/minecraft/nbt/CompoundTag;"
            )
        );

        assertEquals(true, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.TinkersJeiPluginMixin", targets));
        forgeTypes.fields.clear();
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.TinkersJeiPluginMixin", targets));
    }

    @Test
    void ironFurnacesContractRequiresPluginCategoriesAndRecipeMembers() {
        ClassNode plugin = classWithMethod(
            "registerRecipes",
            "(Lmezz/jei/api/registration/IRecipeRegistration;)V"
        );
        addRecipesInvocation(plugin.methods.get(0));
        String setRecipeDescriptor = "(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;"
            + "Lironfurnaces/recipes/SimpleGeneratorRecipe;Lmezz/jei/api/recipe/IFocusGroup;)V";
        ClassNode recipe = classWithMethods(
            "<init>", "(ILnet/minecraft/world/item/ItemStack;)V",
            "getEnergy", "()I",
            "getIngredient", "()Lnet/minecraft/world/item/ItemStack;"
        );
        Map<String, ClassNode> targets = Map.of(
            "ironfurnaces.jei.IronFurnacesJEIPlugin", plugin,
            "ironfurnaces.jei.RecipeCategoryGeneratorRegular", classWithMethod(
                "setRecipe", setRecipeDescriptor),
            "ironfurnaces.jei.RecipeCategoryGeneratorSmoking", classWithMethod(
                "setRecipe", setRecipeDescriptor),
            "ironfurnaces.recipes.SimpleGeneratorRecipe", recipe
        );

        assertEquals(true, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.IronFurnacesJeiPluginMixin", targets));
        recipe.methods.remove(recipe.methods.size() - 1);
        assertEquals(false, JeiOptMixinPlugin.hasAtomicFeatureContract(
            "compat.IronFurnacesGeneratorCategoryMixin", targets));
    }

    private static ClassNode classWithInvocation(String methodDescriptor, String invocationDescriptor) {
        ClassNode target = new ClassNode();
        MethodNode method = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "getDisenchantRecipes",
            methodDescriptor,
            null,
            null
        );
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            PLATFORM_HELPER,
            "isItemEnchantable",
            invocationDescriptor,
            true
        ));
        target.methods.add(method);
        return target;
    }

    private static ClassNode classWithMethod(String name, String descriptor) {
        ClassNode target = new ClassNode();
        target.methods.add(new MethodNode(0, name, descriptor, null, null));
        return target;
    }

    private static ClassNode classWithMethods(String... namesAndDescriptors) {
        ClassNode target = new ClassNode();
        for (int index = 0; index < namesAndDescriptors.length; index += 2) {
            target.methods.add(new MethodNode(
                0,
                namesAndDescriptors[index],
                namesAndDescriptors[index + 1],
                null,
                null
            ));
        }
        return target;
    }

    private static void addRecipesInvocation(MethodNode method) {
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            "mezz/jei/api/registration/IRecipeRegistration",
            "addRecipes",
            "(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V",
            true
        ));
    }

    private static ClassNode recipeDiagnosticClass(int invocationCount) {
        return recipeDiagnosticClass(invocationCount, true);
    }

    private static ClassNode recipeDiagnosticClass(int invocationCount, boolean includeUnhandledMarker) {
        ClassNode target = new ClassNode();
        MethodNode method = new MethodNode(
            Opcodes.ACC_PRIVATE,
            "addRecipe",
            "(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/lang/Object;Ljava/util/Set;)Z",
            null,
            null
        );
        for (int index = 0; index < invocationCount; index++) {
            method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "mezz/jei/library/util/RecipeDebugUtil",
                "getDebugInfoFromRecipe",
                "(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;"
                    + "Lmezz/jei/api/runtime/IIngredientManager;)Ljava/lang/String;",
                false
            ));
            if (includeUnhandledMarker && index == 1) {
                method.instructions.add(new LdcInsnNode(
                    "Recipe not added because the recipe category cannot handle it: {}"
                ));
            }
        }
        target.methods.add(method);
        return target;
    }

    private static ClassNode pluginCallerClass(int invocationCount) {
        ClassNode target = new ClassNode();
        MethodNode method = new MethodNode(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "callOnPlugins",
            "(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V",
            null,
            null
        );
        for (int index = 0; index < invocationCount; index++) {
            method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/function/Consumer",
                "accept",
                "(Ljava/lang/Object;)V",
                true
            ));
        }
        target.methods.add(method);
        return target;
    }

    private static ClassNode classWithModernHelper() {
        ClassNode target = new ClassNode();
        addModernHelper(target, false);
        return target;
    }

    private static void addModernHelper(ClassNode target, boolean omitCaller) {
        String helperDescriptor = "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/enchantment/Enchantment;"
            + "Lnet/minecraft/resources/ResourceLocation;)Z";
        target.methods.add(new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "canEnchant",
            helperDescriptor,
            null,
            null
        ));
        if (omitCaller) {
            return;
        }
        MethodNode caller = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "getDisenchantRecipes",
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            null,
            null
        );
        caller.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "mezz/jei/library/plugins/vanilla/grindstone/GrindstoneRecipeMaker",
            "canEnchant",
            helperDescriptor,
            false
        ));
        target.methods.add(caller);
    }
}