package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/**
 * Lets the config hide JEI's two generated anvil recipe kinds (repair and enchanting).
 * When a flag is on, the corresponding generator returns an empty stream, so those recipes are
 * neither generated nor shown. Both flags default to false (JEI's normal behavior).
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
public abstract class AnvilRecipeControlMixin {
    // getRepairRecipes(IVanillaRecipeFactory, IIngredientHelper) is a static method with this same
    // erased descriptor on both Forge 1.20.1 (JEI 15.x) and NeoForge 1.21.1 (JEI 19.27.x).
    @Inject(
        method = "getRepairRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/ingredients/IIngredientHelper;)Ljava/util/stream/Stream;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void jeiopt$maybeSkipRepairRecipes(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientHelper<?> ingredientHelper,
        CallbackInfoReturnable<Stream<?>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.disableAnvilRepairRecipes()) {
            callbackInfo.setReturnValue(Stream.empty());
        }
    }

    // getBookEnchantmentRecipes differs: Forge 1.20.1 takes a trailing IIngredientHelper; NeoForge
    // 1.21.1 (JEI 19.27.x) dropped it.
    //? if forge {
    @Inject(
        method = "getBookEnchantmentRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/api/ingredients/IIngredientHelper;)Ljava/util/stream/Stream;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void jeiopt$maybeSkipEnchantRecipes(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientManager ingredientManager,
        IIngredientHelper<?> ingredientHelper,
        CallbackInfoReturnable<Stream<?>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.disableAnvilEnchantRecipes()) {
            callbackInfo.setReturnValue(Stream.empty());
        }
    }
    //?} else {
    /*@Inject(
        method = "getBookEnchantmentRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/stream/Stream;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void jeiopt$maybeSkipEnchantRecipes(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<Stream<?>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.disableAnvilEnchantRecipes()) {
            callbackInfo.setReturnValue(Stream.empty());
        }
    }
    *///?}
}
