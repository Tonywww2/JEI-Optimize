package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.TooltipSearchAdapter;
import com.tonywww.jeioptimize.runtime.TooltipCaptureContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.ListElementInfo", remap = false)
public abstract class ListElementInfoTooltipCaptureMixin {
    @Inject(
        method = "getTooltipStrings(Lmezz/jei/common/config/IIngredientFilterConfig;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/Set;",
        at = @At("HEAD"), cancellable = true
    )
    private void jeiopt$replayTooltip(IIngredientFilterConfig config, IIngredientManager manager,
        CallbackInfoReturnable<Set<String>> callback) {
        if (JeiOptFeatureFlags.tooltipSearchIndex()) {
            if (TooltipCaptureContext.isCapturing(this)) {
                TooltipCaptureContext.settings(this, TooltipSearchAdapter.advancedSetting(config));
            }
            var replay = TooltipCaptureContext.replayStrings(this);
            if (replay != null && Minecraft.getInstance().isSameThread()) {
                callback.setReturnValue(new java.util.LinkedHashSet<>(replay));
            } else {
                TooltipCaptureContext.beginGetter(this);
            }
        }
    }

    @Inject(
        method = "getTooltipStrings(Lmezz/jei/common/config/IIngredientFilterConfig;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/Set;",
        at = @At("RETURN"), cancellable = true
    )
    private void jeiopt$captureTooltip(
        IIngredientFilterConfig config,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<Set<String>> callback
    ) {
        TooltipCaptureContext.endGetter(this);
        if ((JeiOptFeatureFlags.tooltipSearchMetrics() || JeiOptFeatureFlags.tooltipSearchIndex())
            && TooltipCaptureContext.isCapturing(this)) {
            if (!Minecraft.getInstance().isSameThread()) {
                TooltipCaptureContext.fail(this);
                return;
            }
            TooltipCaptureContext.record(this, callback.getReturnValue());
            if (TooltipCaptureContext.suppresses(this)) {
                callback.setReturnValue(Set.of());
            }
        }
    }
}