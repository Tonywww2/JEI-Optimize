package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tonywww.jeioptimize.recipe.BrewingRecipeIndex;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "mezz.jei.library.util.BrewingRecipeMakerCommon", remap = false)
public abstract class BrewingRecipeIndexForgeMixin {
    @WrapOperation(
        method = "getNewPotions",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;stream()Ljava/util/stream/Stream;")
    )
    private static Stream<IJeiBrewingRecipe> jeiOptimize$findIndexedRecipe(
        Collection<IJeiBrewingRecipe> recipes,
        Operation<Stream<IJeiBrewingRecipe>> original,
        @Local(ordinal = 0) IJeiBrewingRecipe recipe
    ) {
        if (!BrewingRecipeIndex.usable(recipes)) {
            return original.call(recipes);
        }
        return Stream.ofNullable(BrewingRecipeIndex.find(recipe));
    }

    @WrapOperation(
        method = "getNewPotions",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;add(Ljava/lang/Object;)Z")
    )
    private static boolean jeiOptimize$trackAddedRecipe(
        Collection<IJeiBrewingRecipe> recipes,
        Object recipe,
        Operation<Boolean> original
    ) {
        boolean added = original.call(recipes, recipe);
        if (added && recipe instanceof IJeiBrewingRecipe brewingRecipe) {
            BrewingRecipeIndex.prepare(recipes);
            BrewingRecipeIndex.added(recipes, brewingRecipe);
        }
        return added;
    }

    @WrapOperation(
        method = "getNewPotions",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;remove(Ljava/lang/Object;)Z")
    )
    private static boolean jeiOptimize$trackRemovedRecipe(
        Collection<IJeiBrewingRecipe> recipes,
        Object recipe,
        Operation<Boolean> original
    ) {
        boolean removed = original.call(recipes, recipe);
        if (removed && recipe instanceof IJeiBrewingRecipe brewingRecipe) {
            BrewingRecipeIndex.removed(recipes, brewingRecipe);
        }
        return removed;
    }
}