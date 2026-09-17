package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

public final class TooltipSnapshotProducer {
    private final TooltipIndexPipeline pipeline;
    private final int total;
    private final List<TooltipSearchSnapshot> chunk = new ArrayList<>();
    private TooltipIndexPipeline.Batch pending;
    private int cursor;
    private long chunkBytes;
    private long chunkCharacters;
    private long peakBytes;
    private long peakCharacters;
    private boolean finished;

    public TooltipSnapshotProducer(TooltipIndexPipeline pipeline, int total) {
        this.pipeline = pipeline;
        this.total = total;
    }

    public Step pump(BooleanSupplier hasTime, IntFunction<TooltipSearchSnapshot> extract) {
        if (finished) {
            return new Step(0, false);
        }
        int processed = 0;
        if (!flushPending()) {
            return new Step(0, true);
        }
        while (cursor < total && (processed == 0 || hasTime.getAsBoolean())) {
            if (!pipeline.hasCapacity(chunkBytes)) {
                flush();
                return new Step(processed, true);
            }
            TooltipSearchSnapshot snapshot = extract.apply(cursor);
            if (snapshot.elementOrdinal() != cursor) {
                throw new IllegalStateException("Tooltip ordinal changed during extraction");
            }
            chunk.add(snapshot);
            chunkBytes += TooltipIndexPipeline.estimatedBytes(snapshot);
            for (String text : snapshot.tooltipStrings()) {
                chunkCharacters += text.length();
            }
            TooltipIndexPipeline.Metrics metrics = pipeline.metrics();
            peakBytes = Math.max(peakBytes, metrics.bytes() + chunkBytes + 64);
            peakCharacters = Math.max(peakCharacters, metrics.characters() + chunkCharacters);
            cursor++;
            processed++;
            if (chunk.size() >= 128 || chunkCharacters >= 262144 || chunkBytes >= 1024 * 1024) {
                if (!flush()) {
                    return new Step(processed, true);
                }
            }
        }
        if (!flush()) {
            return new Step(processed, true);
        }
        if (cursor == total) {
            finished = true;
            pipeline.closeInput();
        }
        return new Step(processed, false);
    }

    private boolean flush() {
        if (!chunk.isEmpty()) {
            pending = new TooltipIndexPipeline.Batch(chunk);
            chunk.clear();
            chunkBytes = 0;
            chunkCharacters = 0;
        }
        return flushPending();
    }

    private boolean flushPending() {
        if (pending != null) {
            if (!pipeline.offer(pending)) {
                return false;
            }
            pending = null;
        }
        return true;
    }

    public boolean finished() {
        return finished;
    }

    public long peakBytes() {
        return peakBytes;
    }

    public long peakCharacters() {
        return peakCharacters;
    }

    public void clear() {
        finished = true;
        chunk.clear();
        pending = null;
        chunkBytes = 0;
        chunkCharacters = 0;
    }

    public record Step(int processed, boolean backpressured) {}
}