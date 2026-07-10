package com.tonywww.jeioptimize.mixin.registration;

import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.IngredientManagerBuilder", remap = false)
public abstract class IngredientManagerBuilderRegistrationCountMixin {
    @Inject(method = "register", at = @At("HEAD"))
    private <V> void jeiopt$countIngredientType(
        IIngredientType<V> ingredientType,
        Collection<V> allIngredients,
        IIngredientHelper<V> ingredientHelper,
        IIngredientRenderer<V> ingredientRenderer,
        CallbackInfo callbackInfo
    ) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_TYPES, 1);
    }

    @Inject(method = "addExtraIngredients", at = @At("HEAD"))
    private <V> void jeiopt$countExtraIngredients(IIngredientType<V> ingredientType, Collection<V> extraIngredients, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.EXTRA_INGREDIENTS, extraIngredients.size());
    }

    @Inject(method = "addAlias(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;Ljava/lang/String;)V", at = @At("HEAD"))
    private <I> void jeiopt$countAliasByType(IIngredientType<I> type, I ingredient, String alias, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, 1);
    }

    @Inject(method = "addAlias(Lmezz/jei/api/ingredients/ITypedIngredient;Ljava/lang/String;)V", at = @At("HEAD"))
    private <I> void jeiopt$countAliasByTypedIngredient(ITypedIngredient<I> typedIngredient, String alias, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, 1);
    }

    @Inject(method = "addAliases(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;Ljava/util/Collection;)V", at = @At("HEAD"))
    private <I> void jeiopt$countAliasesByType(IIngredientType<I> type, I ingredient, Collection<String> aliases, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, aliases.size());
    }

    @Inject(method = "addAliases(Lmezz/jei/api/ingredients/ITypedIngredient;Ljava/util/Collection;)V", at = @At("HEAD"))
    private <I> void jeiopt$countAliasesByTypedIngredient(ITypedIngredient<I> typedIngredient, Collection<String> aliases, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, aliases.size());
    }

    @Inject(method = "addAliases(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;Ljava/lang/String;)V", at = @At("HEAD"))
    private <I> void jeiopt$countSharedAliasByType(IIngredientType<I> type, Collection<I> ingredients, String alias, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, ingredients.size());
    }

    @Inject(method = "addAliases(Ljava/util/Collection;Ljava/lang/String;)V", at = @At("HEAD"))
    private <I> void jeiopt$countSharedAliasByTypedIngredients(Collection<ITypedIngredient<I>> typedIngredients, String alias, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, typedIngredients.size());
    }

    @Inject(method = "addAliases(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;Ljava/util/Collection;)V", at = @At("HEAD"))
    private <I> void jeiopt$countAliasesForManyByType(IIngredientType<I> type, Collection<I> ingredients, Collection<String> aliases, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, (long) ingredients.size() * aliases.size());
    }

    @Inject(method = "addAliases(Ljava/util/Collection;Ljava/util/Collection;)V", at = @At("HEAD"))
    private <I> void jeiopt$countAliasesForManyTypedIngredients(Collection<ITypedIngredient<I>> typedIngredients, Collection<String> aliases, CallbackInfo callbackInfo) {
        JeiPluginCallContext.record(JeiPluginCallContext.RegistrationMetric.INGREDIENT_ALIASES, (long) typedIngredients.size() * aliases.size());
    }
}