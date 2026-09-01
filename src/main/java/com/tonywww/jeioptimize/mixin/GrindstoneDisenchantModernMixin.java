package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import mezz.jei.common.platform.IPlatformRecipeHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker", remap = false)
public abstract class GrindstoneDisenchantModernMixin {
    @Inject(
        method = "canEnchant(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/resources/ResourceLocation;)Z",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
    )
    private static void jeiOptimize$limitDisenchantmentItems(
        IPlatformRecipeHelper platformHelper,
        ItemStack stack,
        Enchantment enchantment,
        ResourceLocation enchantmentId,
        CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (JeiOptFeatureFlags.optimizeGrindstoneRepresentatives()
            && Boolean.TRUE.equals(callbackInfo.getReturnValue())) {
            callbackInfo.setReturnValue(
                JeiRecipeGenerationLimiter.shouldKeepGrindstone(enchantment, stack)
            );
        }
    }
}