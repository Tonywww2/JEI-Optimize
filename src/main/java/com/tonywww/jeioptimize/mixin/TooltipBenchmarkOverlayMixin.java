package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.instrumentation.JeiOptBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class TooltipBenchmarkOverlayMixin {
    @Inject(method = "drawScreen(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
    private void jeiopt$recordSidebar(CallbackInfo callback) {
        JeiOptBenchmark.sidebarDrawn();
    }
}