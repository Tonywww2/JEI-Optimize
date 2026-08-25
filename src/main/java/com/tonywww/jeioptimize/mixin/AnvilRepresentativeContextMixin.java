package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import mezz.jei.api.runtime.IIngredientManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.anvil.AnvilRecipeMaker", remap = false)
public abstract class AnvilRepresentativeContextMixin {
    @Inject(
        method = "getAnvilRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;",
        at = @At("HEAD")
    )
    private static void jeiOptimize$beginRepresentativeSelection(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<List<?>> callbackInfo
    ) {
        if (JeiOptFeatureFlags.optimizeAnvilRepresentatives()
            && !JeiOptFeatureFlags.disableAnvilEnchantRecipes()) {
            JeiRecipeGenerationLimiter.beginAnvil(
                ingredientManager,
                JeiOptFeatureFlags.anvilRepresentativesPerEnchantment()
            );
        } else {
            JeiRecipeGenerationLimiter.endAnvil();
        }
    }

    @Inject(
        method = "getAnvilRecipes(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lmezz/jei/api/runtime/IIngredientManager;)Ljava/util/List;",
        at = @At("RETURN")
    )
    private static void jeiOptimize$endRepresentativeSelection(
        IVanillaRecipeFactory vanillaRecipeFactory,
        IIngredientManager ingredientManager,
        CallbackInfoReturnable<List<?>> callbackInfo
    ) {
        JeiRecipeGenerationLimiter.endAnvil();
    }
}