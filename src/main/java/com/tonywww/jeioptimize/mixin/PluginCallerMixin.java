package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.instrumentation.JeiOptDiagnostics;
import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptIncompatPluginStore;
import mezz.jei.api.IModPlugin;
import mezz.jei.library.plugins.vanilla.VanillaPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.PluginCaller", remap = false)
public abstract class PluginCallerMixin {
    private static final Set<String> ASYNC_PHASES = Set.of(
            "Registering item subtypes",
            "Registering fluid subtypes",
            "Registering ingredients",
            "Registering extra ingredients",
            "Registering search ingredient aliases",
            "Registering Mod Info",
            "Registering categories",
            "Registering recipe catalysts",
            "Registering advanced plugins",
            "Registering recipes",
            "Registering recipes transfer handlers",
            "Registering gui handlers"
    );

    @Redirect(
            method = "callOnPlugins",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
    )
    private static void jeiOptimize$timePluginCall(
            Consumer<IModPlugin> consumer,
            Object plugin,
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> func
    ) {
        IModPlugin modPlugin = (IModPlugin) plugin;
        Runnable call = pluginCall(consumer, modPlugin, title);
        boolean offThread = JeiOptExecutors.isJeiStartOffThread();
        boolean dispatchAsync = JeiOptFeatureFlags.parallelPluginCalls()
                && ASYNC_PHASES.contains(title)
                && !JeiOptIncompatPluginStore.isExcluded(pluginUid(modPlugin));
        if (dispatchAsync) {
            int order = plugins.indexOf(modPlugin);
            JeiOptExecutors.runPluginCallAsync(() -> {
                try {
                    call.run();
                } catch (RuntimeException | LinkageError e) {
                    JeiOptIncompatPluginStore.record(title, order, modPlugin, call);
                    JeiOptimize.LOGGER.warn(
                            "Plugin {} is not compatible with parallel dispatch: it threw {} during phase '{}'. "
                                    + "It will be re-run on the main thread.",
                            modPlugin.getClass(), e, title);
                }
            });
            return;
        }
        if (!offThread) {
            call.run();
            return;
        }
        if (JeiOptIncompatPluginStore.isExcluded(pluginUid(modPlugin))) {
            JeiOptExecutors.runOnMainThreadAndWait(call);
            return;
        }
        int order = plugins.indexOf(modPlugin);
        try {
            call.run();
        } catch (RuntimeException | LinkageError e) {
            JeiOptIncompatPluginStore.record(title, order, modPlugin, call);
            JeiOptimize.LOGGER.warn(
                    "Plugin {} threw {} while JEI startup ran on a background thread during phase '{}'. "
                            + "It will be re-run on the main thread.",
                    modPlugin.getClass(), e, title);
        }
    }

    private static Runnable pluginCall(Consumer<IModPlugin> consumer, IModPlugin modPlugin, String title) {
        return () -> JeiOptDiagnostics.callPluginWithTiming(title, modPlugin, () ->
                JeiPluginCallContext.runWithPlugin(modPlugin, () -> consumer.accept(modPlugin)));
    }

    @Inject(method = "callOnPlugins", at = @At("RETURN"))
    private static void jeiOptimize$awaitPluginCalls(
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> func,
            CallbackInfo ci
    ) {
        long startTime = System.nanoTime();
        JeiOptExecutors.awaitPendingPluginCalls();
        List<JeiOptIncompatPluginStore.Entry> retries = JeiOptIncompatPluginStore.drain();
        if (!retries.isEmpty()) {
            JeiOptimize.LOGGER.info(
                    "Re-running {} JEI plugin call(s) on the main thread after a parallel dispatch failure.",
                    retries.size());
            for (JeiOptIncompatPluginStore.Entry entry : retries) {
                retryOnMainThread(entry);
            }
        }
        JeiOptDiagnostics.reportPhaseBarrier(title, System.nanoTime() - startTime);
    }

    private static void retryOnMainThread(JeiOptIncompatPluginStore.Entry entry) {
        try {
            JeiOptExecutors.runOnMainThreadAndWait(entry.call());
            JeiOptimize.LOGGER.info(
                    "Plugin {} succeeded when re-run on the main thread during phase '{}'.",
                    entry.plugin().getClass(), entry.phase());
        } catch (RuntimeException | LinkageError e) {
            if (entry.plugin() instanceof VanillaPlugin) {
                throw e;
            }
            JeiOptIncompatPluginStore.learnIncompatible(pluginUid(entry.plugin()));
            JeiOptimize.LOGGER.error("Caught an error from mod plugin: {} {}", entry.plugin().getClass(), pluginUid(entry.plugin()), e);
        }
    }

    private static String pluginUid(IModPlugin plugin) {
        return plugin.getPluginUid().toString();
    }
}