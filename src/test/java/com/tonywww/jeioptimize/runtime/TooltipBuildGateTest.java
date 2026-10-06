package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

public final class TooltipBuildGateTest {
    public static void main(String[] arguments) {
        verifyPublicationProgress();
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

    private static void verifyPublicationProgress() {
        JeiOptStartupProgressState.begin(100);
        JeiOptStartupProgressState.registerBuild(100, 2, 1000);
        CompletableFuture<Void> installed = new CompletableFuture<>();
        JeiOptStartupProgressState.trackPublication(100, installed);
        CompletableFuture<Void> waiting = JeiOptStartupProgressState.publicationFuture(100);
        JeiOptStartupProgressState.markReady(100);
        check(!waiting.isDone(), "ready index must still wait for sealing and installation");
        IllegalStateException failure = new IllegalStateException("filter installation failed");
        installed.completeExceptionally(new CompletionException(failure));
        check(waiting.isDone(), "installation failure must release the runtime publication wait");
        try {
            waiting.join();
            throw new AssertionError("publication failure swallowed");
        } catch (CompletionException expected) {
            check(expected.getCause() == failure, "original publication failure retained");
        }
        JeiOptStartupProgressState.markChunkCompleted(100);
        JeiOptStartupProgressState.markReady(100);
        JeiOptStartupProgressState.markPublished(100);
        JeiOptStartupProgressState.markRuntimeComplete(100);
        check(!JeiOptStartupProgressState.registerBuild(100, 1, 1), "failed generation rejects new builds");
        check(!JeiOptStartupProgressState.snapshot().visible()
            && !JeiOptStartupProgressState.blocksJeiInput(), "late callbacks cannot revive failed progress");
        check(JeiOptStartupProgressState.publicationFuture(100) == waiting,
            "later runtime publication must still observe the failure");

        JeiOptStartupProgressState.begin(101);
        JeiOptStartupProgressState.registerBuild(101, 1, 1);
        CompletableFuture<Void> cancelledBuild = new CompletableFuture<>();
        CompletableFuture<Void> cancelledPublication = cancelledBuild.thenRun(() -> {});
        JeiOptStartupProgressState.trackPublication(101, cancelledPublication);
        CompletableFuture<Void> cancelledWait = JeiOptStartupProgressState.publicationFuture(101);
        cancelledBuild.cancel(false);
        check(cancelledWait.isCancelled(), "dependent build cancellation cancels the publication wait");
        try {
            cancelledWait.join();
            throw new AssertionError("cancelled publication accepted");
        } catch (CancellationException expected) {}
        JeiOptStartupProgressState.markReady(101);
        check(!JeiOptStartupProgressState.snapshot().visible(), "late ready cannot revive cancellation");

        JeiOptStartupProgressState.begin(102);
        JeiOptStartupProgressState.registerBuild(102, 1, 1);
        CompletableFuture<Void> oldPublication = new CompletableFuture<>();
        JeiOptStartupProgressState.trackPublication(102, oldPublication);
        CompletableFuture<Void> oldWait = JeiOptStartupProgressState.publicationFuture(102);
        JeiOptStartupProgressState.begin(103);
        JeiOptStartupProgressState.registerBuild(103, 1, 1);
        CompletableFuture<Void> currentPublication = new CompletableFuture<>();
        JeiOptStartupProgressState.trackPublication(103, currentPublication);
        CompletableFuture<Void> currentWait = JeiOptStartupProgressState.publicationFuture(103);
        check(oldWait.isCancelled(), "restart releases previous publication waiters");
        oldPublication.completeExceptionally(failure);
        check(!currentWait.isDone() && JeiOptStartupProgressState.snapshot().visible(),
            "stale publication failure cannot cancel a newer startup");
        JeiOptStartupProgressState.markReady(103);
        currentPublication.complete(null);
        check(currentWait.isDone() && !currentWait.isCompletedExceptionally(), "successful installation releases wait");
        check(JeiOptStartupProgressState.snapshot().stage() == JeiOptStartupProgressState.Stage.PUBLISHED,
            "runtime callbacks remain guarded until startup finishes");
        JeiOptStartupProgressState.markReady(103);
        check(JeiOptStartupProgressState.snapshot().stage() == JeiOptStartupProgressState.Stage.PUBLISHED,
            "late ready cannot regress publication");
        JeiOptStartupProgressState.markRuntimeComplete(103);
        JeiOptStartupProgressState.markReady(103);
        JeiOptStartupProgressState.markPublished(103);
        check(!JeiOptStartupProgressState.snapshot().visible(), "finished runtime stays hidden");

        JeiOptStartupProgressState.begin(104);
        JeiOptStartupProgressState.registerBuild(104, 1, 1);
        CompletableFuture<Void> alreadyFailed = CompletableFuture.failedFuture(failure);
        JeiOptStartupProgressState.trackPublication(104, alreadyFailed);
        check(JeiOptStartupProgressState.publicationFuture(104).isCompletedExceptionally(),
            "failure before observer registration also releases publication");
        JeiOptStartupProgressState.cancel(104);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
