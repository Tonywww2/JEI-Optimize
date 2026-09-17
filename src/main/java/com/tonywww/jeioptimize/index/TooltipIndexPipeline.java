package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public final class TooltipIndexPipeline {
    private final Executor executor;
    private final int maxQueuedBatches;
    private final long maxBytes;
    private final boolean trim;
    private final ArrayDeque<Batch> queue = new ArrayDeque<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private BiConsumer<String, Integer> sink;
    private boolean scheduled;
    private boolean running;
    private boolean closed;
    private boolean cancelled;
    private Throwable failure;
    private long bytes;
    private long characters;
    private long peakBytes;
    private long peakCharacters;
    private int peakQueued;
    private int submitted;
    private int consumed;
    private long workerNanos;

    public TooltipIndexPipeline(Executor executor, int maxQueuedBatches, long maxBytes,
        boolean trim, BiConsumer<String, Integer> sink) {
        this.executor = Objects.requireNonNull(executor);
        this.maxQueuedBatches = Math.max(1, maxQueuedBatches);
        this.maxBytes = Math.max(1, maxBytes);
        this.trim = trim;
        this.sink = Objects.requireNonNull(sink);
    }

    public synchronized boolean hasCapacity(long assembledBytes) {
        checkOpen();
        return queue.size() < maxQueuedBatches
            && (bytes == 0 || assembledBytes < maxBytes - bytes);
    }

    public boolean offer(Batch batch) {
        boolean startWriter;
        synchronized (this) {
            checkOpen();
            if (queue.size() >= maxQueuedBatches || (bytes != 0 && batch.bytes() > maxBytes - bytes)) {
                return false;
            }
            queue.add(batch);
            bytes += batch.bytes();
            characters += batch.characters();
            peakBytes = Math.max(peakBytes, bytes);
            peakCharacters = Math.max(peakCharacters, characters);
            peakQueued = Math.max(peakQueued, queue.size());
            submitted++;
            startWriter = !scheduled;
            scheduled = true;
        }
        if (startWriter) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException failure) {
                synchronized (this) {
                    fail(failure);
                    scheduled = false;
                    finishIfStopped();
                }
            }
        }
        return true;
    }

    public synchronized void closeInput() {
        closed = true;
        finishIfStopped();
    }

    public synchronized void cancel() {
        cancelled = true;
        discardQueued();
        finishIfStopped();
    }

    public synchronized Throwable failure() {
        return failure;
    }

    public CompletableFuture<Void> completion() {
        return completion;
    }

    public synchronized Metrics metrics() {
        return new Metrics(submitted, consumed, peakQueued, bytes, characters, peakBytes, peakCharacters, workerNanos);
    }

    private void drain() {
        synchronized (this) {
            if (cancelled || failure != null) {
                scheduled = false;
                finishIfStopped();
                return;
            }
            running = true;
        }
        while (true) {
            Batch batch;
            synchronized (this) {
                batch = queue.poll();
                if (batch == null) {
                    running = false;
                    scheduled = false;
                    finishIfStopped();
                    return;
                }
            }
            long started = System.nanoTime();
            try {
                for (TooltipSearchSnapshot snapshot : batch.snapshots()) {
                    for (String original : snapshot.tooltipStrings()) {
                        synchronized (this) {
                            if (cancelled || failure != null || Thread.currentThread().isInterrupted()) {
                                throw new java.util.concurrent.CancellationException("Tooltip writer cancelled");
                            }
                        }
                        String text = trim ? original.trim() : original;
                        if (!text.isEmpty()) {
                            sink.accept(text, snapshot.elementOrdinal());
                        }
                    }
                }
                synchronized (this) {
                    consumed++;
                }
            } catch (Throwable error) {
                synchronized (this) {
                    if (!cancelled) {
                        fail(error);
                    }
                }
            } finally {
                synchronized (this) {
                    workerNanos += System.nanoTime() - started;
                    bytes -= batch.bytes();
                    characters -= batch.characters();
                }
            }
        }
    }

    private void checkOpen() {
        if (failure != null) {
            throw new java.util.concurrent.CompletionException(failure);
        }
        if (closed || cancelled) {
            throw new IllegalStateException("Tooltip pipeline no longer accepts input");
        }
    }

    private void fail(Throwable error) {
        failure = error;
        discardQueued();
    }

    private void discardQueued() {
        for (Batch batch : queue) {
            bytes -= batch.bytes();
            characters -= batch.characters();
        }
        queue.clear();
    }

    private void finishIfStopped() {
        if (running) {
            return;
        }
        if (failure != null) {
            sink = null;
            completion.completeExceptionally(failure);
        } else if (cancelled) {
            sink = null;
            completion.cancel(false);
        } else if (closed && !scheduled && queue.isEmpty()) {
            sink = null;
            completion.complete(null);
        }
    }

    public record Batch(List<TooltipSearchSnapshot> snapshots, long bytes, long characters) {
        public Batch(List<TooltipSearchSnapshot> snapshots) {
            this(List.copyOf(snapshots), snapshots.stream().mapToLong(TooltipIndexPipeline::estimatedBytes).sum() + 64,
                snapshots.stream().flatMap(snapshot -> snapshot.tooltipStrings().stream()).mapToLong(String::length).sum());
        }

        public Batch {
            snapshots = List.copyOf(snapshots);
            if (bytes < 0 || characters < 0) {
                throw new IllegalArgumentException("Negative batch weight");
            }
        }
    }

    public static long estimatedBytes(TooltipSearchSnapshot snapshot) {
        long weight = 64;
        for (String text : snapshot.tooltipStrings()) {
            weight += 48L + 2L * text.length();
        }
        return weight;
    }

    public record Metrics(int submitted, int consumed, int peakQueued, long bytes, long characters,
        long peakBytes, long peakCharacters, long workerNanos) {}
}