package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.integration.IronFurnacesGeneratorCompactor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {
        "ironfurnaces.jei.RecipeCategoryGeneratorRegular",
        "ironfurnaces.jei.RecipeCategoryGeneratorSmoking"
    },
    remap = false
)
public abstract class IronFurnacesGeneratorCategoryMixin {
    @Inject(method = "setRecipe", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$addCompactedInput(
        IRecipeLayoutBuilder builder,
        @Coerce Object recipe,
        IFocusGroup focuses,
        CallbackInfo callbackInfo
    ) {
        if (IronFurnacesGeneratorCompactor.addCompactedInput(builder, recipe)) {
            callbackInfo.cancel();
        }
    }
}