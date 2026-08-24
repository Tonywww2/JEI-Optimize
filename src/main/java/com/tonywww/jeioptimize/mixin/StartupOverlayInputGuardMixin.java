package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class StartupOverlayInputGuardMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$blockOverlayClicksUntilRuntimeReady(
        long windowHandle,
        int button,
        int action,
        int modifiers,
        CallbackInfo callbackInfo
    ) {
        if (!JeiOptStartupProgressState.blocksJeiInput()) {
            return;
        }

        Screen screen = minecraft.screen;
        if (screen instanceof AbstractContainerScreen<?>) {
            callbackInfo.cancel();
        }
    }
}