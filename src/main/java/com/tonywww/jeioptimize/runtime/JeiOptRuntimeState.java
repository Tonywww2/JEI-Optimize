package com.tonywww.jeioptimize.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class JeiOptRuntimeState {
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static final Object LOCK = new Object();

    private static long currentGeneration;
    private static final List<CompletableFuture<?>> pendingTasks = new ArrayList<>();
    private static final AtomicBoolean RUNTIME_UNLOADED_ONCE = new AtomicBoolean(false);

    private JeiOptRuntimeState() {
    }

    /**
     * Marks that JEI's runtime has been torn down at least once (a reload/rebuild has occurred).
     * Used to restrict main-thread parallel passes to the initial, exclusive startup only.
     */
    public static void markRuntimeUnloaded() {
        RUNTIME_UNLOADED_ONCE.set(true);
    }

    /**
     * @return whether JEI's runtime has been unloaded at least once (any rebuild after first start).
     */
    public static boolean hasRuntimeUnloadedOnce() {
        return RUNTIME_UNLOADED_ONCE.get();
    }

    public static long beginStart() {
        synchronized (LOCK) {
            cancelPendingTasksLocked();
            currentGeneration = NEXT_GENERATION.incrementAndGet();
            return currentGeneration;
        }
    }

    public static long currentGeneration() {
        synchronized (LOCK) {
            return currentGeneration;
        }
    }

    public static boolean isCurrent(long generation) {
        synchronized (LOCK) {
            return currentGeneration == generation;
        }
    }

    public static void invalidate() {
        synchronized (LOCK) {
            cancelPendingTasksLocked();
            currentGeneration = NEXT_GENERATION.incrementAndGet();
        }
    }

    public static void track(CompletableFuture<?> task) {
        if (task == null) {
            return;
        }
        synchronized (LOCK) {
            if (task.isDone()) {
                return;
            }
            pendingTasks.add(task);
        }
        task.whenComplete((ignoredResult, ignoredError) -> untrack(task));
    }

    public static void untrack(CompletableFuture<?> task) {
        synchronized (LOCK) {
            pendingTasks.remove(task);
        }
    }

    public static void cancelPendingTasks() {
        synchronized (LOCK) {
            cancelPendingTasksLocked();
        }
    }

    public static int pendingTaskCount() {
        synchronized (LOCK) {
            return pendingTasks.size();
        }
    }

    private static void cancelPendingTasksLocked() {
        if (pendingTasks.isEmpty()) {
            return;
        }
        List<CompletableFuture<?>> toCancel = List.copyOf(pendingTasks);
        pendingTasks.clear();
        for (CompletableFuture<?> task : toCancel) {
            task.cancel(false);
        }
    }
}