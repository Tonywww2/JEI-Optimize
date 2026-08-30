package com.tonywww.jeioptimize.recipe;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BrewingRecipeIndex {
    private static final GenerationAwareRecipeIndex<IJeiBrewingRecipe> INDEX =
        new GenerationAwareRecipeIndex<>();
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();
    private static volatile long loggedGeneration = Long.MIN_VALUE;

    private BrewingRecipeIndex() {
    }

    public static boolean prepare(Collection<IJeiBrewingRecipe> recipes) {
        return INDEX.prepare(
            recipes,
            JeiOptRuntimeState.currentGeneration(),
            JeiOptFeatureFlags.indexedBrewingLookup()
        );
    }

    public static boolean usable(Collection<IJeiBrewingRecipe> recipes) {
        long generation = JeiOptRuntimeState.currentGeneration();
        boolean usable = INDEX.usable(
            recipes,
            generation,
            JeiOptFeatureFlags.indexedBrewingLookup()
        );
        if (usable && loggedGeneration != generation) {
            loggedGeneration = generation;
            JeiOptimize.LOGGER.debug(
                "JEI Optimize indexed brewing lookup is active for generation {} with {} recipes",
                generation,
                recipes.size()
            );
        }
        if (!usable && INDEX.broken() && WARNING_LOGGED.compareAndSet(false, true)) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize brewing index fell out of step with JEI; using JEI's lookup for this lifecycle"
            );
        }
        return usable;
    }

    public static IJeiBrewingRecipe find(IJeiBrewingRecipe recipe) {
        return INDEX.find(recipe);
    }

    public static void added(Collection<IJeiBrewingRecipe> recipes, IJeiBrewingRecipe recipe) {
        INDEX.added(recipes, recipe);
    }

    public static void removed(Collection<IJeiBrewingRecipe> recipes, IJeiBrewingRecipe recipe) {
        INDEX.removed(recipes, recipe);
    }
}