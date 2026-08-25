package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformRecipeHelper;
//? if neoforge {
/*import net.minecraft.core.Holder;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
//? if forge {
import net.minecraft.world.inventory.GrindstoneMenu;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.grindstone.GrindstoneRecipeMaker", remap = false)
public abstract class GrindstoneRepresentativeMixin {
    @Inject(
        method = "getGrindstoneRecipes(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/List;",
        at = @At("HEAD")
    )
    private static void jeiOptimize$beginRepresentativeSelection(
        IIngredientManager ingredientManager,
        IPlatformRecipeHelper platformHelper,
        CallbackInfoReturnable<List<?>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.optimizeGrindstoneRepresentatives()) {
            JeiRecipeGenerationLimiter.beginGrindstone(
                ingredientManager,
                JeiOptFeatureFlags.grindstoneRepresentativesPerEnchantment()
            );
        } else {
            JeiRecipeGenerationLimiter.endGrindstone();
        }
    }

    //? if forge {
    @WrapOperation(
        method = "getDisenchantRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/common/platform/IPlatformRecipeHelper;isItemEnchantable(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        )
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

    @Inject(
        method = "getRepairRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lmezz/jei/api/runtime/IIngredientManager;Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void jeiOptimize$limitRepairExamples(
        IPlatformRecipeHelper platformHelper,
        IIngredientManager ingredientManager,
        GrindstoneMenu grindstoneMenu,
        CallbackInfoReturnable<Stream<?>> callbackInfo
    ) {
        jeiOptimize$limitRepairExamples(callbackInfo);
    }
    //?} else {
    /*@WrapOperation(
        method = "getDisenchantRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/stream/Stream;",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/common/platform/IPlatformRecipeHelper;isItemEnchantable(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;)Z"
        )
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

    @Inject(
        method = "getRepairRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/stream/Stream;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void jeiOptimize$limitRepairExamples(
        IPlatformRecipeHelper platformHelper,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<Stream<?>> callbackInfo
    ) {
        jeiOptimize$limitRepairExamples(callbackInfo);
    }
    *///?}

    private static void jeiOptimize$limitRepairExamples(CallbackInfoReturnable<Stream<?>> callbackInfo) {
        if (JeiOptFeatureFlags.optimizeGrindstoneRepresentatives() && callbackInfo.getReturnValue() != null) {
            callbackInfo.setReturnValue(
                callbackInfo.getReturnValue().limit(JeiOptFeatureFlags.grindstoneRepairRepresentatives())
            );
        }
    }

    @Inject(
        method = "getGrindstoneRecipes(Lmezz/jei/api/runtime/IIngredientManager;Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/List;",
        at = @At("RETURN")
    )
    private static void jeiOptimize$endRepresentativeSelection(
        IIngredientManager ingredientManager,
        IPlatformRecipeHelper platformHelper,
        CallbackInfoReturnable<List<?>> callbackInfo
    ) {
        JeiRecipeGenerationLimiter.endGrindstone();
    }
}