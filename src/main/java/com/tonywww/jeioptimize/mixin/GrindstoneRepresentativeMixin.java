package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.platform.IPlatformRecipeHelper;
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
        at = @At("HEAD"),
        require = 1
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
            JeiRecipeGenerationLimiter.clearGrindstone();
        }
    }

    //? if forge {
    @Inject(
        method = "getRepairRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lmezz/jei/api/runtime/IIngredientManager;Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
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
    /*@Inject(
        method = "getRepairRecipes(Lmezz/jei/common/platform/IPlatformRecipeHelper;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/stream/Stream;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
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
        at = @At("RETURN"),
        require = 1
    )
    private static void jeiOptimize$endRepresentativeSelection(
        IIngredientManager ingredientManager,
        IPlatformRecipeHelper platformHelper,
        CallbackInfoReturnable<List<?>> callbackInfo
    ) {
        JeiRecipeGenerationLimiter.endGrindstone();
    }

}