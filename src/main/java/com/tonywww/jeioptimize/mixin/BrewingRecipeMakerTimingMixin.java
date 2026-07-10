package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Diagnostic: times JEI's brewing recipe generation (potion combinatorics). Gated by pluginTiming.
 */
@Pseudo
@Mixin(targets = "mezz.jei.forge.platform.RecipeHelper", remap = false)
public abstract class BrewingRecipeMakerTimingMixin {
    @Unique
    private static long jeiopt$brewingStartNanos;

    @Inject(method = "getBrewingRecipes", at = @At("HEAD"))
    private void jeiopt$brewingStart(
        IIngredientManager ingredientManager,
        IVanillaRecipeFactory vanillaRecipeFactory,
        CallbackInfoReturnable<List<Object>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.pluginTiming()) {
            jeiopt$brewingStartNanos = System.nanoTime();
        }
    }

    @Inject(method = "getBrewingRecipes", at = @At("RETURN"))
    private void jeiopt$brewingEnd(
        IIngredientManager ingredientManager,
        IVanillaRecipeFactory vanillaRecipeFactory,
        CallbackInfoReturnable<List<Object>> callbackInfo
    ) {
        if (!JeiOptFeatureFlags.pluginTiming()) {
            return;
        }
        long elapsedMs = (System.nanoTime() - jeiopt$brewingStartNanos) / 1_000_000L;
        List<Object> result = callbackInfo.getReturnValue();
        JeiOptimize.LOGGER.info(
            "JEI Optimize brewing recipe generation: {} recipes in {} ms",
            result == null ? -1 : result.size(),
            elapsedMs
        );
    }
}
