package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientTickHookMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void jeiOptimize$drainClientTickQueue(CallbackInfo callbackInfo) {
        JeiOptClientTickQueue.drainForCurrentTick();
    }
}