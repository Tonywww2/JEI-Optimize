package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.recipe.ItemStackRepresentativeSelector;
import com.tonywww.jeioptimize.recipe.RepresentativeItemLimiter;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CelestialForgeReinforceInputPool {
    private static final String REINFORCE_INTERFACE =
        "com.xiaoyue.celestial_forge.content.reinforce.IReinforce";
    private static final String TYPE_TEST_UTILS = "com.xiaoyue.celestial_forge.utils.TypeTestUtils";

    private static final Object LOCK = new Object();
    private static final Map<PoolKey, Ingredient> INPUTS = new HashMap<>();
    private static long generation = Long.MIN_VALUE;
    private static Object sourceMap;
    private static int sourceSize = -1;
    private static boolean failureLogged;

    private CelestialForgeReinforceInputPool() {
    }

    public static Ingredient getOrCreate(Object recipe, boolean aggressive, int representativeLimit) {
        if (recipe == null) {
            return null;
        }

        try {
            Object output = recipe.getClass().getMethod("output").invoke(recipe);
            if (output == null) {
                return null;
            }
            Method isInput = output.getClass().getMethod("isInput", ItemStack.class);
            if (!REINFORCE_INTERFACE.equals(isInput.getDeclaringClass().getName())) {
                return null;
            }
            Object rawTypes = output.getClass().getMethod("types").invoke(output);
            if (!(rawTypes instanceof List<?> types) || types.isEmpty() || types.stream().anyMatch(type -> type == null)) {
                return null;
            }

            ClassLoader classLoader = recipe.getClass().getClassLoader();
            Class<?> typeTestUtils = Class.forName(TYPE_TEST_UTILS, false, classLoader);
            Field cacheField = typeTestUtils.getField("CACHE");
            Object rawCandidates = cacheField.get(null);
            if (!(rawCandidates instanceof Map<?, ?> candidates) || candidates.isEmpty()) {
                return null;
            }

            int limit = aggressive ? Math.max(1, representativeLimit) : 0;
            PoolKey key = new PoolKey(Set.copyOf(types), limit);
            synchronized (LOCK) {
                resetIfStale(candidates);
                Ingredient cached = INPUTS.get(key);
                if (cached != null) {
                    return copyNonEmpty(cached);
                }

                Ingredient built = build(output, isInput, candidates, aggressive, limit);
                Ingredient stored = copyNonEmpty(built);
                if (stored == null) {
                    return null;
                }
                INPUTS.put(key, stored);
                return copyNonEmpty(stored);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logFailureOnce(e);
            return null;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            INPUTS.clear();
            generation = Long.MIN_VALUE;
            sourceMap = null;
            sourceSize = -1;
            failureLogged = false;
        }
    }

    private static Ingredient build(
        Object output,
        Method isInput,
        Map<?, ?> candidates,
        boolean aggressive,
        int limit
    ) throws ReflectiveOperationException {
        int fullMatchCapacity = aggressive ? Math.min(candidates.size(), limit + 1) : candidates.size();
        List<ItemStack> fullMatches = new ArrayList<>(fullMatchCapacity);
        List<ItemStack> representatives = aggressive ? new ArrayList<>(limit) : fullMatches;
        RepresentativeItemLimiter limiter = aggressive ? new RepresentativeItemLimiter(limit) : null;
        Object group = aggressive ? new Object() : null;
        int matchingCount = 0;

        for (Object candidate : candidates.keySet()) {
            if (!(candidate instanceof Item item)) {
                return null;
            }
            ItemStack stack = item.getDefaultInstance();
            if (!Boolean.TRUE.equals(isInput.invoke(output, stack))) {
                continue;
            }
            matchingCount++;
            if (!aggressive || matchingCount <= limit + 1) {
                fullMatches.add(stack);
            }
            if (aggressive && limiter.shouldKeep(group, ItemStackRepresentativeSelector.itemKey(stack))) {
                representatives.add(stack);
            }
            if (aggressive && matchingCount > limit && representatives.size() == limit) {
                break;
            }
        }

        List<ItemStack> selected = chooseMatches(aggressive, limit, fullMatches, representatives);
        return selected.isEmpty() ? null : Ingredient.of(selected.stream());
    }

    static <T> List<T> chooseMatches(
        boolean aggressive,
        int limit,
        List<T> firstMatches,
        List<T> representatives
    ) {
        return aggressive && firstMatches.size() > limit ? representatives : firstMatches;
    }

    private static void resetIfStale(Map<?, ?> candidates) {
        long currentGeneration = JeiOptRuntimeState.currentGeneration();
        if (generation == currentGeneration && sourceMap == candidates && sourceSize == candidates.size()) {
            return;
        }
        INPUTS.clear();
        generation = currentGeneration;
        sourceMap = candidates;
        sourceSize = candidates.size();
        failureLogged = false;
    }

    private static Ingredient copyNonEmpty(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return null;
        }
        return Ingredient.of(java.util.Arrays.stream(items).map(ItemStack::copy));
    }

    private static void logFailureOnce(Throwable error) {
        synchronized (LOCK) {
            if (failureLogged) {
                return;
            }
            failureLogged = true;
        }
        JeiOptimize.LOGGER.warn(
            "Could not build the shared Celestial Forge reinforce input pool; keeping the original recipe behavior",
            error
        );
    }

    private record PoolKey(Set<?> types, int representativeLimit) {
    }
}