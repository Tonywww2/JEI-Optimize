package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptTaskRegistry;
import com.tonywww.jeioptimize.snapshot.RecipeIndexSnapshot;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class AsyncCatalystIndex implements AsyncIndex<Map<Object, Set<RecipeType<?>>>> {
    private static final String TASK_ID = "catalyst-index";
    private static final AtomicReference<AsyncCatalystIndex> LATEST = new AtomicReference<>();

    private final AtomicReference<AsyncIndexState> state = new AtomicReference<>(AsyncIndexState.NOT_STARTED);
    private final AtomicReference<Map<Object, Set<RecipeType<?>>>> readyValue = new AtomicReference<>();
    private volatile CompletableFuture<Map<Object, Set<RecipeType<?>>>> future = CompletableFuture.completedFuture(Map.of());

    private AsyncCatalystIndex() {
    }

    public static AsyncCatalystIndex preheat(Collection<RecipeIndexSnapshot<?>> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        AsyncCatalystIndex index = new AsyncCatalystIndex();
        index.start(snapshots);
        LATEST.set(index);
        return index;
    }

    public static Optional<AsyncCatalystIndex> latest() {
        return Optional.ofNullable(LATEST.get());
    }

    public boolean isCatalystForRecipeCategory(RecipeType<?> recipeType, Object catalystUid) {
        Objects.requireNonNull(recipeType, "recipeType");
        Objects.requireNonNull(catalystUid, "catalystUid");
        Set<RecipeType<?>> recipeTypes = readyValue()
            .map(index -> index.get(catalystUid))
            .orElse(null);
        return recipeTypes != null && recipeTypes.contains(recipeType);
    }

    private void start(Collection<RecipeIndexSnapshot<?>> snapshots) {
        if (!JeiOptFeatureFlags.catalystPreheat()) {
            this.state.set(AsyncIndexState.NOT_STARTED);
            this.future = CompletableFuture.completedFuture(Map.of());
            return;
        }

        java.util.List<RecipeIndexSnapshot<?>> snapshotCopy = java.util.List.copyOf(snapshots);
        this.state.set(AsyncIndexState.BUILDING);
        Optional<CompletableFuture<Map<Object, Set<RecipeType<?>>>>> submitted = JeiOptTaskRegistry.submitIfEnabled(
            TASK_ID,
            JeiOptFeatureFlags::catalystPreheat,
            () -> build(snapshotCopy),
            result -> {
                this.readyValue.set(result);
                this.state.set(AsyncIndexState.READY);
            }
        );

        if (submitted.isEmpty()) {
            this.state.set(AsyncIndexState.NOT_STARTED);
            this.future = CompletableFuture.completedFuture(Map.of());
            return;
        }

        this.future = submitted.get();
        this.future.whenComplete((result, error) -> {
            if (error != null && this.state.get() != AsyncIndexState.READY) {
                this.state.set(AsyncIndexState.FAILED);
            }
        });
    }

    private static Map<Object, Set<RecipeType<?>>> build(Collection<RecipeIndexSnapshot<?>> snapshots) {
        Map<Object, Set<RecipeType<?>>> mutable = new HashMap<>();
        for (RecipeIndexSnapshot<?> snapshot : snapshots) {
            Set<Object> catalystUids = snapshot.roleToIngredientUids().get(RecipeIngredientRole.CATALYST);
            if (catalystUids == null || catalystUids.isEmpty()) {
                continue;
            }
            RecipeType<?> recipeType = snapshot.recipeType();
            for (Object catalystUid : catalystUids) {
                mutable.computeIfAbsent(catalystUid, ignored -> new HashSet<>()).add(recipeType);
            }
        }

        Map<Object, Set<RecipeType<?>>> immutable = new HashMap<>(mutable.size());
        for (Map.Entry<Object, Set<RecipeType<?>>> entry : mutable.entrySet()) {
            immutable.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    @Override
    public AsyncIndexState state() {
        return this.state.get();
    }

    @Override
    public CompletableFuture<Map<Object, Set<RecipeType<?>>>> future() {
        return this.future;
    }

    @Override
    public Optional<Map<Object, Set<RecipeType<?>>>> readyValue() {
        return Optional.ofNullable(this.readyValue.get());
    }

    @Override
    public Map<Object, Set<RecipeType<?>>> awaitOrFallback(Supplier<Map<Object, Set<RecipeType<?>>>> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        Map<Object, Set<RecipeType<?>>> ready = this.readyValue.get();
        if (ready != null) {
            return ready;
        }
        try {
            Map<Object, Set<RecipeType<?>>> result = this.future.get();
            return result != null ? result : fallback.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.get();
        } catch (RuntimeException | java.util.concurrent.ExecutionException e) {
            return fallback.get();
        }
    }
}