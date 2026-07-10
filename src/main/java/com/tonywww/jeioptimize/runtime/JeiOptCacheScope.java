package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class JeiOptCacheScope {
    public static final String UIDS = "uids";
    public static final String STRINGS = "strings";
    public static final String SORT_KEYS = "sortKeys";
    public static final String TAG_COUNTS = "tagCounts";

    private static final Object LOCK = new Object();
    private static long generation = Long.MIN_VALUE;
    private static final Map<String, Map<Object, Object>> caches = new HashMap<>();

    private JeiOptCacheScope() {
    }

    public static void begin(long newGeneration) {
        synchronized (LOCK) {
            generation = newGeneration;
            caches.clear();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            generation = Long.MIN_VALUE;
            caches.clear();
        }
    }

    public static long generation() {
        synchronized (LOCK) {
            return generation;
        }
    }

    public static boolean isActiveFor(long expectedGeneration) {
        synchronized (LOCK) {
            return generation == expectedGeneration && JeiOptRuntimeState.isCurrent(expectedGeneration);
        }
    }

    public static int size(String namespace) {
        synchronized (LOCK) {
            return cache(namespace).size();
        }
    }

    public static int totalSize() {
        synchronized (LOCK) {
            int total = 0;
            for (Map<Object, Object> cache : caches.values()) {
                total += cache.size();
            }
            return total;
        }
    }

    public static <T> T getOrCompute(String namespace, Object key, Supplier<T> supplier) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");

        if (!JeiOptFeatureFlags.cacheScope()) {
            return supplier.get();
        }

        long currentGeneration = JeiOptRuntimeState.currentGeneration();
        synchronized (LOCK) {
            if (generation != currentGeneration) {
                generation = currentGeneration;
                caches.clear();
            }

            Map<Object, Object> cache = cache(namespace);
            Object cached = cache.get(key);
            if (cached != null || cache.containsKey(key)) {
                @SuppressWarnings("unchecked")
                T castCached = (T) cached;
                return castCached;
            }
        }

        T value = supplier.get();

        synchronized (LOCK) {
            if (generation == currentGeneration && JeiOptRuntimeState.isCurrent(currentGeneration)) {
                cache(namespace).put(key, value);
            }
        }

        return value;
    }

    public static void remove(String namespace, Object key) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        synchronized (LOCK) {
            cache(namespace).remove(key);
        }
    }

    private static Map<Object, Object> cache(String namespace) {
        return caches.computeIfAbsent(namespace, ignored -> new HashMap<>());
    }
}