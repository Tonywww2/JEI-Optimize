package com.tonywww.jeioptimize.integration;

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
        return entry == null ? null : copy(entry.input());
    }

    public static Ingredient getResult(Object recipe) {
        CacheEntry entry = CACHE.get(recipe);
        return entry == null ? null : copy(entry.result());
    }

    public static void putInput(Object recipe, Ingredient input) {
        if (recipe == null || input == null) {
            return;
        }
        synchronized (CACHE) {
            CacheEntry entry = CACHE.get(recipe);
            CACHE.put(recipe, new CacheEntry(copy(input), entry == null ? null : entry.result()));
        }
    }

    public static void putResult(Object recipe, Ingredient result) {
        if (recipe == null || result == null) {
            return;
        }
        synchronized (CACHE) {
            CacheEntry entry = CACHE.get(recipe);
            CACHE.put(recipe, new CacheEntry(entry == null ? null : entry.input(), copy(result)));
        }
    }

    private static Ingredient copy(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        return Ingredient.of(Arrays.stream(ingredient.getItems()).map(net.minecraft.world.item.ItemStack::copy));
    }

    private record CacheEntry(Ingredient input, Ingredient result) {
    }
}