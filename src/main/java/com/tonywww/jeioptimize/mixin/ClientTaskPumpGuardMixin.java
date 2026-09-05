package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.instrumentation.JeiClientTaskPumpDiagnostics;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockableEventLoop.class)
public abstract class ClientTaskPumpGuardMixin {
    @Inject(method = "pollTask", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$blockOffMainClientTaskPump(CallbackInfoReturnable<Boolean> callbackInfo) {
        if (JeiOptExecutors.isJeiStartThread() && (Object) this == Minecraft.getInstance()) {
            JeiClientTaskPumpDiagnostics.onBlockedClientTaskPump();
            callbackInfo.setReturnValue(false);
        }
    }
}