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
 * Diagnostic: times JEI's anvil recipe generation (a known combinatorial startup hog). Gated by pluginTiming.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
public abstract class AnvilRecipeMakerTimingMixin {
    @Unique
    private static long jeiopt$anvilStartNanos;

    @Inject(method = "getAnvilRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;", at = @At("HEAD"))
    private static void jeiopt$anvilStart(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<List<Object>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.pluginTiming()) {
            jeiopt$anvilStartNanos = System.nanoTime();
        }
    }

    @Inject(method = "getAnvilRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;", at = @At("RETURN"))
    private static void jeiopt$anvilEnd(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<List<Object>> callbackInfo
    ) {
        if (!JeiOptFeatureFlags.pluginTiming()) {
            return;
        }
        long elapsedMs = (System.nanoTime() - jeiopt$anvilStartNanos) / 1_000_000L;
        List<Object> result = callbackInfo.getReturnValue();
        JeiOptimize.LOGGER.info(
            "JEI Optimize anvil recipe generation: {} recipes in {} ms",
            result == null ? -1 : result.size(),
            elapsedMs
        );
    }
}
