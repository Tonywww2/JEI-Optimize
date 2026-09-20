package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

public final class TooltipUiRefreshBatchTest {
    public static void main(String[] arguments) {
        verifyBookmarkPublication();
        Object grid = new Object();
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();
        check(!JeiOptUiRefreshBatch.defer(grid, refreshes::incrementAndGet), "outside startup is unchanged");
        int flushed = JeiOptUiRefreshBatch.run(() -> {
            for (int ordinal = 0; ordinal < 22140; ordinal++) {
                changes.incrementAndGet();
                check(JeiOptUiRefreshBatch.defer(grid, () -> {
                    check(changes.get() == 22140, "all mutations visible before refresh");
                    check(!JeiOptUiRefreshBatch.defer(grid, () -> {}), "flush outside deferral scope");
                    refreshes.incrementAndGet();
                }), "layout deferred");
            }
            JeiOptUiRefreshBatch.run(() -> JeiOptUiRefreshBatch.defer(grid, refreshes::incrementAndGet), () -> true);
            check(!CompletableFuture.supplyAsync(() -> JeiOptUiRefreshBatch.defer(grid, () -> {})).join(),
                "unrelated thread does not inherit UI batching");
            check(refreshes.get() == 0, "nested batch does not flush early");
        }, () -> true);
        check(flushed == 1 && refreshes.get() == 1, "one layout per grid");
        try {
            JeiOptUiRefreshBatch.run(() -> {
                JeiOptUiRefreshBatch.defer(grid, refreshes::incrementAndGet);
                throw new IllegalArgumentException("plugin failed");
            }, () -> true);
            throw new AssertionError("must propagate failure");
        } catch (IllegalArgumentException expected) {}
        try {
            JeiOptUiRefreshBatch.run(() -> JeiOptUiRefreshBatch.defer(grid, refreshes::incrementAndGet), () -> false);
            throw new AssertionError("must reject stale generation");
        } catch (CancellationException expected) {}
        check(refreshes.get() == 1 && !JeiOptUiRefreshBatch.defer(grid, refreshes::incrementAndGet), "failure releases scope");
        try {
            JeiOptUiRefreshBatch.run(() -> {
                JeiOptUiRefreshBatch.defer(grid, () -> { throw new IllegalStateException("layout failed"); });
                JeiOptUiRefreshBatch.defer(new Object(), refreshes::incrementAndGet);
            }, () -> true);
            throw new AssertionError("layout failure must propagate");
        } catch (IllegalStateException expected) {}
        check(refreshes.get() == 1 && !JeiOptUiRefreshBatch.defer(grid, () -> {}), "failed flush releases remaining layouts");
        System.out.println("TooltipUiRefreshBatchTest passed: 22140 mutations, one refresh, nested/failure/stale cleanup");
    }

    private static void verifyBookmarkPublication() {
        AtomicBoolean runtime = new AtomicBoolean();
        AtomicBoolean current = new AtomicBoolean(true);
        List<String> order = new ArrayList<>();
        Object bookmarks = new Object();
        Runnable renderBookmark = () -> {
            check(runtime.get(), "bookmark icon requires published JEI runtime");
            order.add("bookmark");
        };
        JeiOptStartupProgressState.begin(100);
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "startup render preparation blocked");
        JeiOptStartupProgressState.registerBuild(100, 1, 1);
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "indexing render blocked");
        JeiOptStartupProgressState.markReady(100);
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "ready filter is not ready runtime");
        JeiOptStartupProgressState.markPublished(100);
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "filter installation does not unblock bookmarks");
        int flushed = JeiOptUiRefreshBatch.runAndPublish(() -> {
            order.add("callbacks");
            check(!runtime.get(), "plugin callback publication order unchanged");
            JeiOptUiRefreshBatch.defer(bookmarks, renderBookmark);
            JeiOptUiRefreshBatch.defer(bookmarks, renderBookmark);
        }, () -> {
            order.add("publish");
            runtime.set(true);
        }, current::get);
        check(flushed == 1 && order.equals(List.of("callbacks", "publish", "bookmark")), "layout flush follows publication");
        check(JeiOptStartupProgressState.blocksJeiRendering(true), "startup completion still required");
        JeiOptStartupProgressState.markRuntimeComplete(100);
        check(!JeiOptStartupProgressState.blocksJeiRendering(true), "next render may update bookmark bounds");
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "removed runtime remains blocked even with hidden progress");
        JeiOptStartupProgressState.begin(101);
        check(JeiOptStartupProgressState.blocksJeiRendering(true), "reload blocks old runtime rendering");
        JeiOptStartupProgressState.cancel(101);
        JeiOptStartupProgressState.markRuntimeComplete(100);
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "cancel/stale completion cannot render missing runtime");
        JeiOptStartupProgressState.begin(102);
        JeiOptStartupProgressState.fail(102, new IllegalStateException("startup failed"));
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "failed startup remains guarded");
        runtime.set(false);
        order.clear();
        try {
            JeiOptUiRefreshBatch.runAndPublish(() -> {
                JeiOptUiRefreshBatch.defer(bookmarks, renderBookmark);
                current.set(false);
            }, () -> { throw new AssertionError("stale runtime published"); }, current::get);
            throw new AssertionError("cancelled callbacks accepted");
        } catch (CancellationException expected) {}
        check(order.isEmpty(), "cancel discards bookmark refresh");
        current.set(true);
        try {
            JeiOptUiRefreshBatch.runAndPublish(() -> JeiOptUiRefreshBatch.defer(bookmarks, renderBookmark),
                () -> { throw new IllegalStateException("publication failed"); }, current::get);
            throw new AssertionError("publication failure swallowed");
        } catch (IllegalStateException expected) {}
        check(order.isEmpty(), "failed publication discards bookmark refresh");
        check(!JeiOptUiRefreshBatch.defer(bookmarks, renderBookmark), "failed publication releases scope");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}