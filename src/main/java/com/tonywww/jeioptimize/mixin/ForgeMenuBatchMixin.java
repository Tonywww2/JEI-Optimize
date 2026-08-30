package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.MenuUpdateSuppressor;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class ForgeMenuBatchMixin {
    private ForgeMenuBatchMixin() {
    }

    @Pseudo
    @Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilHelper", remap = false)
    public abstract static class Anvil {
        @WrapOperation(
            method = "setAnvilMenu",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/inventory/Slot;set(Lnet/minecraft/world/item/ItemStack;)V"
            )
        )
        private static void jeiOptimize$setInputQuietly(
            Slot slot,
            ItemStack stack,
            Operation<Void> original,
            @Local(argsOnly = true) AnvilMenu menu
        ) {
            if (!JeiOptFeatureFlags.skipRedundantMenuUpdates()) {
                original.call(slot, stack);
                return;
            }
            try (MenuUpdateSuppressor.Scope ignored = MenuUpdateSuppressor.suppress(menu)) {
                original.call(slot, stack);
            }
        }

        @Inject(method = "setAnvilMenu", at = @At("RETURN"))
        private static void jeiOptimize$computeFinalResult(
            AnvilMenu menu,
            ItemStack left,
            ItemStack right,
            CallbackInfoReturnable<AnvilMenu> callbackInfo
        ) {
            if (JeiOptFeatureFlags.skipRedundantMenuUpdates() && callbackInfo.getReturnValue() != null) {
                menu.createResult();
            }
        }
    }

    @Pseudo
    @Mixin(targets = "mezz.jei.forge.platform.RecipeHelper", remap = false)
    public abstract static class Grindstone {
        @WrapOperation(
            method = "getGrindstoneResult",
            at = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/inventory/Slot;set(Lnet/minecraft/world/item/ItemStack;)V"
            )
        )
        private void jeiOptimize$setInputQuietly(
            Slot slot,
            ItemStack stack,
            Operation<Void> original,
            @Local(argsOnly = true) GrindstoneMenu menu
        ) {
            if (!JeiOptFeatureFlags.skipRedundantMenuUpdates()) {
                original.call(slot, stack);
                return;
            }
            try (MenuUpdateSuppressor.Scope ignored = MenuUpdateSuppressor.suppress(menu)) {
                original.call(slot, stack);
            }
        }
    }
}