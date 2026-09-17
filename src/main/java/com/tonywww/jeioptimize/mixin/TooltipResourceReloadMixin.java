package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.startup.ResourceReloadHandler", remap = false)
public abstract class TooltipResourceReloadMixin {
    @Inject(method = {
        "onResourceManagerReload(Lnet/minecraft/server/packs/resources/ResourceManager;)V",
        "m_6213_(Lnet/minecraft/server/packs/resources/ResourceManager;)V"
    }, at = @At("HEAD"))
    private void jeiopt$invalidateTooltipBuild(CallbackInfo callback) {
        if (JeiOptFeatureFlags.tooltipSearchIndex()) {
            JeiOptFilterBootstrap.resourcesChanged();
        }
    }
}