package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptTaskRegistry;
import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;
import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshotBuilder;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.gui.ingredients.IListElementInfo;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class AsyncSearchIndex implements AsyncIndex<SearchIndexBuilder.BuiltSearchIndex> {
    private static final String DEFAULT_TASK_ID = "async-search-index";

    private final long generation;
    private final CompletableFuture<SearchIndexBuilder.BuiltSearchIndex> future;

    private AsyncSearchIndex(long generation, CompletableFuture<SearchIndexBuilder.BuiltSearchIndex> future) {
        this.generation = generation;
        this.future = Objects.requireNonNull(future, "future");
    }

    public static Optional<AsyncSearchIndex> buildAsync(Collection<IngredientSearchSnapshot> snapshots) {
        return buildAsync(DEFAULT_TASK_ID, snapshots);
    }

    public static Optional<AsyncSearchIndex> buildAsync(String taskId, Collection<IngredientSearchSnapshot> snapshots) {
        if (!JeiOptFeatureFlags.searchPreheat()) {
            return Optional.empty();
        }
        Collection<IngredientSearchSnapshot> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        long generation = JeiOptRuntimeState.currentGeneration();
        return JeiOptTaskRegistry.submitIfEnabled(
            taskId,
            JeiOptFeatureFlags::searchPreheat,
            () -> SearchIndexBuilder.build(safeSnapshots),
            ignored -> {
            }
        ).map(future -> new AsyncSearchIndex(generation, future));
    }

    public static Optional<AsyncSearchIndex> buildAsyncFromElementInfos(
        List<? extends IListElementInfo<?>> elementInfos,
        IIngredientManager ingredientManager,
        IIngredientFilterConfig ingredientFilterConfig,
        IColorHelper colorHelper
    ) {
        if (!JeiOptFeatureFlags.searchPreheat()) {
            return Optional.empty();
        }
        if (elementInfos == null || elementInfos.isEmpty()) {
            return Optional.empty();
        }
        List<? extends IListElementInfo<?>> safeElementInfos = List.copyOf(elementInfos);
        long generation = JeiOptRuntimeState.currentGeneration();
        return JeiOptTaskRegistry.submitIfEnabled(
            DEFAULT_TASK_ID,
            JeiOptFeatureFlags::searchPreheat,
            () -> SearchIndexBuilder.build(IngredientSearchSnapshotBuilder.fromElementInfos(
                safeElementInfos, ingredientManager, ingredientFilterConfig, colorHelper)),
            ignored -> {
            }
        ).map(future -> new AsyncSearchIndex(generation, future));
    }

    public static AsyncSearchIndex completed(Collection<IngredientSearchSnapshot> snapshots) {
        SearchIndexBuilder.BuiltSearchIndex index = SearchIndexBuilder.build(snapshots);
        return new AsyncSearchIndex(JeiOptRuntimeState.currentGeneration(), CompletableFuture.completedFuture(index));
    }

    @Override
    public AsyncIndexState state() {
        if (!JeiOptRuntimeState.isCurrent(generation)) {
            return AsyncIndexState.FAILED;
        }
        if (!future.isDone()) {
            return AsyncIndexState.BUILDING;
        }
        if (future.isCompletedExceptionally() || future.isCancelled()) {
            return AsyncIndexState.FAILED;
        }
        return AsyncIndexState.READY;
    }

    @Override
    public CompletableFuture<SearchIndexBuilder.BuiltSearchIndex> future() {
        return future;
    }

    @Override
    public Optional<SearchIndexBuilder.BuiltSearchIndex> readyValue() {
        if (state() != AsyncIndexState.READY) {
            return Optional.empty();
        }
        return Optional.of(future.join());
    }

    @Override
    public SearchIndexBuilder.BuiltSearchIndex awaitOrFallback(Supplier<SearchIndexBuilder.BuiltSearchIndex> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        try {
            SearchIndexBuilder.BuiltSearchIndex result = future.get();
            if (!JeiOptRuntimeState.isCurrent(generation)) {
                return fallback.get();
            }
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.get();
        } catch (ExecutionException | RuntimeException e) {
            return fallback.get();
        }
    }
}