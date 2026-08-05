package com.tonywww.jeioptimize.recipe;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
//? if neoforge {
/*import net.minecraft.world.item.crafting.RecipeHolder;
*///?}
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves every recipe's {@link Ingredient} contents on worker threads before JEI reads them.
 *
 * <p>Ingredient tag resolution is lazy and JEI triggers all of it during startup anyway, so doing
 * it up front in parallel is the actual saving. Workers touch nothing but {@code Recipe} and
 * {@code Ingredient} instances and every result is discarded: JEI's validation, ordering and
 * registration all stay untouched on the main thread, which runs after a join barrier.
 *
 * <p>This deliberately never calls {@code IRecipeCategory.isHandled} off-thread. That method
 * mutates JEI's per-recipe extension cache (a plain {@code IdentityHashMap}) and invokes
 * third-party extension factories, so calling it concurrently corrupted the cache and made JEI
 * silently drop recipes it had already classified as handled.
 */
public final class VanillaRecipeWarmup {
    private static final int MIN_PARALLEL_RECIPES = 256;
    private static final int MIN_CHUNK_SIZE = 64;

    private VanillaRecipeWarmup() {
    }

    public static void warmUp(RecipeManager recipeManager) {
        if (recipeManager == null || !JeiOptFeatureFlags.parallelVanillaRecipes()) {
            return;
        }
        // An in-world rebuild runs while the main thread owns recipe/registry/tag state, so a
        // worker could need the main thread and the joining main thread would deadlock. Only the
        // initial, exclusive startup pass pre-resolves off-thread.
        if (JeiOptRuntimeState.hasRuntimeUnloadedOnce()) {
            return;
        }

        try {
            List<Recipe<?>> recipes = snapshot(recipeManager);
            if (recipes.size() < MIN_PARALLEL_RECIPES) {
                return;
            }

            long startNanos = System.nanoTime();
            int chunkCount = chunkCount(recipes.size());
            int chunkSize = (recipes.size() + chunkCount - 1) / chunkCount;

            List<CompletableFuture<Void>> pending = new ArrayList<>(chunkCount);
            for (int index = 1; index < chunkCount; index++) {
                int from = index * chunkSize;
                if (from >= recipes.size()) {
                    break;
                }
                List<Recipe<?>> chunk = recipes.subList(from, Math.min(from + chunkSize, recipes.size()));
                pending.add(JeiOptExecutors.runAsync(() -> resolve(chunk)));
            }
            resolve(recipes.subList(0, Math.min(chunkSize, recipes.size())));
            // JEI must never read an ingredient while a worker is still resolving it.
            CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();

            JeiOptimize.LOGGER.info(
                "JEI Optimize pre-resolved ingredients for {} recipes across {} chunks in {} ms",
                recipes.size(),
                chunkCount,
                (System.nanoTime() - startNanos) / 1_000_000L
            );
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize recipe ingredient pre-resolve failed; JEI baseline remains active", e);
        }
    }

    private static int chunkCount(int recipeCount) {
        return Math.max(1, Math.min(JeiOptFeatureFlags.workerThreads(), recipeCount / MIN_CHUNK_SIZE));
    }

    private static void resolve(List<Recipe<?>> recipes) {
        for (Recipe<?> recipe : recipes) {
            try {
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient != null) {
                        ingredient.getItems();
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                // Best effort: JEI re-reads the same ingredient on the main thread and reports there.
            }
        }
    }

    //? if forge {
    private static List<Recipe<?>> snapshot(RecipeManager recipeManager) {
        return List.copyOf(recipeManager.getRecipes());
    }
    //?} else {
    /*private static List<Recipe<?>> snapshot(RecipeManager recipeManager) {
        return recipeManager.getRecipes()
            .stream()
            .<Recipe<?>>map(RecipeHolder::value)
            .toList();
    }
    *///?}
}
