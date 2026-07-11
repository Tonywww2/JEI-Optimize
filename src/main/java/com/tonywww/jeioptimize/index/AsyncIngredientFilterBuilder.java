package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.ElementSearch;
import mezz.jei.gui.search.IElementSearch;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a fully populated JEI {@link IElementSearch} on worker threads using JEI's own
 * {@link ElementPrefixParser} and {@link ElementSearch}. The resulting instance is isolated
 * (never shared with the main thread until it is atomically swapped in), so the only work
 * that leaves the main thread is JEI's own extraction and suffix-tree construction.
 */
public final class AsyncIngredientFilterBuilder {

    private AsyncIngredientFilterBuilder() {
    }

    public static CompletableFuture<IElementSearch> buildAsync(
        List<IListElementInfo<?>> elements,
        IIngredientManager ingredientManager,
        IIngredientFilterConfig ingredientFilterConfig,
        IColorHelper colorHelper,
        IModIdHelper modIdHelper,
        IIngredientVisibility ingredientVisibility
    ) {
        List<IListElementInfo<?>> safeElements = List.copyOf(elements);
        return JeiOptExecutors.supplyAsync(() -> {
            for (IListElementInfo<?> info : safeElements) {
                updateHiddenState(info.getElement(), ingredientVisibility);
            }
            //? if forge {
            ElementPrefixParser prefixParser =
                new ElementPrefixParser(ingredientManager, ingredientFilterConfig, colorHelper);
            //?} else {
            /*ElementPrefixParser prefixParser =
                new ElementPrefixParser(ingredientManager, ingredientFilterConfig, colorHelper, modIdHelper);
            *///?}
            ElementSearch elementSearch = new ElementSearch(prefixParser);
            elementSearch.addAll(safeElements, ingredientManager);
            return elementSearch;
        });
    }

    private static void updateHiddenState(IListElement<?> element, IIngredientVisibility ingredientVisibility) {
        ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
        boolean visible = ingredientVisibility.isIngredientVisible(typedIngredient);
        if (element.isVisible() != visible) {
            element.setVisible(visible);
        }
    }
}
