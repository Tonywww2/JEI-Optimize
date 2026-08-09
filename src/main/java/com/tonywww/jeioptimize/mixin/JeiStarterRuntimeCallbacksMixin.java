package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.api.IModPlugin;
import mezz.jei.library.load.PluginCaller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class JeiStarterRuntimeCallbacksMixin {
    @Redirect(
        method = "start",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/library/load/PluginCaller;callOnPlugins(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V"
        )
    )
    private void jeiOptimize$runRuntimeCallbacksOnMainThread(
        String title,
        List<IModPlugin> plugins,
        Consumer<IModPlugin> callback
    ) {
        Runnable callPlugins = () -> PluginCaller.callOnPlugins(title, plugins, callback);
        if (JeiOptExecutors.isJeiStartThread() && "Sending Runtime".equals(title)) {
            long generation = JeiOptRuntimeState.currentGeneration();
            JeiOptExecutors.awaitJeiStartTask(JeiOptStartupProgressState.publicationFuture(generation));
            JeiOptExecutors.runOnMainThreadAndWait(callPlugins);
        } else {
            callPlugins.run();
        }
    }
}