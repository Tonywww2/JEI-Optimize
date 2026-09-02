package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.SfmFallingAnvilCache;
import com.tonywww.jeioptimize.integration.SfmFallingAnvilRepresentativeLimiter;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "ca.teamdman.sfm.client.jei.FallingAnvilJEICategory", remap = false)
public abstract class SfmFallingAnvilCategoryMixin {
    @Inject(
        method = "setRecipeForFallingAnvilDisenchantRecipe",
        at = @At("HEAD"),
        cancellable = true,
        require = 1
    )
    private static void jeiOptimize$useCachedDisenchantmentLayout(
        IRecipeLayoutBuilder builder,
        IFocusGroup focuses,
        List<ItemStack> anvils,
        CallbackInfo callbackInfo
    ) {
        boolean aggressive = JeiOptFeatureFlags.aggressiveSfmFallingAnvil() && focuses.isEmpty();
        boolean cacheable = JeiOptFeatureFlags.cacheSfmFallingAnvil() && focuses.isEmpty() && !aggressive;
        if (cacheable && SfmFallingAnvilCache.addCachedLayout(builder)) {
            callbackInfo.cancel();
            return;
        }
        SfmFallingAnvilRepresentativeLimiter.begin(
            aggressive,
            JeiOptFeatureFlags.aggressiveRepresentativesPerGroup()
        );
        SfmFallingAnvilCache.beginCapture(cacheable);
    }

    @WrapOperation(
        method = "setRecipeForFallingAnvilDisenchantRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lca/teamdman/sfm/common/enchantment/SFMEnchantmentKey;canEnchant(Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private static boolean jeiOptimize$limitToolRepresentatives(
        @org.spongepowered.asm.mixin.injection.Coerce Object enchantment,
        ItemStack stack,
        Operation<Boolean> original
    ) {
        return SfmFallingAnvilRepresentativeLimiter.shouldKeep(
            enchantment,
            stack,
            original.call(enchantment, stack)
        );
    }

    @WrapOperation(
        method = "setRecipeForFallingAnvilDisenchantRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/api/gui/builder/IRecipeSlotBuilder;addItemStacks(Ljava/util/List;)Lmezz/jei/api/gui/builder/IIngredientAcceptor;"
        )
    )
    private static IIngredientAcceptor<?> jeiOptimize$captureDisplayList(
        IRecipeSlotBuilder slot,
        List<ItemStack> stacks,
        Operation<IIngredientAcceptor<?>> original
    ) {
        SfmFallingAnvilCache.capture(stacks);
        return original.call(slot, stacks);
    }

    @Inject(
        method = "setRecipeForFallingAnvilDisenchantRecipe",
        at = @At("RETURN"),
        require = 1
    )
    private static void jeiOptimize$finishCapture(
        IRecipeLayoutBuilder builder,
        IFocusGroup focuses,
        List<ItemStack> anvils,
        CallbackInfo callbackInfo
    ) {
        SfmFallingAnvilCache.endCapture();
        SfmFallingAnvilRepresentativeLimiter.end();
    }

}