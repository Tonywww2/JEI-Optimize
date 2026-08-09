package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.IElementSearch;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

    public static CompletableFuture<IElementSearch> buildChunkedAsync(
        List<IListElementInfo<?>> elements,
        IIngredientManager ingredientManager,
        IIngredientVisibility ingredientVisibility,
        Supplier<IElementSearch> emptySearchFactory,
        ChunkAppender chunkAppender,
        int requestedChunkSize,
        long generation
    ) {
        List<IListElementInfo<?>> safeElements = List.copyOf(elements);
        int chunkSize = Math.max(1, requestedChunkSize);
        int chunkCount = (safeElements.size() + chunkSize - 1) / chunkSize;
        JeiOptStartupProgressState.registerBuild(generation, chunkCount, safeElements.size());

        CompletableFuture<IElementSearch> future = JeiOptExecutors.supplyAsync(() -> {
            IElementSearch search = emptySearchFactory.get();
            for (int start = 0; start < safeElements.size(); start += chunkSize) {
                checkActive(generation);
                int end = Math.min(start + chunkSize, safeElements.size());
                List<IListElementInfo<?>> chunk = safeElements.subList(start, end);
                for (IListElementInfo<?> info : chunk) {
                    updateHiddenState(info.getElement(), ingredientVisibility);
                }
                chunkAppender.add(search, chunk);
                JeiOptStartupProgressState.markChunkCompleted(generation);
            }
            checkActive(generation);
            JeiOptStartupProgressState.markReady(generation);
            return search;
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

    private static void checkActive(long generation) {
        if (Thread.currentThread().isInterrupted() || !JeiOptRuntimeState.isCurrent(generation)) {
            throw new CancellationException("JEI ingredient filter build is no longer current");
        }
    }

    @FunctionalInterface
    public interface ChunkAppender {
        void add(IElementSearch search, List<IListElementInfo<?>> chunk);
    }
}
