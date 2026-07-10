package com.tonywww.jeioptimize.mixin.registration;

import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import mezz.jei.api.recipe.category.IRecipeCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.RecipeCategoryRegistration", remap = false)
public abstract class RecipeCategoryRegistrationCountMixin {
    @Inject(method = "addRecipeCategories", at = @At("HEAD"))
    private void jeiopt$countRecipeCategories(IRecipeCategory<?>[] recipeCategories, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.RECIPE_CATEGORIES, recipeCategories.length);
    }
}