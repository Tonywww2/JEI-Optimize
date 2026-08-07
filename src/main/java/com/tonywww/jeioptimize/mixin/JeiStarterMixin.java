package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
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
 * <p>JEI itself never asserts a main thread during startup, and the GUI-side handlers JEI installs
 * (overlay rendering, input handling) are only registered once the runtime is sent inside the last
 * plugin calls, so while the background startup runs the render thread is free and JEI's own code
 * cannot touch half-built state. Plugins that do need the main thread are detected by
 * {@link PluginCallerMixin} and re-run there before JEI consumes each phase.
 */
@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class JeiStarterMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void jeiOptimize$startJeiOnBackgroundThread(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!JeiOptFeatureFlags.asyncStartup() || !minecraft.isSameThread()) {
            return;
        }
        JeiOptimize.LOGGER.info(
                "JEI Optimize: running JEI startup on a background thread; the render thread stays "
                        + "responsive and JEI overlays appear when startup finishes.");
        ci.cancel();
        JeiOptExecutors.runJeiStartAsync(() -> {
            try {
                ((JeiStarter) (Object) this).start();
            } catch (Throwable t) {
                JeiOptimize.LOGGER.error("JEI failed to start on the background thread", t);
                minecraft.execute(() -> {
                    throw new RuntimeException("JEI failed to start", t);
                });
            }
        });
    }
}
