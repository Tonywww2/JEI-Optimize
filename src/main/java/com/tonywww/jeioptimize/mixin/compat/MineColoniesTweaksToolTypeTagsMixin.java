package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.integration.MineColoniesJeiToolScanCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "steve_gall.minecolonies_tweaks.api.common.tool.ToolTypeTags", remap = false)
public abstract class MineColoniesTweaksToolTypeTagsMixin {
    @Inject(
        method = "isInBlacklist(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private static void jeiOptimize$skipEmptyBlacklistRules(
        ItemStack stack,
        ResourceLocation toolTypeId,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (MineColoniesJeiToolScanCache.shouldBypassEmptyTweaksRules()) {
            callbackInfo.setReturnValue(false);
        }
    }
}