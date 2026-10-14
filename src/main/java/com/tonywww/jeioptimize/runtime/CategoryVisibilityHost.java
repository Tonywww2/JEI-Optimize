package com.tonywww.jeioptimize.runtime;

/**
 * Lifecycle holder for the current recipe-manager prep. Ordinary classes talk only to this type.
 * The Mixin implementation registers and abandons itself here.
 */
public final class CategoryVisibilityHost {
    public interface Worker {
        boolean visibleCacheReady();

        /**
         * @return {@code true} when this worker will not publish anything further
         */
        boolean sliceVisibleCategories(long deadlineNanos);

        /** Drops an in-progress list and invalidates a cache this prep published. */
        void abandon();
    }

    private static Worker current;
    private static boolean open;

    private CategoryVisibilityHost() {
    }

    public static void attach(Worker worker) {
        current = worker;
        open = true;
    }

    public static Worker current() {
        return open ? current : null;
    }

    public static boolean isOpen() {
        return open;
    }

    public static void clear() {
        open = false;
        Worker worker = current;
        current = null;
        if (worker != null) {
            worker.abandon();
        }
    }
}
