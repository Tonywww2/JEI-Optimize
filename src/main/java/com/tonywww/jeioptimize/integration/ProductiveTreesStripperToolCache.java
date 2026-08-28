package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

public final class ProductiveTreesStripperToolCache {
    private static volatile long generation = Long.MIN_VALUE;
    private static volatile Ingredient ingredient;

    private ProductiveTreesStripperToolCache() {
    }

    public static Ingredient get(Supplier<Ingredient> factory) {
        long currentGeneration = JeiOptRuntimeState.currentGeneration();
        Ingredient cached = ingredient;
        if (cached != null && generation == currentGeneration) {
            return cached;
        }
        synchronized (ProductiveTreesStripperToolCache.class) {
            if (ingredient == null || generation != currentGeneration) {
                ingredient = factory.get();
                generation = currentGeneration;
            }
            return ingredient;
        }
    }
}