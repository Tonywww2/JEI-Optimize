package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap;
import com.tonywww.jeioptimize.instrumentation.JeiOptBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientTickHookMixin {
    @Unique
    private long jeiOptimize$tickStarted;

    @Inject(method = "tick", at = @At("HEAD"))
    private void jeiOptimize$startTickTiming(CallbackInfo callbackInfo) {
        jeiOptimize$tickStarted = System.nanoTime();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void jeiOptimize$drainClientTickQueue(CallbackInfo callbackInfo) {
        JeiOptClientTickQueue.drainForCurrentTick();
        JeiOptFilterBootstrap.clientTickFinished(jeiOptimize$tickStarted);
        JeiOptBenchmark.tick(jeiOptimize$tickStarted);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void jeiOptimize$measureFrame(boolean renderLevel, CallbackInfo callbackInfo) {
        JeiOptBenchmark.frame();
    }
}