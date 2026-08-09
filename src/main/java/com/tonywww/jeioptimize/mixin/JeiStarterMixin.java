package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.JeiOptStartupDriver;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.library.startup.JeiStarter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs JEI's plugin startup on a background thread so the render thread never blocks on it.
 *
 * <p>Each startup has a generation token. Stopping the world cancels the token, interrupts the
 * dedicated startup thread, and prevents a stale runtime from being published.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class JeiStarterMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$startJeiOnBackgroundThread(CallbackInfo ci) {
        if (JeiOptExecutors.isJeiStartThread()) {
            JeiOptExecutors.checkJeiStartActive();
            return;
        }
        if (!JeiOptFeatureFlags.enabled()) {
            return;
        }

        long generation = JeiOptRuntimeState.beginStart();
        Minecraft minecraft = Minecraft.getInstance();
        if (!JeiOptFeatureFlags.asyncStartup() || !minecraft.isSameThread()) {
            return;
        }
        JeiOptStartupProgressState.begin(generation);
        JeiOptimize.LOGGER.info(
                "JEI Optimize: running JEI startup on a background thread; the render thread stays "
                        + "responsive and JEI overlays appear when startup finishes.");
        ci.cancel();
        JeiOptExecutors.runJeiStartAsync(generation, () -> {
            try {
                ((JeiStarter) (Object) this).start();
                JeiOptStartupProgressState.markRuntimeComplete(generation);
            } catch (Throwable t) {
                if (JeiOptExecutors.isJeiStartCancellation(t)
                    || !JeiOptRuntimeState.isCurrent(generation)) {
                    JeiOptimize.LOGGER.info("JEI Optimize stopped a cancelled JEI startup before runtime publication.");
                    JeiOptStartupProgressState.cancel(generation);
                    return;
                }
                JeiOptStartupProgressState.cancel(generation);
                JeiOptimize.LOGGER.error("JEI failed to start on the background thread", t);
                minecraft.execute(() -> {
                    if (JeiOptRuntimeState.isCurrent(generation)) {
                        throw new RuntimeException("JEI failed to start", t);
                    }
                });
            }
        });
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void jeiOptimize$cancelJeiStartup(CallbackInfo ci) {
        JeiOptStartupDriver.onJeiStopping();
    }
}
