package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftProfilerGuardMixin {
    @Inject(method = "getProfiler", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$useIsolatedProfilerForJeiStartup(
        CallbackInfoReturnable<ProfilerFiller> callbackInfo
    ) {
        if (JeiOptExecutors.isJeiStartThread()) {
            callbackInfo.setReturnValue(InactiveProfiler.INSTANCE);
        }
    }
}