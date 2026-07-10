package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptTaskRegistry;
import com.tonywww.jeioptimize.snapshot.RecipeIndexSnapshot;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class AsyncRecipeFocusIndex implements AsyncIndex<AsyncRecipeFocusIndex.BuiltRecipeFocusIndex> {
    private static final String DEFAULT_TASK_ID = "async-recipe-focus-index";

    private final long generation;
    private final CompletableFuture<BuiltRecipeFocusIndex> future;

    private AsyncRecipeFocusIndex(long generation, CompletableFuture<BuiltRecipeFocusIndex> future) {
        this.generation = generation;
        this.future = Objects.requireNonNull(future, "future");
    }

    public static Optional<AsyncRecipeFocusIndex> buildAsync(Collection<? extends RecipeIndexSnapshot<?>> snapshots) {
        return buildAsync(DEFAULT_TASK_ID, snapshots);
    }

    public static Optional<AsyncRecipeFocusIndex> buildAsync(String taskId, Collection<? extends RecipeIndexSnapshot<?>> snapshots) {
        if (!JeiOptFeatureFlags.recipeFocusPreheat()) {
            return Optional.empty();
        }
        Collection<? extends RecipeIndexSnapshot<?>> safeSnapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
        long generation = JeiOptRuntimeState.currentGeneration();
        return JeiOptTaskRegistry.submitIfEnabled(
            taskId,
            JeiOptFeatureFlags::recipeFocusPreheat,
            () -> BuiltRecipeFocusIndex.build(safeSnapshots),
            ignored -> {
            }
        ).map(future -> new AsyncRecipeFocusIndex(generation, future));
    }

    public static AsyncRecipeFocusIndex completed(Collection<? extends RecipeIndexSnapshot<?>> snapshots) {
        return new AsyncRecipeFocusIndex(
            JeiOptRuntimeState.currentGeneration(),
            CompletableFuture.completedFuture(BuiltRecipeFocusIndex.build(snapshots == null ? List.of() : snapshots))
        );
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
    public CompletableFuture<BuiltRecipeFocusIndex> future() {
        return future;
    }

    @Override
    public Optional<BuiltRecipeFocusIndex> readyValue() {
        if (state() != AsyncIndexState.READY) {
            return Optional.empty();
        }
        return Optional.of(future.join());
    }

    @Override
    public BuiltRecipeFocusIndex awaitOrFallback(Supplier<BuiltRecipeFocusIndex> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        try {
            BuiltRecipeFocusIndex result = future.get();
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

    public record FocusKey(RecipeIngredientRole role, Object ingredientUid) {
        public FocusKey {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(ingredientUid, "ingredientUid");
        }
    }

    public record BuiltRecipeFocusIndex(
        Map<RecipeType<?>, List<Object>> allRecipesByType,
        Map<FocusKey, Set<RecipeType<?>>> typesByFocus,
        Map<RecipeType<?>, Map<FocusKey, List<Object>>> recipesByTypeAndFocus
    ) {
        public static BuiltRecipeFocusIndex empty() {
            return new BuiltRecipeFocusIndex(Map.of(), Map.of(), Map.of());
        }

        public static BuiltRecipeFocusIndex build(Collection<? extends RecipeIndexSnapshot<?>> snapshots) {
            if (snapshots == null || snapshots.isEmpty()) {
                return empty();
            }

            Map<FocusKey, Set<RecipeType<?>>> typesByFocus = new HashMap<>();
            Map<RecipeType<?>, Map<FocusKey, List<Object>>> recipesByTypeAndFocus = new HashMap<>();
            Map<RecipeType<?>, List<Object>> allRecipesByType = new HashMap<>();

            for (RecipeIndexSnapshot<?> snapshot : snapshots) {
                if (snapshot == null || snapshot.hidden()) {
                    continue;
                }
                RecipeType<?> recipeType = snapshot.recipeType();
                Object recipe = snapshot.recipe();
                if (recipeType == null || recipe == null) {
                    continue;
                }
                allRecipesByType.computeIfAbsent(recipeType, ignored -> new ArrayList<>()).add(recipe);

                for (Map.Entry<RecipeIngredientRole, Set<Object>> entry : snapshot.roleToIngredientUids().entrySet()) {
                    RecipeIngredientRole role = entry.getKey();
                    for (Object ingredientUid : entry.getValue()) {
                        if (role == null || ingredientUid == null) {
                            continue;
                        }
                        FocusKey focusKey = new FocusKey(role, ingredientUid);
                        typesByFocus.computeIfAbsent(focusKey, ignored -> new LinkedHashSet<>()).add(recipeType);
                        recipesByTypeAndFocus
                            .computeIfAbsent(recipeType, ignored -> new HashMap<>())
                            .computeIfAbsent(focusKey, ignored -> new ArrayList<>())
                            .add(recipe);
                    }
                }
            }

            return new BuiltRecipeFocusIndex(freezeAllRecipes(allRecipesByType), freezeTypes(typesByFocus), freezeRecipes(recipesByTypeAndFocus));
        }

        public List<RecipeType<?>> getRecipeTypes(FocusKey focusKey) {
            Set<RecipeType<?>> recipeTypes = typesByFocus.get(focusKey);
            if (recipeTypes == null || recipeTypes.isEmpty()) {
                return List.of();
            }
            return List.copyOf(recipeTypes);
        }

        @SuppressWarnings("unchecked")
        public <T> List<T> getRecipes(RecipeType<T> recipeType, FocusKey focusKey) {
            Map<FocusKey, List<Object>> byFocus = recipesByTypeAndFocus.get(recipeType);
            if (byFocus == null) {
                return List.of();
            }
            List<Object> recipes = byFocus.get(focusKey);
            if (recipes == null || recipes.isEmpty()) {
                return List.of();
            }
            return recipes.stream()
                .map(recipe -> (T) recipe)
                .toList();
        }

        @SuppressWarnings("unchecked")
        public <T> List<T> getAllRecipes(RecipeType<T> recipeType) {
            List<Object> recipes = allRecipesByType.get(recipeType);
            if (recipes == null || recipes.isEmpty()) {
                return List.of();
            }
            return recipes.stream()
                .map(recipe -> (T) recipe)
                .toList();
        }

        public boolean isCatalystForRecipeCategory(RecipeType<?> recipeType, FocusKey focusKey) {
            if (focusKey.role() != RecipeIngredientRole.CATALYST) {
                return false;
            }
            Map<FocusKey, List<Object>> byFocus = recipesByTypeAndFocus.get(recipeType);
            if (byFocus == null) {
                return false;
            }
            List<Object> recipes = byFocus.get(focusKey);
            return recipes != null && !recipes.isEmpty();
        }

        public int size() {
            return recipesByTypeAndFocus.values()
                .stream()
                .mapToInt(map -> map.values().stream().mapToInt(List::size).sum())
                .sum();
        }

        private static Map<FocusKey, Set<RecipeType<?>>> freezeTypes(Map<FocusKey, Set<RecipeType<?>>> input) {
            Map<FocusKey, Set<RecipeType<?>>> output = new HashMap<>();
            input.forEach((focusKey, recipeTypes) -> output.put(focusKey, Set.copyOf(recipeTypes)));
            return Map.copyOf(output);
        }

        private static Map<RecipeType<?>, List<Object>> freezeAllRecipes(Map<RecipeType<?>, List<Object>> input) {
            Map<RecipeType<?>, List<Object>> output = new HashMap<>();
            input.forEach((recipeType, recipes) -> output.put(recipeType, List.copyOf(recipes)));
            return Map.copyOf(output);
        }

        private static Map<RecipeType<?>, Map<FocusKey, List<Object>>> freezeRecipes(Map<RecipeType<?>, Map<FocusKey, List<Object>>> input) {
            Map<RecipeType<?>, Map<FocusKey, List<Object>>> output = new HashMap<>();
            input.forEach((recipeType, byFocus) -> {
                Map<FocusKey, List<Object>> frozenByFocus = new HashMap<>();
                byFocus.forEach((focusKey, recipes) -> frozenByFocus.put(focusKey, List.copyOf(recipes)));
                output.put(recipeType, Map.copyOf(frozenByFocus));
            });
            return Map.copyOf(output);
        }
    }
}