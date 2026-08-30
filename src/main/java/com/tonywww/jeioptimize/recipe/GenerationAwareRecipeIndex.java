package com.tonywww.jeioptimize.recipe;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class GenerationAwareRecipeIndex<T> {
    private final Map<T, T> recipesByIdentity = new HashMap<>();

    private long generation = Long.MIN_VALUE;
    private Collection<?> trackedRecipes;
    private boolean active;
    private boolean broken;

    synchronized boolean prepare(Collection<T> recipes, long expectedGeneration, boolean enabled) {
        switchGeneration(expectedGeneration);
        if (!enabled || broken) {
            return false;
        }
        if (trackedRecipes != recipes) {
            trackedRecipes = recipes;
            recipesByIdentity.clear();
            for (T recipe : recipes) {
                recipesByIdentity.putIfAbsent(recipe, recipe);
            }
            active = true;
        }
        return active;
    }

    synchronized boolean usable(Collection<T> recipes, long expectedGeneration, boolean enabled) {
        if (!prepare(recipes, expectedGeneration, enabled)) {
            return false;
        }
        if (recipesByIdentity.size() != recipes.size()) {
            broken = true;
            active = false;
            recipesByIdentity.clear();
            return false;
        }
        return true;
    }

    synchronized T find(T recipe) {
        return recipesByIdentity.get(recipe);
    }

    synchronized void added(Collection<T> recipes, T recipe) {
        if (active && !broken && trackedRecipes == recipes) {
            recipesByIdentity.putIfAbsent(recipe, recipe);
        }
    }

    synchronized void removed(Collection<T> recipes, T recipe) {
        if (active && !broken && trackedRecipes == recipes) {
            recipesByIdentity.remove(recipe);
        }
    }

    synchronized boolean broken() {
        return broken;
    }

    private void switchGeneration(long expectedGeneration) {
        if (generation == expectedGeneration) {
            return;
        }
        generation = expectedGeneration;
        trackedRecipes = null;
        recipesByIdentity.clear();
        active = false;
        broken = false;
    }
}