package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.gui.input.UserInput;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.input.ClientInputHandler", remap = false)
public abstract class JeiClientInputGuardMixin {
    @Inject(
        method = {
            "onKeyboardKeyPressedPre(Lnet/minecraft/client/gui/screens/Screen;Lmezz/jei/gui/input/UserInput;)Z",
            "onKeyboardKeyPressedPost(Lnet/minecraft/client/gui/screens/Screen;Lmezz/jei/gui/input/UserInput;)Z",
            "onGuiMouseClicked(Lnet/minecraft/client/gui/screens/Screen;Lmezz/jei/gui/input/UserInput;)Z",
            "onGuiMouseReleased(Lnet/minecraft/client/gui/screens/Screen;Lmezz/jei/gui/input/UserInput;)Z"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$ignoreUserInputUntilRuntimeReady(
        Screen screen,
        UserInput input,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
        method = "onKeyboardCharTypedPre(Lnet/minecraft/client/gui/screens/Screen;CI)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$ignoreCharacterInputUntilRuntimeReady(
        Screen screen,
        char codePoint,
        int modifiers,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
        method = "onKeyboardCharTypedPost(Lnet/minecraft/client/gui/screens/Screen;CI)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$ignoreCharacterInputPostUntilRuntimeReady(
        Screen screen,
        char codePoint,
        int modifiers,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.cancel();
        }
    }

    //? if forge {
    @Inject(method = "onGuiMouseScroll(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$ignoreMouseScrollUntilRuntimeReady(
        double mouseX,
        double mouseY,
        double scrollDelta,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.setReturnValue(false);
        }
    }
    //?} else {
    /*@Inject(method = "onGuiMouseScroll(DDDD)Z", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$ignoreMouseScrollUntilRuntimeReady(
        double mouseX,
        double mouseY,
        double horizontalDelta,
        double verticalDelta,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.setReturnValue(false);
        }
    }
    *///?}

    @Inject(
        method = "onGuiMouseDragged(Lnet/minecraft/client/gui/screens/Screen;DDIDD)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void jeiOptimize$ignoreMouseDragUntilRuntimeReady(
        Screen screen,
        double mouseX,
        double mouseY,
        int button,
        double dragX,
        double dragY,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.setReturnValue(false);
        }
    }
}