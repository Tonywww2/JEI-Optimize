package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.CelestialForgeReinforceCache;
import com.tonywww.jeioptimize.integration.CelestialForgeReinforceInputPool;
import com.tonywww.jeioptimize.recipe.ItemStackRepresentativeSelector;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.xiaoyue.celestial_forge.compat.ReinforceRecipeWrapper", remap = false)
public abstract class CelestialForgeReinforceRecipeMixin {
    @Inject(method = "input", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$useCachedInput(CallbackInfoReturnable<Ingredient> callbackInfo) {
        boolean aggressive = JeiOptFeatureFlags.aggressiveCelestialForgeReinforce();
        if (!JeiOptFeatureFlags.cacheCelestialForgeReinforce() && !aggressive) {
            return;
        }
        Ingredient pooled = CelestialForgeReinforceInputPool.getOrCreate(
            this,
            aggressive,
            JeiOptFeatureFlags.aggressiveRepresentativesPerGroup()
        );
        if (pooled != null) {
            callbackInfo.setReturnValue(pooled);
            return;
        }
        if (aggressive) {
            return;
        }
        Ingredient cached = CelestialForgeReinforceCache.getInput(this);
        if (cached != null) {
            callbackInfo.setReturnValue(cached);
        }
    }

    @Inject(method = "input", at = @At("RETURN"), cancellable = true)
    private void jeiOptimize$cacheInput(CallbackInfoReturnable<Ingredient> callbackInfo) {
        Ingredient input = callbackInfo.getReturnValue();
        boolean aggressive = JeiOptFeatureFlags.aggressiveCelestialForgeReinforce();
        if (JeiOptFeatureFlags.cacheCelestialForgeReinforce() && !aggressive) {
            CelestialForgeReinforceCache.putInput(this, input);
        }
        if (aggressive && input != null) {
            input = Ingredient.of(ItemStackRepresentativeSelector.selectFamilies(
                java.util.Arrays.asList(input.getItems()),
                JeiOptFeatureFlags.aggressiveRepresentativesPerGroup()
            ).stream());
            callbackInfo.setReturnValue(input);
        }
    }

    @Inject(method = "result", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$useCachedResult(CallbackInfoReturnable<Ingredient> callbackInfo) {
        if (!JeiOptFeatureFlags.cacheCelestialForgeReinforce()
            || JeiOptFeatureFlags.aggressiveCelestialForgeReinforce()) {
            return;
        }
        Ingredient cached = CelestialForgeReinforceCache.getResult(this);
        if (cached != null) {
            callbackInfo.setReturnValue(cached);
        }
    }

    @Inject(method = "result", at = @At("RETURN"))
    private void jeiOptimize$cacheResult(CallbackInfoReturnable<Ingredient> callbackInfo) {
        if (JeiOptFeatureFlags.cacheCelestialForgeReinforce()
            && !JeiOptFeatureFlags.aggressiveCelestialForgeReinforce()) {
            CelestialForgeReinforceCache.putResult(this, callbackInfo.getReturnValue());
        }
    }
}