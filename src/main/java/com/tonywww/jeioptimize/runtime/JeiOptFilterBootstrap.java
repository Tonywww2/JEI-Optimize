package com.tonywww.jeioptimize.runtime;

import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.IElementSearch;

import java.util.List;

/**
 * Hands the ingredient list and isolated empty search from a constructor redirect to the matching
 * {@code RETURN} callback.
 *
 * <p>Newer JEI builds construct the whole search index inside one private factory call, so the
 * only way to defer that work is to intercept the call itself. The callback that schedules the
 * client-tick build cannot capture the constructor arguments without binding to a specific JEI
 * constructor descriptor, so the two halves meet here instead. JEI builds one filter at a time,
 * so a single slot is enough.
 */
public final class JeiOptFilterBootstrap {
    private static volatile Pending pending;

    private JeiOptFilterBootstrap() {
    }

    public static void capture(
        List<IListElementInfo<?>> ingredients,
        IIngredientManager ingredientManager,
        IElementSearch emptySearch
    ) {
        pending = new Pending(ingredients, ingredientManager, emptySearch);
    }

    public static Pending take() {
        Pending taken = pending;
        pending = null;
        return taken;
    }

    public static void clear() {
        pending = null;
    }

    public record Pending(
        List<IListElementInfo<?>> ingredients,
        IIngredientManager ingredientManager,
        IElementSearch emptySearch
    ) {
    }
}
