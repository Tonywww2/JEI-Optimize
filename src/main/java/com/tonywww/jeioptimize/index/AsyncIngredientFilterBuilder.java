package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.ElementSearch;
import mezz.jei.gui.search.IElementSearch;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Builds a fully populated JEI {@link IElementSearch} on worker threads using JEI's own
 * {@link ElementSearch}. The build MUST reuse the {@link ElementPrefixParser} instance owned
 * by the target {@code IngredientFilter}: JEI keys its per-prefix search storages in an
 * {@code IdentityHashMap} by {@code PrefixInfo} identity, and parses live queries with that
 * same parser. Building with a fresh parser would produce a search whose {@code @}/{@code $}
 * (mod/tag) storages can never be matched by the filter's queries, silently breaking prefixed
 * search while plain text search (which uses the shared static {@code NO_PREFIX}) keeps working.
 * The resulting instance is isolated (never shared with the main thread until it is atomically
 * swapped in), so the only work that leaves the main thread is JEI's own extraction and
 * suffix-tree construction.
 */
public final class AsyncIngredientFilterBuilder {

    private static final AtomicReference<CompletableFuture<IElementSearch>> IN_FLIGHT = new AtomicReference<>();

    private AsyncIngredientFilterBuilder() {
    }

    public static CompletableFuture<IElementSearch> buildAsync(
        List<IListElementInfo<?>> elements,
        IIngredientManager ingredientManager,
        ElementPrefixParser prefixParser,
        IIngredientVisibility ingredientVisibility
    ) {
        return buildAsync(elements, ingredientVisibility, infos -> {
            // Reuse the filter's own parser so PrefixInfo identities match its live queries.
            ElementSearch elementSearch = new ElementSearch(prefixParser);
            elementSearch.addAll(infos, ingredientManager);
            return elementSearch;
        });
    }

    /** Builds through JEI's own search factory, for JEI versions that expose one. */
    public static CompletableFuture<IElementSearch> buildAsync(
        List<IListElementInfo<?>> elements,
        IIngredientVisibility ingredientVisibility,
        Function<List<IListElementInfo<?>>, IElementSearch> searchFactory
    ) {
        List<IListElementInfo<?>> safeElements = List.copyOf(elements);
        CompletableFuture<IElementSearch> future = JeiOptExecutors.supplyAsync(() -> {
            for (IListElementInfo<?> info : safeElements) {
                updateHiddenState(info.getElement(), ingredientVisibility);
            }
            return searchFactory.apply(safeElements);
        });
        CompletableFuture<IElementSearch> previous = IN_FLIGHT.getAndSet(future);
        if (previous != null) {
            previous.cancel(true);
        }
        return future;
    }

    /** Drops a build that is still running for a JEI runtime that is going away. */
    public static void cancelInFlight() {
        CompletableFuture<IElementSearch> future = IN_FLIGHT.getAndSet(null);
        if (future != null) {
            future.cancel(true);
        }
    }

    private static void updateHiddenState(IListElement<?> element, IIngredientVisibility ingredientVisibility) {
        ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
        boolean visible = ingredientVisibility.isIngredientVisible(typedIngredient);
        if (element.isVisible() != visible) {
            element.setVisible(visible);
        }
    }
}
