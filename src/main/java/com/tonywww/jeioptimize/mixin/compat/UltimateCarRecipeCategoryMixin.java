package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.integration.UltimateCarWorkshopCompactor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.maxhenkel.car.integration.jei.CarRecipeCategory", remap = false)
public abstract class UltimateCarRecipeCategoryMixin {
    @Inject(method = "setRecipe", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$addCompactedInputs(
        IRecipeLayoutBuilder builder,
        @Coerce Object recipe,
        IFocusGroup focuses,
        CallbackInfo callbackInfo
    ) {
        if (UltimateCarWorkshopCompactor.addCompactSlots(builder, recipe)) {
            callbackInfo.cancel();
        }
    }
}