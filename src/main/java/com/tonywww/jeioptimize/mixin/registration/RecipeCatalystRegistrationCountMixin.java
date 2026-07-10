package com.tonywww.jeioptimize.mixin.registration;

import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.RecipeCatalystRegistration", remap = false)
public abstract class RecipeCatalystRegistrationCountMixin {
    @Inject(method = "addRecipeCatalyst", at = @At("HEAD"))
    private <T> void jeiopt$countSingleCatalyst(IIngredientType<T> ingredientType, T ingredient, RecipeType<?>[] recipeTypes, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.RECIPE_CATALYSTS, recipeTypes.length);
    }

    @Inject(method = "addRecipeCatalysts(Lmezz/jei/api/recipe/RecipeType;[Lnet/minecraft/world/level/ItemLike;)V", at = @At("HEAD"))
    private void jeiopt$countItemLikeCatalysts(RecipeType<?> recipeType, ItemLike[] ingredients, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.RECIPE_CATALYSTS, ingredients.length);
    }

    @Inject(method = "addRecipeCatalysts(Lmezz/jei/api/recipe/RecipeType;Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/List;)V", at = @At("HEAD"))
    private <T> void jeiopt$countTypedCatalysts(RecipeType<?> recipeType, IIngredientType<T> ingredientType, List<T> ingredients, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.RECIPE_CATALYSTS, ingredients.size());
    }
}