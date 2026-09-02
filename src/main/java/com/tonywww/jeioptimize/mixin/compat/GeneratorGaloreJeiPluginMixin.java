package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.integration.GeneratorGaloreRecipeCompactor;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeRegistration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "cy.jdkdigital.generatorgalore.integrations.JeiPlugin", remap = false)
public abstract class GeneratorGaloreJeiPluginMixin {
    @WrapOperation(
        method = "lambda$registerRecipes$14(Lmezz/jei/api/registration/IRecipeRegistration;"
            + "Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;"
            + "Lnet/minecraft/resources/ResourceLocation;"
            + "Lcy/jdkdigital/generatorgalore/util/GeneratorObject;)V",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/api/registration/IRecipeRegistration;addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V"
        ),
        require = 1
    )
    private static <T> void jeiOptimize$compactSolidFuelRecipes(
        IRecipeRegistration registration,
        RecipeType<T> recipeType,
        List<T> recipes,
        Operation<Void> original
    ) {
        @SuppressWarnings("unchecked")
        List<T> compacted = (List<T>) GeneratorGaloreRecipeCompactor.compact(
            recipes,
            registration.getIngredientManager()
        );
        original.call(registration, recipeType, compacted);
    }
}