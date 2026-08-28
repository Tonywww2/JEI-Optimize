package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.integration.EmbersDawnstoneAnvilCompactor;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "com.rekindled.embers.compat.jei.JEIPlugin", remap = false)
public abstract class EmbersJeiPluginMixin {
    @WrapOperation(
        method = "addRecipes",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
        )
    )
    private static <T> void jeiOptimize$compactDawnstoneAnvilRecipes(
        IRecipeRegistration registration,
        RecipeType<T> recipeType,
        List<T> recipes,
        Operation<Void> original
    ) {
        @SuppressWarnings("unchecked")
        List<T> compacted = (List<T>) EmbersDawnstoneAnvilCompactor.compact(
            recipes,
            registration.getIngredientManager()
        );
        original.call(registration, recipeType, compacted);
    }
}