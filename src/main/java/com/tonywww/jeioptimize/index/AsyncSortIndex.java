package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptTaskRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class AsyncSortIndex<T> implements AsyncIndex<List<T>> {
    private static final String INGREDIENT_SORT_TASK_ID = "ingredient-sort";
    private static final AtomicReference<AsyncSortIndex<Integer>> LATEST_INGREDIENT_SORT = new AtomicReference<>();

    private final AtomicReference<AsyncIndexState> state = new AtomicReference<>(AsyncIndexState.NOT_STARTED);
    private final AtomicReference<List<T>> readyValue = new AtomicReference<>();
    private volatile CompletableFuture<List<T>> future = CompletableFuture.completedFuture(List.of());

    private AsyncSortIndex() {
    }

    public static <T> AsyncSortIndex<T> preheat(String taskId, List<SortKey<T>> sortKeys) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(sortKeys, "sortKeys");

        AsyncSortIndex<T> index = new AsyncSortIndex<>();
        index.start(taskId, sortKeys);
        return index;
    }

    public static AsyncSortIndex<Integer> preheatIngredientSort(List<SortKey<Integer>> sortKeys) {
        AsyncSortIndex<Integer> index = preheat(INGREDIENT_SORT_TASK_ID, sortKeys);
        LATEST_INGREDIENT_SORT.set(index);
        return index;
    }

    public static Optional<AsyncSortIndex<Integer>> latestIngredientSort() {
        return Optional.ofNullable(LATEST_INGREDIENT_SORT.get());
    }

    private void start(String taskId, List<SortKey<T>> sortKeys) {
        if (!JeiOptFeatureFlags.sortPreheat()) {
            this.state.set(AsyncIndexState.NOT_STARTED);
            this.future = CompletableFuture.completedFuture(List.of());
            return;
        }

        List<SortKey<T>> snapshot = List.copyOf(sortKeys);
        this.state.set(AsyncIndexState.BUILDING);
        Optional<CompletableFuture<List<T>>> submitted = JeiOptTaskRegistry.submitIfEnabled(
            taskId,
            JeiOptFeatureFlags::sortPreheat,
            () -> sort(snapshot),
            result -> {
                this.readyValue.set(result);
                this.state.set(AsyncIndexState.READY);
            }
        );

        if (submitted.isEmpty()) {
            this.state.set(AsyncIndexState.NOT_STARTED);
            this.future = CompletableFuture.completedFuture(List.of());
            return;
        }

        this.future = submitted.get();
        this.future.whenComplete((result, error) -> {
            if (error != null && this.state.get() != AsyncIndexState.READY) {
                this.state.set(AsyncIndexState.FAILED);
            }
        });
    }

    private static <T> List<T> sort(List<SortKey<T>> sortKeys) {
        return sortKeys.stream()
            .sorted(SortKey.compareByIndex())
            .map(SortKey::value)
            .toList();
    }

    @Override
    public AsyncIndexState state() {
        return this.state.get();
    }

    @Override
    public CompletableFuture<List<T>> future() {
        return this.future;
    }

    @Override
    public Optional<List<T>> readyValue() {
        return Optional.ofNullable(this.readyValue.get());
    }

    @Override
    public List<T> awaitOrFallback(Supplier<List<T>> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        List<T> ready = this.readyValue.get();
        if (ready != null) {
            return ready;
        }
        try {
            List<T> result = this.future.get();
            return result != null ? result : fallback.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.get();
        } catch (RuntimeException | java.util.concurrent.ExecutionException e) {
            return fallback.get();
        }
    }
}