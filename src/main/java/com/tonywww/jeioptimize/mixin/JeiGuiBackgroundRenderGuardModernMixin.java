package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiGuiBackgroundRenderGuardModernMixin {
    @Inject(
        method = "onDrawBackgroundPost(Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$skipJeiBackgroundUntilRuntimeReady(
        GuiGraphics guiGraphics,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.cancel();
        }
    }
}