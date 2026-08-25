package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.IronsSpellsRecipeCompactor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeMaker", remap = false)
public abstract class IronsSpellsArcaneAnvilMakerMixin {
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true)
    private static void jeiOptimize$compactImbuingRecipes(CallbackInfoReturnable<List<?>> callbackInfo) {
        if (JeiOptFeatureFlags.compactIronsSpellsImbuing() && callbackInfo.getReturnValue() != null) {
            callbackInfo.setReturnValue(IronsSpellsRecipeCompactor.compact(callbackInfo.getReturnValue()));
        }
    }
}