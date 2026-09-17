package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class TooltipSearchIndex {
    private final Map<String, BitSet> postings;

    private TooltipSearchIndex(Map<String, BitSet> postings) {
        this.postings = postings;
    }

    public static TooltipSearchIndex build(List<TooltipSearchSnapshot> snapshots, boolean trimStrings) {
        return build(snapshots, trimStrings, () -> false);
    }

    public static TooltipSearchIndex build(List<TooltipSearchSnapshot> snapshots, boolean trimStrings, BooleanSupplier cancelled) {
        Map<String, BitSet> postings = new LinkedHashMap<>();
        for (TooltipSearchSnapshot snapshot : snapshots) {
            checkCancelled(cancelled);
            for (String original : snapshot.tooltipStrings()) {
                checkCancelled(cancelled);
                String text = trimStrings ? original.trim() : original;
                if (!text.isEmpty()) {
                    postings.computeIfAbsent(text, ignored -> new BitSet()).set(snapshot.elementOrdinal());
                }
            }
        }
        return new TooltipSearchIndex(postings);
    }

    public static TooltipSearchIndex merge(List<TooltipSearchIndex> segments) {
        return merge(segments, () -> false);
    }

    public static TooltipSearchIndex merge(List<TooltipSearchIndex> segments, BooleanSupplier cancelled) {
        Map<String, BitSet> postings = new LinkedHashMap<>();
        for (TooltipSearchIndex segment : segments) {
            for (Map.Entry<String, BitSet> entry : segment.postings.entrySet()) {
                checkCancelled(cancelled);
                BitSet existing = postings.get(entry.getKey());
                if (existing == null) {
                    postings.put(entry.getKey(), (BitSet) entry.getValue().clone());
                } else {
                    existing.or(entry.getValue());
                }
            }
        }
        return new TooltipSearchIndex(postings);
    }

    public BitSet search(String token) {
        return search(token, () -> false);
    }

    public BitSet search(String token, BooleanSupplier cancelled) {
        BitSet result = new BitSet();
        if (!token.isEmpty()) {
            for (Map.Entry<String, BitSet> entry : postings.entrySet()) {
                checkCancelled(cancelled);
                if (entry.getKey().contains(token)) {
                    result.or(entry.getValue());
                }
            }
        }
        return result;
    }

    public List<String> validationTokens(int limit) {
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add("");
        tokens.add("\u0000jet-tooltip-absent\u0000");
        tokens.add(" ");
        for (String text : postings.keySet()) {
            if (tokens.size() >= limit) {
                break;
            }
            tokens.add(text);
            tokens.add(text.substring(0, Math.min(1, text.length())));
            tokens.add(text.substring(0, Math.min(2, text.length())));
            int middle = text.length() / 2;
            tokens.add(text.substring(middle, Math.min(middle + 4, text.length())));
        }
        return new ArrayList<>(tokens).subList(0, Math.min(tokens.size(), limit));
    }

    public int stringCount() {
        return postings.size();
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new CancellationException("Tooltip reference indexing interrupted");
        }
    }
}