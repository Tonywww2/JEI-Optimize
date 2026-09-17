package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.IronsSpellsRecipeCompactor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.jei.ArcaneAnvilJeiRecipe", remap = false)
public abstract class IronsSpellsArcaneAnvilRecipeMixin {
    @Inject(method = "getRecipeItems()Lio/redspace/ironsspellbooks/jei/ArcaneAnvilJeiRecipe$Tuple;",
        at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$materializeRepresentativeRecipe(CallbackInfoReturnable<Object> callbackInfo) {
        if (!JeiOptFeatureFlags.compactIronsSpellsImbuing()) {
            return;
        }
        Object recipeItems = IronsSpellsRecipeCompactor.createRecipeItems(this);
        if (recipeItems != null) {
            callbackInfo.setReturnValue(recipeItems);
        }
    }
}