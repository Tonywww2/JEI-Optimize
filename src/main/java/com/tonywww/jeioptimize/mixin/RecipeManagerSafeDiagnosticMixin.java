package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class RecipeManagerSafeDiagnosticMixin {
    @WrapOperation(
        method = "addRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/library/util/RecipeDebugUtil;getDebugInfoFromRecipe(Ljava/lang/Object;Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/lang/String;",
            ordinal = 1
        ),
        require = 1
    )
    private static <T> String jeiOptimize$describeRecipeWithoutRebuildingLayout(
        T recipe,
        IRecipeCategory<T> recipeCategory,
        IIngredientManager ingredientManager,
        Operation<String> original
    ) {
        return "Recipe Class: " + (recipe == null ? "null" : recipe.getClass().getName())
            + "\nRecipe Category: " + recipeCategory.getRecipeType().getUid();
    }
}