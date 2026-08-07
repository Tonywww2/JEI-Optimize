package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import mezz.jei.library.startup.JeiStarter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class JeiStarterPublishModernMixin {
    @Shadow(remap = false)
    private boolean running;

    @Unique
    private IJeiRuntime jeiOptimize$pendingRuntime;

    @Redirect(
        method = "start",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/common/Internal;setRuntime(Lmezz/jei/api/runtime/IJeiRuntime;)V"
        )
    )
    private void jeiOptimize$captureRuntime(IJeiRuntime runtime) {
        if (!JeiOptExecutors.isJeiStartThread()) {
            Internal.setRuntime(runtime);
            return;
        }
        JeiOptExecutors.checkJeiStartActive();
        jeiOptimize$pendingRuntime = runtime;
    }

    @Redirect(
        method = "start",
        at = @At(
            value = "FIELD",
            target = "Lmezz/jei/library/startup/JeiStarter;running:Z",
            opcode = Opcodes.PUTFIELD
        )
    )
    private void jeiOptimize$publishRuntimeAndMarkRunning(JeiStarter instance, boolean value) {
        if (!value || !JeiOptExecutors.isJeiStartThread()) {
            running = value;
            return;
        }

        JeiOptExecutors.checkJeiStartActive();
        IJeiRuntime runtime = jeiOptimize$pendingRuntime;
        if (runtime == null) {
            JeiOptExecutors.checkJeiStartActive();
            throw new IllegalStateException("JEI runtime publication was not captured");
        }
        jeiOptimize$pendingRuntime = null;
        JeiOptExecutors.publishJeiRuntimeOnMainThreadAndWait(() -> {
            Internal.setRuntime(runtime);
            running = true;
        });
    }

}