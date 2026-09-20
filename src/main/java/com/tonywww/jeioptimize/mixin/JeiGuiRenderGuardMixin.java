package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import com.tonywww.jeioptimize.mixin.accessor.JeiRuntimeAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiGuiRenderGuardMixin {
    @Inject(
        method = {
            "onGuiInit(Lnet/minecraft/client/gui/screens/Screen;)V",
            "onGuiOpen(Lnet/minecraft/client/gui/screens/Screen;)V"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$skipJeiLayoutUntilRuntimeReady(
        Screen screen,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "updateForScreenRender(Lnet/minecraft/client/gui/screens/Screen;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$skipJeiRenderPreparationUntilRuntimeReady(
        Screen screen,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "onDrawForeground(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lnet/minecraft/client/gui/GuiGraphics;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$skipJeiForegroundUntilRuntimeReady(
        AbstractContainerScreen<?> screen,
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "onDrawScreenPost(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;II)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$skipJeiRenderingUntilRuntimeReady(
        Screen screen,
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            callbackInfo.cancel();
        }
    }
}