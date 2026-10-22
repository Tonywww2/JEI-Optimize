package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import com.tonywww.jeioptimize.mixin.accessor.JeiRuntimeAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI 19.57 renamed the overlay draw callbacks. This mixin applies only when the complete
 * modern screen-render ABI is present, so a partial match cannot leave a layout path unguarded.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.events.GuiEventHandler", remap = false)
public abstract class JeiGuiRenderGuardModernMixin {
    @Inject(
        method = "onGuiInit(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$skipJeiInitUntilRuntimeReady(
        Screen screen,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "onGuiOpen(Lnet/minecraft/client/gui/screens/Screen;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$skipJeiOpenUntilRuntimeReady(
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
        cancellable = true,
        require = 1
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
        method = "drawForScreenBackground(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$skipJeiBackgroundUntilRuntimeReady(
        Screen screen,
        GuiGraphics guiGraphics,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiRendering(JeiRuntimeAccessor.jeiopt$getNullableRuntime() != null)) {
            callbackInfo.cancel();
        }
    }

    @Inject(
        method = "drawForScreenForeground(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;II)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$skipJeiForegroundUntilRuntimeReady(
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
