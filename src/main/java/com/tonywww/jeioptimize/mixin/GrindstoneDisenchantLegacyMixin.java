package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import mezz.jei.common.platform.IPlatformRecipeHelper;
//? if neoforge {
/*import net.minecraft.core.Holder;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker", remap = false)
public abstract class GrindstoneDisenchantLegacyMixin {
    //? if forge {
    @WrapOperation(
        method = "getDisenchantRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/common/platform/IPlatformRecipeHelper;isItemEnchantable(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        ),
        require = 1
    )
    private static boolean jeiOptimize$limitDisenchantmentItems(
        IPlatformRecipeHelper platformHelper,
        ItemStack stack,
        Enchantment enchantment,
        Operation<Boolean> original
    ) {
        return original.call(platformHelper, stack, enchantment)
            && JeiRecipeGenerationLimiter.shouldKeepGrindstone(enchantment, stack);
    }
    //?} else {
    /*@WrapOperation(
        method = "getDisenchantRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/stream/Stream;",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/common/platform/IPlatformRecipeHelper;isItemEnchantable(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;)Z"
        ),
        require = 1
    )
    private static boolean jeiOptimize$limitDisenchantmentItems(
        IPlatformRecipeHelper platformHelper,
        ItemStack stack,
        Holder<Enchantment> enchantment,
        Operation<Boolean> original
    ) {
        return original.call(platformHelper, stack, enchantment)
            && JeiRecipeGenerationLimiter.shouldKeepGrindstone(enchantment, stack);
    }
    *///?}
}