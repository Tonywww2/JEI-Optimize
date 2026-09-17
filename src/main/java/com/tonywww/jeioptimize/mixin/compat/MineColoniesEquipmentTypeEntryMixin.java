package com.tonywww.jeioptimize.mixin.compat;

import com.tonywww.jeioptimize.integration.MineColoniesJeiToolScanCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "com.minecolonies.api.equipment.registry.EquipmentTypeEntry",
    remap = false,
    priority = 500
)
public abstract class MineColoniesEquipmentTypeEntryMixin {
    @Inject(
        method = "checkIsEquipment(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$checkEquipment(
        ItemStack stack,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        Boolean cached = MineColoniesJeiToolScanCache.getEquipment(this, stack);
        if (cached != null) {
            callbackInfo.setReturnValue(cached);
        }
    }

    @Inject(
        method = "checkIsEquipment(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("RETURN"),
        require = 1
    )
    private void jeiOptimize$cacheEquipment(
        ItemStack stack,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        MineColoniesJeiToolScanCache.putEquipment(this, stack, callbackInfo.getReturnValue());
    }
}