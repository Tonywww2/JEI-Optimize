package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.UltimateCarWorkshopCompactor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "de.maxhenkel.car.integration.jei.CarRecipeBuilder", remap = false)
public abstract class UltimateCarRecipeBuilderMixin {
    @Inject(method = "getAllRecipes", at = @At("HEAD"), cancellable = true)
    private static void jeiOptimize$replaceWorkshopCombinations(CallbackInfoReturnable<List<?>> callbackInfo) {
        if (!JeiOptFeatureFlags.compactUltimateCarWorkshop()) {
            return;
        }
        List<?> compacted = UltimateCarWorkshopCompactor.createCompactRecipes(
            UltimateCarRecipeBuilderMixin.class.getClassLoader()
        );
        if (compacted != null) {
            callbackInfo.setReturnValue(compacted);
        }
    }
}