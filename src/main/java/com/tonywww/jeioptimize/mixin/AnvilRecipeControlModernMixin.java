package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
public abstract class AnvilRecipeControlModernMixin {
    @Inject(
        method = "getRepairRecipes()Ljava/util/stream/Stream;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiopt$maybeSkipRepairRecipes(CallbackInfoReturnable<Stream<?>> callbackInfo) {
        if (JeiOptFeatureFlags.disableAnvilRepairRecipes()) {
            callbackInfo.setReturnValue(Stream.empty());
        }
    }

    @Inject(
        method = "getBookEnchantmentRecipes()Ljava/util/stream/Stream;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiopt$maybeSkipEnchantRecipes(CallbackInfoReturnable<Stream<?>> callbackInfo) {
        if (JeiOptFeatureFlags.disableAnvilEnchantRecipes()) {
            callbackInfo.setReturnValue(Stream.empty());
        }
    }
}