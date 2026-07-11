package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI integration point. JEI discovers this automatically via the {@link JeiPlugin} annotation.
 *
 * <p>This plugin performs no registration work; it only uses JEI's runtime lifecycle callbacks as
 * a reliable post-startup signal to drive project-owned async preheating.
 */
@JeiPlugin
public final class JeiOptJeiPlugin implements IModPlugin {
    //? if forge {
    private static final ResourceLocation PLUGIN_UID = new ResourceLocation(JeiOptimize.MOD_ID, "core");
    //?} else {
    /*private static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(JeiOptimize.MOD_ID, "core");
    *///?}

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiOptStartupDriver.onRuntimeAvailable();
    }

    @Override
    public void onRuntimeUnavailable() {
        JeiOptStartupDriver.onRuntimeUnavailable();
    }
}
