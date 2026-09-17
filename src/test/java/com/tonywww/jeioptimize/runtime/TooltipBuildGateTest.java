package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CompletableFuture;

public final class TooltipBuildGateTest {
    public static void main(String[] arguments) {
        JeiOptFilterBuildGate gate = new JeiOptFilterBuildGate();
        gate.begin(1);
        CompletableFuture<Void> capture = new CompletableFuture<>();
        CompletableFuture<Void> publication = new CompletableFuture<>();
        check(gate.register(1, capture) && gate.register(1, publication), "register");
        CompletableFuture<Void> completion = gate.completion(1);
        capture.complete(null);
        check(!completion.isDone(), "capture is not publication");
        publication.complete(null);
        check(completion.isDone() && !completion.isCompletedExceptionally(), "full completion");
        gate.begin(2);
        CompletableFuture<Void> failing = new CompletableFuture<>();
        gate.register(2, failing);
        failing.completeExceptionally(new IllegalStateException("test"));
        check(gate.completion(2).isCompletedExceptionally(), "failure must propagate");
        check(gate.completion(2).isCompletedExceptionally(), "publication recheck cannot forget failed build");
        gate.begin(3);
        CompletableFuture<Void> old = new CompletableFuture<>();
        gate.register(3, old);
        CompletableFuture<Void> oldCompletion = gate.completion(3);
        gate.begin(4);
        check(old.isCancelled() && oldCompletion.isCompletedExceptionally(), "cancel old generation");
        CompletableFuture<Void> stale = new CompletableFuture<>();
        check(!gate.register(3, stale) && stale.isCancelled(), "reject stale registration");
        check(gate.completion(3).isCancelled(), "reject stale wait");
        check(!old.complete(null), "late result cannot revive old build");
        gate.clear();
        check(gate.completion(4).isCancelled(), "clear");
        JeiOptTooltipCache cache = new JeiOptTooltipCache(200);
        cache.put(0, java.util.List.of());
        check(cache.get(0) != null && cache.get(0).isEmpty(), "cached empty is not a miss");
        cache.put(1, java.util.List.of("first"));
        cache.put(1, java.util.List.of("replacement"));
        check(cache.get(1).equals(java.util.List.of("first")), "build snapshot is stable");
        cache.put(2, java.util.List.of("over budget"));
        check(cache.get(2) == null && cache.weight() <= 200, "bounded cache bypass");
        cache.clear();
        check(cache.get(0) == null && cache.weight() == 0, "cache teardown");
        System.out.println("TooltipBuildGateTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}