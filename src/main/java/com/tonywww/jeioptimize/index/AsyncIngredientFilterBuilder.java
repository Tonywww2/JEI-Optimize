package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.IElementSearch;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Populates one isolated JEI search incrementally on client ticks. Visibility, tooltip, tag,
 * creative-tab, and color helpers all stay on the client thread, and the deadline is checked after
 * every element.
 */
public final class AsyncIngredientFilterBuilder {

    private static final AtomicReference<CompletableFuture<IElementSearch>> IN_FLIGHT = new AtomicReference<>();

    private AsyncIngredientFilterBuilder() {
    }

    public static CompletableFuture<IElementSearch> buildBudgetedAsync(
        List<? extends IListElementInfo<?>> elements,
        IIngredientVisibility ingredientVisibility,
        IElementSearch search,
        ElementAppender elementAppender,
        int requestedChunkSize,
        long generation
    ) {
        int chunkSize = Math.max(1, requestedChunkSize);
        int chunkCount = (elements.size() + chunkSize - 1) / chunkSize;
        JeiOptStartupProgressState.registerBuild(generation, chunkCount, elements.size());

        CompletableFuture<IElementSearch> future = new CompletableFuture<>();
        AtomicInteger nextIndex = new AtomicInteger();
        CompletableFuture<IElementSearch> previous = IN_FLIGHT.getAndSet(future);
        if (previous != null) {
            previous.cancel(true);
        }
        JeiOptClientTickQueue.enqueue(() -> {
            if (future.isCancelled() || !JeiOptRuntimeState.isCurrent(generation)) {
                future.cancel(false);
                IN_FLIGHT.compareAndSet(future, null);
                return true;
            }

            try {
                boolean processed = false;
                while (nextIndex.get() < elements.size()
                    && (!processed || JeiOptClientTickQueue.hasTimeRemaining())) {
                    int index = nextIndex.getAndIncrement();
                    IListElementInfo<?> info = elements.get(index);
                    updateHiddenState(info.getElement(), ingredientVisibility);
                    elementAppender.add(search, info);
                    int completed = index + 1;
                    if (completed % chunkSize == 0 || completed == elements.size()) {
                        JeiOptStartupProgressState.markChunkCompleted(generation);
                    }
                    processed = true;
                }

                if (nextIndex.get() < elements.size()) {
                    return false;
                }
                JeiOptStartupProgressState.markReady(generation);
                future.complete(search);
                IN_FLIGHT.compareAndSet(future, null);
                return true;
            } catch (RuntimeException | LinkageError e) {
                future.completeExceptionally(e);
                IN_FLIGHT.compareAndSet(future, null);
                return true;
            }
        });
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

    @FunctionalInterface
    public interface ElementAppender {
        void add(IElementSearch search, IListElementInfo<?> element);
    }

}
