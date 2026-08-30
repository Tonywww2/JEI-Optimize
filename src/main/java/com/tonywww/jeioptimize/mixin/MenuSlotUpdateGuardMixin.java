package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.recipe.MenuUpdateSuppressor;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class MenuSlotUpdateGuardMixin {
    private MenuSlotUpdateGuardMixin() {
    }

    @Mixin(ItemCombinerMenu.class)
    public abstract static class ItemCombiner {
        @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
        private void jeiOptimize$skipIntermediateResult(Container container, CallbackInfo callbackInfo) {
            if (MenuUpdateSuppressor.isSuppressed(this)) {
                callbackInfo.cancel();
            }
        }
    }

    @Mixin(GrindstoneMenu.class)
    public abstract static class Grindstone {
        @Inject(method = "slotsChanged", at = @At("HEAD"), cancellable = true)
        private void jeiOptimize$skipIntermediateResult(Container container, CallbackInfo callbackInfo) {
            if (MenuUpdateSuppressor.isSuppressed(this)) {
                callbackInfo.cancel();
            }
        }
    }
}