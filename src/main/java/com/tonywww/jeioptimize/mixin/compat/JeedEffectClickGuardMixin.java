package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.jeed.plugin.jei.JEIPlugin", remap = false)
public abstract class JeedEffectClickGuardMixin {
    @Shadow(remap = false)
    public static IJeiHelpers JEI_HELPERS;

    @Shadow(remap = false)
    public static IJeiRuntime JEI_RUNTIME;

    @Inject(
        method = "onClickedEffect(Lnet/minecraft/world/effect/MobEffectInstance;DDI)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$ignoreEffectClickUntilRuntimeReady(
        MobEffectInstance effect,
        double mouseX,
        double mouseY,
        int button,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptFeatureFlags.enabled()
            && (JeiOptStartupProgressState.blocksJeiInput()
                || JEI_HELPERS == null
                || JEI_RUNTIME == null)) {
            callbackInfo.cancel();
        }
    }
}