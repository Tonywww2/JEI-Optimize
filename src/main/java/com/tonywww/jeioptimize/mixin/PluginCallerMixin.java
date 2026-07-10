package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.instrumentation.JeiOptDiagnostics;
import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
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
        JeiOptDiagnostics.callPluginWithTiming(title, modPlugin, () ->
            JeiPluginCallContext.runWithPlugin(modPlugin, () -> consumer.accept(modPlugin)));
    }
}