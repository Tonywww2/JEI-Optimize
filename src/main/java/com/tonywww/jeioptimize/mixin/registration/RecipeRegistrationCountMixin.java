package com.tonywww.jeioptimize.mixin.registration;

import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.RecipeRegistration", remap = false)
public abstract class RecipeRegistrationCountMixin {
    @Inject(method = "addRecipes", at = @At("HEAD"))
    private <T> void jeiopt$countRecipes(RecipeType<T> recipeType, List<T> recipes, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.RECIPES, recipes.size());
    }

    @Inject(method = "addIngredientInfo(Ljava/lang/Object;Lmezz/jei/api/ingredients/IIngredientType;[Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private <T> void jeiopt$countSingleIngredientInfo(T ingredient, IIngredientType<T> ingredientType, Component[] descriptionComponents, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_INFO_RECIPES, 1);
    }

    @Inject(method = "addIngredientInfo(Ljava/util/List;Lmezz/jei/api/ingredients/IIngredientType;[Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private <T> void jeiopt$countIngredientInfoList(List<T> ingredients, IIngredientType<T> ingredientType, Component[] descriptionComponents, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_INFO_RECIPES, 1);
    }
}