package com.tonywww.jeioptimize.recipe;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationAwareRecipeIndexTest {
    @Test
    void tracksOneCollectionAndResetsForANewGeneration() {
        GenerationAwareRecipeIndex<String> index = new GenerationAwareRecipeIndex<>();
        List<String> recipes = new ArrayList<>();

        assertTrue(index.prepare(recipes, 1L, true));
        recipes.add("a");
        index.added(recipes, "a");
        assertTrue(index.usable(recipes, 1L, true));
        assertEquals("a", index.find("a"));

        assertTrue(index.usable(recipes, 2L, true));
        assertEquals("a", index.find("a"));
    }

    @Test
    void disablesItselfWhenTheIndexAndCollectionDiverge() {
        GenerationAwareRecipeIndex<String> index = new GenerationAwareRecipeIndex<>();
        List<String> recipes = new ArrayList<>();

        assertTrue(index.prepare(recipes, 1L, true));
        recipes.add("untracked");

        assertFalse(index.usable(recipes, 1L, true));
        assertTrue(index.broken());
        assertFalse(index.prepare(recipes, 1L, true));
    }

    @Test
    void tracksAddAndRemoveMutations() {
        GenerationAwareRecipeIndex<String> index = new GenerationAwareRecipeIndex<>();
        List<String> recipes = new ArrayList<>(List.of("a", "b"));

        assertTrue(index.usable(recipes, 1L, true));
        recipes.remove("a");
        index.removed(recipes, "a");
        recipes.add("c");
        index.added(recipes, "c");

        assertTrue(index.usable(recipes, 1L, true));
        assertNull(index.find("a"));
        assertEquals("c", index.find("c"));
    }

    @Test
    void rebuildsWhenJeiReplacesTheCollection() {
        GenerationAwareRecipeIndex<String> index = new GenerationAwareRecipeIndex<>();
        List<String> first = new ArrayList<>(List.of("a"));
        List<String> replacement = new ArrayList<>(List.of("b"));

        assertTrue(index.usable(first, 1L, true));
        assertTrue(index.usable(replacement, 1L, true));
        assertNull(index.find("a"));
        assertEquals("b", index.find("b"));
    }
}