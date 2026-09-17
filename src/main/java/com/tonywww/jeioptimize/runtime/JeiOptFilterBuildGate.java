package com.tonywww.jeioptimize.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class JeiOptFilterBuildGate {
    private long generation = Long.MIN_VALUE;
    private final List<CompletableFuture<?>> builds = new ArrayList<>();

    public synchronized void begin(long generation) {
        clear();
        this.generation = generation;
    }

    public synchronized boolean register(long generation, CompletableFuture<?> build) {
        if (this.generation != generation) {
            build.cancel(false);
            return false;
        }
        builds.add(build);
        return true;
    }

    public synchronized CompletableFuture<Void> completion(long generation) {
        if (this.generation != generation) {
            CompletableFuture<Void> cancelled = new CompletableFuture<>();
            cancelled.cancel(false);
            return cancelled;
        }
        CompletableFuture<Void> completion = CompletableFuture.allOf(builds.toArray(CompletableFuture[]::new));
        builds.removeIf(build -> build.isDone() && !build.isCompletedExceptionally());
        return completion;
    }

    public synchronized void clear() {
        generation = Long.MIN_VALUE;
        List<CompletableFuture<?>> pending = List.copyOf(builds);
        builds.clear();
        pending.forEach(build -> build.cancel(false));
    }
}