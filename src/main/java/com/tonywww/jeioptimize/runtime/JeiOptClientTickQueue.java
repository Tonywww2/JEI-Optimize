package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class JeiOptClientTickQueue {
    private static final Object LOCK = new Object();
    private static final Queue<BooleanSupplier> WORK = new ArrayDeque<>();

    private JeiOptClientTickQueue() {
    }

    public static void enqueue(BooleanSupplier work) {
        Objects.requireNonNull(work, "work");
        synchronized (LOCK) {
            WORK.add(work);
        }
    }

    public static void enqueue(Runnable work) {
        Objects.requireNonNull(work, "work");
        enqueue(() -> {
            work.run();
            return true;
        });
    }

    public static int size() {
        synchronized (LOCK) {
            return WORK.size();
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            WORK.clear();
        }
    }

    public static void drainForCurrentTick() {
        boolean deferredFilter = JeiOptFeatureFlags.deferredIngredientFilter();
        boolean asyncFilter = JeiOptFeatureFlags.asyncIngredientFilter();
        boolean chunking = JeiOptFeatureFlags.snapshotChunking();
        if (!deferredFilter && !asyncFilter && !chunking) {
            clear();
            return;
        }

        int budgetMs = (deferredFilter || asyncFilter)
            ? Math.max(JeiOptFeatureFlags.ingredientFilterBudgetMs(), JeiOptFeatureFlags.snapshotBudgetMs())
            : JeiOptFeatureFlags.snapshotBudgetMs();
        long budgetNanos = TimeUnit.MILLISECONDS.toNanos(budgetMs);
        long deadline = System.nanoTime() + budgetNanos;

        int remaining = size();
        while (remaining > 0 && System.nanoTime() < deadline) {
            BooleanSupplier work = poll();
            if (work == null) {
                return;
            }
            remaining--;

            boolean complete;
            try {
                complete = work.getAsBoolean();
            } catch (RuntimeException | LinkageError e) {
                JeiOptimize.LOGGER.error("JEI Optimize client tick work failed; dropping task", e);
                continue;
            }

            if (!complete) {
                synchronized (LOCK) {
                    WORK.add(work);
                }
            }
        }
    }

    private static BooleanSupplier poll() {
        synchronized (LOCK) {
            return WORK.poll();
        }
    }
}