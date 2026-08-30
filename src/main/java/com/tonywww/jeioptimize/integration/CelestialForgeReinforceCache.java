package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class CelestialForgeReinforceCache {
    private static final Map<Object, CacheEntry> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private CelestialForgeReinforceCache() {
    }

    public static Ingredient getInput(Object recipe) {
        CacheEntry entry = CACHE.get(recipe);
        return isCurrent(entry) ? copyNonEmpty(entry.input()) : null;
    }

    public static Ingredient getResult(Object recipe) {
        CacheEntry entry = CACHE.get(recipe);
        return isCurrent(entry) ? copyNonEmpty(entry.result()) : null;
    }

    public static void putInput(Object recipe, Ingredient input) {
        Ingredient cachedInput = copyNonEmpty(input);
        if (recipe == null || cachedInput == null) {
            return;
        }
        synchronized (CACHE) {
            CacheEntry entry = CACHE.get(recipe);
            long generation = JeiOptRuntimeState.currentGeneration();
            Ingredient result = entry != null && entry.generation() == generation ? entry.result() : null;
            CACHE.put(recipe, new CacheEntry(generation, cachedInput, result));
        }
    }

    public static void putResult(Object recipe, Ingredient result) {
        Ingredient cachedResult = copyNonEmpty(result);
        if (recipe == null || cachedResult == null) {
            return;
        }
        synchronized (CACHE) {
            CacheEntry entry = CACHE.get(recipe);
            long generation = JeiOptRuntimeState.currentGeneration();
            Ingredient input = entry != null && entry.generation() == generation ? entry.input() : null;
            CACHE.put(recipe, new CacheEntry(generation, input, cachedResult));
        }
    }

    private static boolean isCurrent(CacheEntry entry) {
        return entry != null && entry.generation() == JeiOptRuntimeState.currentGeneration();
    }

    private static Ingredient copyNonEmpty(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        net.minecraft.world.item.ItemStack[] items = ingredient.getItems();
        if (!isCacheableItemCount(items.length)) {
            return null;
        }
        return Ingredient.of(Arrays.stream(items).map(net.minecraft.world.item.ItemStack::copy));
    }

    static boolean isCacheableItemCount(int itemCount) {
        return itemCount > 0;
    }

    private record CacheEntry(long generation, Ingredient input, Ingredient result) {
    }
}