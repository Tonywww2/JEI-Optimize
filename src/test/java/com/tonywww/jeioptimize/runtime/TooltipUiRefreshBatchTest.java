package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class TooltipUiRefreshBatchTest {
    public static void main(String[] arguments) {
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

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}