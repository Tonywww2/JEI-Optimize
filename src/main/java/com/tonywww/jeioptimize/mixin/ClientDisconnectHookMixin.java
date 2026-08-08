package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.integration.JeiOptStartupDriver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientDisconnectHookMixin {
    //? if forge {
    @Inject(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At("HEAD"))
    private void jeiOptimize$cancelStartupOnDisconnect(Screen screen, CallbackInfo callbackInfo) {
        JeiOptStartupDriver.onJeiStopping();
    }
    //?} else {
    /*@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void jeiOptimize$cancelStartupOnDisconnect(
        Screen screen,
        boolean transferring,
        CallbackInfo callbackInfo
    ) {
        JeiOptStartupDriver.onJeiStopping();
    }
    *///?}
}