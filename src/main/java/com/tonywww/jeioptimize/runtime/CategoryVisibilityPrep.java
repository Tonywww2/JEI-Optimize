package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Builds the same visible category list JEI caches for an empty focus, one client tick at a time,
 * before {@code JeiGuiStarter.start} constructs {@code RecipesGui}.
 */
public final class CategoryVisibilityPrep {
    private CategoryVisibilityPrep() {
    }

    /**
     * Walks {@code categories} from {@code cursor} and appends entries that are not hidden.
     * Stops at {@code deadlineNanos} or at the end of the list. Returns the next cursor.
     */
    public static <T> int appendVisibleSlice(
        List<T> visible,
        List<T> categories,
        int cursor,
        Predicate<T> hidden,
        long deadlineNanos
    ) {
        while (cursor < categories.size() && System.nanoTime() < deadlineNanos) {
            T category = categories.get(cursor++);
            if (!hidden.test(category)) {
                visible.add(category);
            }
        }
        return cursor;
    }

    public static <T> List<T> visibleInOrder(List<T> categories, Predicate<T> hidden) {
        ArrayList<T> visible = new ArrayList<>();
        appendVisibleSlice(visible, categories, 0, hidden, Long.MAX_VALUE);
        return List.copyOf(visible);
    }

    public static void finishBeforeGuiRegistration() {
        if (!JeiOptExecutors.isJeiStartThread()) {
            return;
        }
        CategoryVisibilityHost.Worker manager = CategoryVisibilityHost.current();
        if (manager == null) {
            JeiOptimize.LOGGER.debug("JEI category prep skipped: recipe manager is not available");
            return;
        }
        if (manager.visibleCacheReady()) {
            return;
        }
        long generation = JeiOptRuntimeState.currentGeneration();
        AtomicBoolean failed = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        JeiOptClientTickQueue.enqueue(() -> {
            boolean done = slice(manager, generation, failed);
            if (done) {
                finished.set(true);
            }
            return done;
        });
        while (!manager.visibleCacheReady() && !failed.get() && !finished.get() && CategoryVisibilityHost.isOpen()) {
            JeiOptExecutors.checkJeiStartActive();
            if (!JeiOptRuntimeState.isCurrent(generation)) {
                return;
            }
            JeiOptClientTickQueue.awaitNextClientTick();
        }
    }

    private static boolean slice(CategoryVisibilityHost.Worker manager, long generation, AtomicBoolean failed) {
        if (!CategoryVisibilityHost.isOpen() || !JeiOptRuntimeState.isCurrent(generation)) {
            return true;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(JeiOptFeatureFlags.ingredientFilterBudgetMs());
        try {
            return manager.sliceVisibleCategories(deadline);
        } catch (RuntimeException | LinkageError failure) {
            failed.set(true);
            JeiOptimize.LOGGER.warn("JEI category prep failed; RecipesGui keeps JEI's own lookup", failure);
            return true;
        }
    }
}
