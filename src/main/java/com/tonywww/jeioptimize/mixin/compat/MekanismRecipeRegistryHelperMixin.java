package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.integration.MekanismNutritionalRecipeCompactor;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "mekanism.client.jei.RecipeRegistryHelper", remap = false)
public abstract class MekanismRecipeRegistryHelperMixin {
    @WrapOperation(
        method = "register(Lmezz/jei/api/registration/IRecipeRegistration;"
            + "Lmekanism/client/jei/MekanismJEIRecipeType;Ljava/util/List;)V",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
        )
    )
    private static <T> void jeiOptimize$compactNutritionalLiquifierRecipes(
        IRecipeRegistration registration,
        RecipeType<T> recipeType,
        List<T> recipes,
        Operation<Void> original
    ) {
        @SuppressWarnings("unchecked")
        List<T> compacted = (List<T>) MekanismNutritionalRecipeCompactor.compact(
            recipes,
            registration.getIngredientManager()
        );
        original.call(registration, recipeType, compacted);
    }
}