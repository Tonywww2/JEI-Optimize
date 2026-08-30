package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptTaskRegistry;
import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;
import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshotBuilder;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.gui.ingredients.IListElementInfo;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
            () -> SearchIndexBuilder.build(
                safeSnapshots,
                true,
                JeiOptFeatureFlags.parallelThreshold()
            ),
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
        if (JeiOptFeatureFlags.snapshotChunking()) {
            return Optional.of(buildChunkedFromElementInfos(
                safeElementInfos,
                ingredientManager,
                ingredientFilterConfig,
                colorHelper
            ));
        }
        List<IngredientSearchSnapshot> snapshots = IngredientSearchSnapshotBuilder.fromElementInfos(
            safeElementInfos,
            ingredientManager,
            ingredientFilterConfig,
            colorHelper
        );
        return buildAsync(DEFAULT_TASK_ID, snapshots);
    }

    private static AsyncSearchIndex buildChunkedFromElementInfos(
        List<? extends IListElementInfo<?>> elementInfos,
        IIngredientManager ingredientManager,
        IIngredientFilterConfig ingredientFilterConfig,
        IColorHelper colorHelper
    ) {
        long generation = JeiOptRuntimeState.currentGeneration();
        CompletableFuture<SearchIndexBuilder.BuiltSearchIndex> result = new CompletableFuture<>();
        JeiOptRuntimeState.track(result);
        IngredientSearchSnapshotBuilder snapshotBuilder = new IngredientSearchSnapshotBuilder(
            ingredientManager,
            ingredientFilterConfig,
            colorHelper
        );
        List<IngredientSearchSnapshot> snapshots = new ArrayList<>(elementInfos.size());
        AtomicInteger nextIndex = new AtomicInteger();
        JeiOptClientTickQueue.enqueue(() -> {
            if (!JeiOptRuntimeState.isCurrent(generation) || !JeiOptFeatureFlags.searchPreheat()) {
                result.cancel(false);
                return true;
            }
            long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(JeiOptFeatureFlags.snapshotBudgetMs());
            while (nextIndex.get() < elementInfos.size() && System.nanoTime() < deadline) {
                int index = nextIndex.getAndIncrement();
                snapshotBuilder.fromElementInfo(elementInfos.get(index)).ifPresent(snapshots::add);
            }
            if (nextIndex.get() < elementInfos.size()) {
                return false;
            }
            JeiOptimize.LOGGER.info(
                "JEI Optimize captured {} of {} search snapshots on client ticks; submitting pure prefix indexing",
                snapshots.size(),
                elementInfos.size()
            );
            CompletableFuture<SearchIndexBuilder.BuiltSearchIndex> worker = CompletableFuture.supplyAsync(
                () -> SearchIndexBuilder.build(
                    List.copyOf(snapshots),
                    true,
                    JeiOptFeatureFlags.parallelThreshold()
                ),
                JeiOptExecutors.pureComputationPool()
            );
            JeiOptRuntimeState.track(worker);
            worker.whenComplete((built, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else if (JeiOptRuntimeState.isCurrent(generation)) {
                    JeiOptimize.LOGGER.info(
                        "JEI Optimize pure prefix index completed: {} ingredients, failed prefixes {}",
                        built.size(),
                        built.failedPrefixes()
                    );
                    result.complete(built);
                } else {
                    result.cancel(false);
                }
            });
            return true;
        });
        return new AsyncSearchIndex(generation, result);
    }

    public static AsyncSearchIndex completed(Collection<IngredientSearchSnapshot> snapshots) {
        SearchIndexBuilder.BuiltSearchIndex index = SearchIndexBuilder.build(snapshots);
        return new AsyncSearchIndex(JeiOptRuntimeState.currentGeneration(), CompletableFuture.completedFuture(index));
    }

    static AsyncSearchIndex buildFromSnapshots(List<IngredientSearchSnapshot> snapshots, long generation) {
        SearchIndexBuilder.BuiltSearchIndex index = SearchIndexBuilder.build(snapshots, false, Integer.MAX_VALUE);
        return new AsyncSearchIndex(generation, CompletableFuture.completedFuture(index));
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