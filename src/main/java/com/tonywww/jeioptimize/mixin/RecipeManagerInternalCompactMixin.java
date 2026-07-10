package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class RecipeManagerInternalCompactMixin {
    private static final ThreadLocal<Boolean> JEI_OPTIMIZE_RUNNING_DELAYED_COMPACT = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void compact();

    @Inject(method = "compact", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$delayCompact(CallbackInfo callbackInfo) {
        if (!JeiOptFeatureFlags.delayCompact() || JEI_OPTIMIZE_RUNNING_DELAYED_COMPACT.get()) {
            return;
        }

        callbackInfo.cancel();
        JeiOptExecutors.runAsync(this::jeiOptimize$runCompact);
    }

    private void jeiOptimize$runCompact() {
        JEI_OPTIMIZE_RUNNING_DELAYED_COMPACT.set(true);
        try {
            compact();
        } finally {
            JEI_OPTIMIZE_RUNNING_DELAYED_COMPACT.remove();
        }
    }
}