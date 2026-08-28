package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.integration.EmbersDawnstoneAnvilCompactor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.rekindled.embers.compat.jei.DawnstoneAnvilCategory", remap = false)
public abstract class EmbersDawnstoneAnvilCategoryMixin {
    @Inject(method = "setRecipe", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$addCompactedLayout(
        IRecipeLayoutBuilder builder,
        @Coerce Object recipe,
        IFocusGroup focuses,
        CallbackInfo callbackInfo
    ) {
        if (EmbersDawnstoneAnvilCompactor.addCompactedLayout(builder, recipe)) {
            callbackInfo.cancel();
        }
    }
}