package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JeiOptTaskRegistry {
    private static final ConcurrentMap<String, CompletableFuture<?>> TASKS = new ConcurrentHashMap<>();

    private JeiOptTaskRegistry() {
    }

    public static <T> Optional<CompletableFuture<T>> submitIfEnabled(
        String taskId,
        BooleanSupplier featureEnabled,
        Supplier<T> worker,
        Consumer<T> publisher
    ) {
        Objects.requireNonNull(featureEnabled, "featureEnabled");
        if (!JeiOptFeatureFlags.enabled() || !featureEnabled.getAsBoolean()) {
            return Optional.empty();
        }
        return Optional.of(submit(taskId, worker, publisher));
    }

    public static <T> CompletableFuture<T> submit(String taskId, Supplier<T> worker, Consumer<T> publisher) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(publisher, "publisher");

        long generation = JeiOptRuntimeState.currentGeneration();
        CompletableFuture<T> task = JeiOptExecutors.supplyAsync(worker);
        CompletableFuture<?> previous = TASKS.put(taskId, task);
        if (previous != null) {
            previous.cancel(false);
            JeiOptRuntimeState.untrack(previous);
        }
        JeiOptRuntimeState.track(task);
        task.whenComplete((result, error) -> onTaskComplete(taskId, generation, task, result, error, publisher));
        return task;
    }

    public static void cancel(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        CompletableFuture<?> task = TASKS.remove(taskId);
        if (task != null) {
            task.cancel(false);
            JeiOptRuntimeState.untrack(task);
        }
    }

    public static void cancelAll() {
        for (String taskId : TASKS.keySet()) {
            cancel(taskId);
        }
    }

    public static int pendingTaskCount() {
        return TASKS.size();
    }

    private static <T> void onTaskComplete(
        String taskId,
        long generation,
        CompletableFuture<T> task,
        T result,
        Throwable error,
        Consumer<T> publisher
    ) {
        TASKS.remove(taskId, task);
        JeiOptRuntimeState.untrack(task);

        if (task.isCancelled()) {
            return;
        }
        if (error != null) {
            JeiOptimize.LOGGER.error("JEI Optimize async task '{}' failed", taskId, error);
            return;
        }

        JeiOptExecutors.executeOnMainThread(() -> {
            if (!JeiOptRuntimeState.isCurrent(generation)) {
                return;
            }
            publisher.accept(result);
        });
    }
}