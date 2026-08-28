package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.recipe.FuelRecipeCompactor;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.cooking.fuel.FuelRecipeMaker", remap = false)
public abstract class FuelRecipeMakerMixin {
    @Inject(
        method = "getFuelRecipes(Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void jeiOptimize$compactFuelRecipes(
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<List<IJeiFuelingRecipe>> callbackInfo
    ) {
        callbackInfo.setReturnValue(FuelRecipeCompactor.compact(callbackInfo.getReturnValue(), ingredientManager));
    }
}