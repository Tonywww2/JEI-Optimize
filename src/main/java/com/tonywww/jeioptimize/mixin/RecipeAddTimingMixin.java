package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.recipe.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Diagnostic: times each {@code RecipeManagerInternal.addRecipes(type, list)} call so we can see the
 * per-recipe-type indexing cost and count during the "Registering recipes" phase. Gated by pluginTiming.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class RecipeAddTimingMixin {
    @Unique
    private static final ThreadLocal<Long> jeiopt$addRecipesStart = new ThreadLocal<>();

    @Inject(method = "addRecipes", at = @At("HEAD"))
    private void jeiopt$addRecipesStart(RecipeType<?> recipeType, List<?> recipes, CallbackInfo callbackInfo) {
        if (JeiOptFeatureFlags.pluginTiming()) {
            jeiopt$addRecipesStart.set(System.nanoTime());
        }
    }

    @Inject(method = "addRecipes", at = @At("RETURN"))
    private void jeiopt$addRecipesEnd(RecipeType<?> recipeType, List<?> recipes, CallbackInfo callbackInfo) {
        if (!JeiOptFeatureFlags.pluginTiming()) {
            return;
        }
        Long start = jeiopt$addRecipesStart.get();
        if (start == null) {
            return;
        }
        jeiopt$addRecipesStart.remove();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        JeiOptimize.LOGGER.info(
            "JEI Optimize addRecipes index: {} size={} in {} ms",
            recipeType.getUid(),
            recipes.size(),
            elapsedMs
        );
    }
}
