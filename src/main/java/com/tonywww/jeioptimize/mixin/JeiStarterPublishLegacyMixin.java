package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class JeiStarterPublishLegacyMixin {
    @Redirect(
        method = "start",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/common/Internal;setRuntime(Lmezz/jei/api/runtime/IJeiRuntime;)V"
        )
    )
    private void jeiOptimize$publishRuntimeOnMainThread(IJeiRuntime runtime) {
        if (!JeiOptExecutors.isJeiStartThread()) {
            Internal.setRuntime(runtime);
            return;
        }
        JeiOptExecutors.runOnMainThreadAndWait(() -> Internal.setRuntime(runtime));
    }
}