package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.integration.MineColoniesJeiToolScanCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "steve_gall.minecolonies_tweaks.api.common.tool.ToolTypeExtension", remap = false)
public abstract class MineColoniesTweaksToolTypeExtensionMixin {
    @Inject(
        method = "isCustomTool(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$skipEmptyCustomToolRules(
        ItemStack stack,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (MineColoniesJeiToolScanCache.shouldBypassEmptyTweaksRules()) {
            callbackInfo.setReturnValue(false);
        }
    }

    @Inject(
        method = "getCustomLevel(Lnet/minecraft/world/item/ItemStack;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$skipEmptyCustomLevelRules(
        ItemStack stack,
        CallbackInfoReturnable<Integer> callbackInfo
    ) {
        if (MineColoniesJeiToolScanCache.shouldBypassEmptyTweaksRules()) {
            callbackInfo.setReturnValue(-1);
        }
    }
}