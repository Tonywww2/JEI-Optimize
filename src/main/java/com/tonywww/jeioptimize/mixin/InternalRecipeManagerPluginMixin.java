package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncRecipeFocusIndex;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.recipes.InternalRecipeManagerPlugin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Mixin(value = InternalRecipeManagerPlugin.class, remap = false)
public abstract class InternalRecipeManagerPluginMixin {
    private static final Map<Object, AsyncRecipeFocusIndex> ASYNC_INDEXES = Collections.synchronizedMap(new WeakHashMap<>());

    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    public static void jeiopt$attachAsyncIndex(Object internalRecipeManagerPlugin, AsyncRecipeFocusIndex asyncRecipeFocusIndex) {
        if (internalRecipeManagerPlugin == null || asyncRecipeFocusIndex == null) {
            return;
        }
        ASYNC_INDEXES.put(internalRecipeManagerPlugin, asyncRecipeFocusIndex);
    }

    public static void jeiopt$detachAsyncIndex(Object internalRecipeManagerPlugin) {
        if (internalRecipeManagerPlugin != null) {
            ASYNC_INDEXES.remove(internalRecipeManagerPlugin);
        }
    }

    @Inject(method = "getRecipeTypes", at = @At("HEAD"), cancellable = true)
    private <V> void jeiopt$getAsyncRecipeTypes(IFocus<V> focus, CallbackInfoReturnable<List<RecipeType<?>>> callbackInfo) {
        if (!JeiOptFeatureFlags.recipeFocusPreheat()) {
            return;
        }
        AsyncRecipeFocusIndex asyncIndex = ASYNC_INDEXES.get(this);
        if (asyncIndex == null) {
            return;
        }
        Optional<AsyncRecipeFocusIndex.FocusKey> focusKey = focusKey(focus);
        if (focusKey.isEmpty()) {
            return;
        }

        AsyncRecipeFocusIndex.BuiltRecipeFocusIndex index = asyncIndex.readyValue()
            .orElseGet(() -> asyncIndex.awaitOrFallback(() -> null));
        if (index == null) {
            return;
        }
        callbackInfo.setReturnValue(index.getRecipeTypes(focusKey.get()));
    }

    @Inject(method = "getRecipes(Lmezz/jei/api/recipe/category/IRecipeCategory;Lmezz/jei/api/recipe/IFocus;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private <T, V> void jeiopt$getAsyncRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus, CallbackInfoReturnable<List<T>> callbackInfo) {
        if (!JeiOptFeatureFlags.recipeFocusPreheat()) {
            return;
        }
        AsyncRecipeFocusIndex asyncIndex = ASYNC_INDEXES.get(this);
        if (asyncIndex == null) {
            return;
        }
        Optional<AsyncRecipeFocusIndex.FocusKey> focusKey = focusKey(focus);
        if (focusKey.isEmpty()) {
            return;
        }

        AsyncRecipeFocusIndex.BuiltRecipeFocusIndex index = asyncIndex.readyValue()
            .orElseGet(() -> asyncIndex.awaitOrFallback(() -> null));
        if (index == null) {
            return;
        }
        RecipeType<T> recipeType = recipeCategory.getRecipeType();
        List<T> recipes = index.getRecipes(recipeType, focusKey.get());
        if (index.isCatalystForRecipeCategory(recipeType, focusKey.get())) {
            List<T> recipesForCategory = index.getAllRecipes(recipeType);
            callbackInfo.setReturnValue(Stream.concat(recipes.stream(), recipesForCategory.stream()).distinct().toList());
            return;
        }
        callbackInfo.setReturnValue(recipes);
    }

    private <V> Optional<AsyncRecipeFocusIndex.FocusKey> focusKey(IFocus<V> focus) {
        if (focus == null) {
            return Optional.empty();
        }
        try {
            ITypedIngredient<V> typedIngredient = focus.getTypedValue();
            IIngredientHelper<V> ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
            Object uid = ingredientHelper.getUniqueId(typedIngredient.getIngredient(), UidContext.Recipe);
            if (uid == null) {
                return Optional.empty();
            }
            return Optional.of(new AsyncRecipeFocusIndex.FocusKey(focus.getRole(), uid));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }
}