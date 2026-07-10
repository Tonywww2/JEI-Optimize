package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    private IElementSearch elementSearch;

    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Shadow
    public abstract <V> void addIngredient(IListElementInfo<V> info);

    @Shadow
    public abstract void invalidateCache();

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/gui/ingredients/IngredientFilter;addIngredient(Lmezz/jei/gui/ingredients/IListElementInfo;)V"
        )
    )
    private void jeiopt$skipIndividualAddDuringConstruction(Object instance, IListElementInfo<?> ingredientInfo) {
        if (!JeiOptFeatureFlags.batchIngredientFilterInit()) {
            addIngredient(ingredientInfo);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void jeiopt$batchAddAfterConstruction(
        Object filterTextSource,
        Object clientConfig,
        Object config,
        IIngredientManager ingredientManager,
        Comparator<?> ingredientComparator,
        List<IListElementInfo<?>> ingredients,
        IModIdHelper modIdHelper,
        IIngredientVisibility ingredientVisibility,
        IColorHelper colorHelper,
        Object clientToggleState,
        CallbackInfo callbackInfo
    ) {
        if (!JeiOptFeatureFlags.batchIngredientFilterInit()) {
            return;
        }

        for (IListElementInfo<?> ingredient : ingredients) {
            updateHiddenStateEquivalent(ingredient.getElement(), ingredientVisibility);
        }
        elementSearch.addAll(ingredients, this.ingredientManager);
        invalidateCache();
    }

    private static void updateHiddenStateEquivalent(IListElement<?> element, IIngredientVisibility ingredientVisibility) {
        ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
        boolean visible = ingredientVisibility.isIngredientVisible(typedIngredient);
        if (element.isVisible() != visible) {
            element.setVisible(visible);
        }
    }
}