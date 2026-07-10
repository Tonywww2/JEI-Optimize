package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.collect.RecipeMap", remap = false)
public abstract class RecipeMapCatalystMixin {
    @Inject(method = "addCatalystForCategory", at = @At("HEAD"), remap = false)
    private <T> void jeiOptimize$observeCatalystAdd(RecipeType<?> recipeType, ITypedIngredient<T> ingredient, CallbackInfo callbackInfo) {
        if (!JeiOptFeatureFlags.catalystPreheat()) {
            return;
        }
        // Intentionally no-op for now. Catalyst indexing is built from RecipeIndexSnapshot
        // so this hook only verifies the target and provides a future integration point
        // without changing JEI's baseline add behavior.
    }

    @Inject(method = "isCatalystForRecipeCategory", at = @At("HEAD"), cancellable = true, remap = false)
    private <T> void jeiOptimize$queryAsyncCatalystIndex(
        RecipeType<T> recipeType,
        ITypedIngredient<?> ingredient,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (!JeiOptFeatureFlags.catalystPreheat()) {
            return;
        }
        // Do not short-circuit until PG-1 provides precomputed catalyst UIDs for this query path.
        // Returning baseline here preserves correctness while allowing this mixin target to compile.
    }
}