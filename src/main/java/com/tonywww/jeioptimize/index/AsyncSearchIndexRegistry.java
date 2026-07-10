package com.tonywww.jeioptimize.index;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plain (non-mixin) registry that binds a built async search index to a JEI {@code ElementSearch}
 * instance. Kept outside the mixin so both the injected read path and the startup driver reference
 * the same normal class, avoiding fragile external references to a mixin class.
 */
public final class AsyncSearchIndexRegistry {
    private static final Map<Object, AsyncSearchIndex> INDEXES = Collections.synchronizedMap(new WeakHashMap<>());

    private AsyncSearchIndexRegistry() {
    }

    public static void attach(Object elementSearch, AsyncSearchIndex asyncSearchIndex) {
        if (elementSearch == null || asyncSearchIndex == null) {
            return;
        }
        INDEXES.put(elementSearch, asyncSearchIndex);
    }

    public static void detach(Object elementSearch) {
        if (elementSearch != null) {
            INDEXES.remove(elementSearch);
        }
    }

    public static AsyncSearchIndex get(Object elementSearch) {
        if (elementSearch == null) {
            return null;
        }
        return INDEXES.get(elementSearch);
    }
}
