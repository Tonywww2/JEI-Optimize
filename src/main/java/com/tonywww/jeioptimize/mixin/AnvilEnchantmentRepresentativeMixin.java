package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker$EnchantmentData", remap = false)
public abstract class AnvilEnchantmentRepresentativeMixin {
    @Inject(
        method = "canEnchant(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private void jeiOptimize$limitCompatibleItems(
        ItemStack ingredient,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (Boolean.TRUE.equals(callbackInfo.getReturnValue())
            && !JeiRecipeGenerationLimiter.shouldKeepAnvil(this, ingredient)) {
            callbackInfo.setReturnValue(false);
        }
    }
}