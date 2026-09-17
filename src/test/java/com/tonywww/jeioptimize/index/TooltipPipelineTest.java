package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class TooltipPipelineTest {
    public static void main(String[] arguments) throws Exception {
        ArrayDeque<Runnable> scheduled = new ArrayDeque<>();
        List<Integer> results = new ArrayList<>();
        TooltipIndexPipeline pipeline = new TooltipIndexPipeline(scheduled::add, 2, 4096, true,
            (text, ordinal) -> results.add(ordinal));
        check(pipeline.offer(batch(0)) && pipeline.offer(batch(1)), "two queued batches");
        check(!pipeline.offer(batch(2)), "full queue yields without loss");
        check(scheduled.size() == 1, "single scheduled writer");
        scheduled.remove().run();
        check(!pipeline.completion().isDone(), "idle is not end of input");
        check(pipeline.offer(batch(2)), "restart idle writer");
        pipeline.closeInput();
        check(!pipeline.completion().isDone(), "close waits for last batch");
        scheduled.remove().run();
        pipeline.completion().join();
        check(results.equals(List.of(0, 1, 2)), "every ordinal in order exactly once");
        check(pipeline.metrics().bytes() == 0 && pipeline.metrics().peakQueued() == 2, "accounting");

        TooltipIndexPipeline oversized = new TooltipIndexPipeline(scheduled::add, 2, 1, false, (text, ordinal) -> {});
        check(oversized.offer(batch(0)) && !oversized.offer(batch(1)), "oversized element exclusively admitted");
        oversized.cancel();
        check(oversized.completion().isCancelled() && oversized.metrics().bytes() == 0, "cancel before execution");
        scheduled.remove().run();

        TooltipIndexPipeline failing = new TooltipIndexPipeline(scheduled::add, 2, 4096, false,
            (text, ordinal) -> { throw new IllegalStateException("test writer failure"); });
        failing.offer(batch(0));
        failing.offer(batch(1));
        scheduled.remove().run();
        check(failing.failure() != null && failing.completion().isCompletedExceptionally(), "failure acknowledged");
        check(failing.metrics().bytes() == 0, "failed writer releases queue");
        TooltipIndexPipeline rejected = new TooltipIndexPipeline(command -> { throw new java.util.concurrent.RejectedExecutionException(); },
            2, 4096, false, (text, ordinal) -> {});
        rejected.offer(batch(0));
        check(rejected.completion().isCompletedExceptionally() && rejected.metrics().bytes() == 0, "executor rejection");

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger writers = new AtomicInteger();
        AtomicInteger maxWriters = new AtomicInteger();
        TooltipIndexPipeline active = new TooltipIndexPipeline(executor, 2, 4096, false, (text, ordinal) -> {
            maxWriters.accumulateAndGet(writers.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) { throw new AssertionError("release timeout"); }
            } catch (InterruptedException error) {
                throw new AssertionError(error);
            } finally {
                writers.decrementAndGet();
            }
        });
        try {
            active.offer(batch(0));
            check(entered.await(5, TimeUnit.SECONDS), "writer started");
            active.offer(batch(1));
            active.offer(batch(2));
            active.cancel();
            check(!active.completion().isDone(), "cancel is not writer exit acknowledgement");
            release.countDown();
            try { active.completion().get(5, TimeUnit.SECONDS); } catch (java.util.concurrent.CancellationException expected) {}
            check(active.metrics().bytes() == 0 && maxWriters.get() == 1, "single writer and teardown");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }

        AtomicInteger count = new AtomicInteger();
        TooltipIndexPipeline inline = new TooltipIndexPipeline(Runnable::run, 2, 4096, false,
            (text, ordinal) -> count.incrementAndGet());
        for (int ordinal = 0; ordinal < 512; ordinal++) {
            check(inline.offer(batch(ordinal)), "budgeted producer may continue after submission");
        }
        inline.closeInput();
        check(count.get() == 512 && inline.completion().isDone(), "no 128-item per-tick limit");
        AtomicInteger produced = new AtomicInteger();
        TooltipIndexPipeline fast = new TooltipIndexPipeline(Runnable::run, 2, 1024 * 1024, false,
            (text, ordinal) -> produced.incrementAndGet());
        TooltipSnapshotProducer producer = new TooltipSnapshotProducer(fast, 180159);
        TooltipSnapshotProducer.Step step = producer.pump(() -> true,
            ordinal -> new TooltipSearchSnapshot(ordinal, List.of("lightweight")));
        check(step.processed() == 180159 && produced.get() == 180159 && producer.finished(),
            "actual producer exceeds 128 per tick when budget permits");
        fast.completion().join();
        TooltipIndexPipeline slow = new TooltipIndexPipeline(scheduled::add, 2, 1024 * 1024, false, (text, ordinal) -> {});
        TooltipSnapshotProducer bounded = new TooltipSnapshotProducer(slow, 600);
        var first = bounded.pump(() -> true, ordinal -> new TooltipSearchSnapshot(ordinal, List.of("bounded")));
        check(first.processed() == 256 && first.backpressured(), "real producer yields on full two-batch queue");
        scheduled.remove().run();
        var second = bounded.pump(() -> false, ordinal -> new TooltipSearchSnapshot(ordinal, List.of("bounded")));
        check(second.processed() == 1, "preserve per-element budget check");
        scheduled.remove().run();
        while (!bounded.finished()) {
            bounded.pump(() -> true, ordinal -> new TooltipSearchSnapshot(ordinal, List.of("bounded")));
            while (!scheduled.isEmpty()) { scheduled.remove().run(); }
        }
        check(slow.completion().isDone(), "final partial batch drained");
        List<Integer> hugeResults = new ArrayList<>();
        AtomicInteger hugeExtractions = new AtomicInteger();
        TooltipIndexPipeline hugePipeline = new TooltipIndexPipeline(scheduled::add, 2, 256, false,
            (text, ordinal) -> hugeResults.add(ordinal));
        TooltipSnapshotProducer hugeProducer = new TooltipSnapshotProducer(hugePipeline, 3);
        while (!hugeProducer.finished()) {
            hugeProducer.pump(() -> true, ordinal -> {
                hugeExtractions.incrementAndGet();
                return new TooltipSearchSnapshot(ordinal, List.of(ordinal == 1 ? "x".repeat(300000) : "small"));
            });
            while (!scheduled.isEmpty()) { scheduled.remove().run(); }
        }
        hugePipeline.completion().join();
        check(hugeExtractions.get() == 3 && hugeResults.equals(List.of(0, 1, 2)), "oversize pending retained without recapture or loss");
        check(hugePipeline.metrics().bytes() == 0, "oversize memory released");
        TooltipIndexPipeline empty = new TooltipIndexPipeline(scheduled::add, 2, 1024, false, (text, ordinal) -> {});
        new TooltipSnapshotProducer(empty, 0).pump(() -> true, ordinal -> { throw new AssertionError("empty getter"); });
        check(empty.completion().isDone() && scheduled.isEmpty(), "empty input completes without worker");
        var raceExecutor = Executors.newFixedThreadPool(4);
        AtomicInteger racingWriters = new AtomicInteger();
        AtomicInteger racingMax = new AtomicInteger();
        AtomicInteger nextOrdinal = new AtomicInteger();
        TooltipIndexPipeline racing = new TooltipIndexPipeline(raceExecutor, 2, 8192, false, (text, ordinal) -> {
            racingMax.accumulateAndGet(racingWriters.incrementAndGet(), Math::max);
            try {
                check(ordinal == nextOrdinal.getAndIncrement(), "concurrent drain preserves order");
            } finally {
                racingWriters.decrementAndGet();
            }
        });
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            for (int ordinal = 0; ordinal < 20000; ordinal++) {
                var next = batch(ordinal);
                while (!racing.offer(next)) {
                    check(System.nanoTime() < deadline, "enqueue/drain race timeout");
                    Thread.yield();
                }
            }
            racing.closeInput();
            racing.completion().get(5, TimeUnit.SECONDS);
            check(nextOrdinal.get() == 20000 && racingMax.get() == 1, "no lost wakeup or simultaneous writer");
        } finally {
            racing.cancel();
            raceExecutor.shutdownNow();
        }
        System.out.println("TooltipPipelineTest passed: queue, bytes, drain, cancellation acknowledgement, failure, idle restart");
    }

    private static TooltipIndexPipeline.Batch batch(int ordinal) {
        return new TooltipIndexPipeline.Batch(List.of(new TooltipSearchSnapshot(ordinal, List.of(" text "))));
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}