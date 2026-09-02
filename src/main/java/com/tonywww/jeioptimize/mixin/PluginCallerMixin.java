package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.instrumentation.JeiOptDiagnostics;
import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import com.tonywww.jeioptimize.integration.SfmFallingAnvilCache;
import com.tonywww.jeioptimize.integration.SfmFallingAnvilRepresentativeLimiter;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import mezz.jei.api.IModPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public abstract class PluginCallerMixin {
    private static final String ALI_PLUGIN_CLASS = "com.yanny.ali.jei.compatibility.JeiCompatibility";
    private static final String REGISTERING_RECIPES = "Registering recipes";

    @Redirect(
        method = "callOnPlugins",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
        ),
        require = 1
    )
    private static void jeiOptimize$timePluginCall(
        Consumer<IModPlugin> consumer,
        Object plugin,
        String title,
        List<IModPlugin> plugins,
        Consumer<IModPlugin> func
    ) {
        JeiOptExecutors.checkJeiStartActive();
        IModPlugin modPlugin = (IModPlugin) plugin;
        boolean isAliPlugin = ALI_PLUGIN_CLASS.equals(modPlugin.getClass().getName());
        Runnable pluginCall = () -> {
            try {
                JeiOptDiagnostics.callPluginWithTiming(title, modPlugin, () ->
                    JeiPluginCallContext.runWithPlugin(modPlugin, () -> consumer.accept(modPlugin)));
            } finally {
                JeiRecipeGenerationLimiter.clearAll();
                SfmFallingAnvilCache.abortCapture();
                SfmFallingAnvilRepresentativeLimiter.clear();
            }
        };
        try {
            if (JeiOptExecutors.isJeiStartThread()) {
                if (isAliPlugin && REGISTERING_RECIPES.equals(title)) {
                    com.tonywww.jeioptimize.JeiOptimize.LOGGER.info(
                        "JEI Optimize waiting for queued ALI client work before recipe registration");
                    JeiOptClientTickQueue.awaitNextClientTick();
                }
                JeiOptExecutors.runOnMainThreadAndWait(pluginCall);
            } else {
                pluginCall.run();
            }
        } finally {
            JeiOptExecutors.checkJeiStartActive();
        }
    }
}