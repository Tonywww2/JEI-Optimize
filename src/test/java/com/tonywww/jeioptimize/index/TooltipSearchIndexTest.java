package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

public final class TooltipSearchIndexTest {
    public static void main(String[] arguments) {
        Random random = new Random(1709);
        List<TooltipSearchSnapshot> snapshots = new ArrayList<>();
        String alphabet = "abCD \t\n\u4e2d\u6587\ud83d\ude80";
        for (int ordinal = 0; ordinal < 128; ordinal++) {
            List<String> strings = new ArrayList<>(List.of("", "  ", "shared", "Attack"));
            for (int line = 0; line < 5; line++) {
                StringBuilder text = new StringBuilder();
                for (int letter = 0; letter < 18; letter++) {
                    text.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                strings.add(text.toString());
            }
            snapshots.add(new TooltipSearchSnapshot(ordinal, strings));
        }
        for (boolean trim : List.of(false, true)) {
            TooltipSearchIndex first = TooltipSearchIndex.build(snapshots.subList(0, 64), trim);
            TooltipSearchIndex second = TooltipSearchIndex.build(snapshots.subList(64, snapshots.size()), trim);
            TooltipSearchIndex merged = TooltipSearchIndex.merge(List.of(first, second));
            List<String> tokens = new ArrayList<>(merged.validationTokens(256));
            tokens.addAll(List.of("attack", "Attack", "shared", "absent", "\u4e2d", "\ud83d", "\ude80", "  "));
            for (String token : tokens) {
                BitSet expected = new BitSet();
                if (!token.isEmpty()) {
                    for (TooltipSearchSnapshot snapshot : snapshots) {
                        if (snapshot.tooltipStrings().stream().map(text -> trim ? text.trim() : text)
                            .anyMatch(text -> !text.isEmpty() && text.contains(token))) {
                            expected.set(snapshot.elementOrdinal());
                        }
                    }
                }
                check(merged.search(token).equals(expected), "substring equivalence");
            }
            BitSet modified = merged.search("shared");
            modified.clear();
            check(merged.search("shared").cardinality() == 128, "query returns independent result");
            check(first.search("shared").cardinality() == 64, "merge does not mutate segment");
        }
        try {
            TooltipSearchIndex.build(snapshots, true, () -> true);
            throw new AssertionError("cancelled token ignored");
        } catch (CancellationException expected) {
            check(!Thread.currentThread().isInterrupted(), "token cancellation needs no thread interrupt");
        }
        Thread.currentThread().interrupt();
        try {
            TooltipSearchIndex.build(snapshots, true);
            throw new AssertionError("cancelled build continued");
        } catch (CancellationException expected) {
            Thread.interrupted();
        }
        System.out.println("TooltipSearchIndexTest passed: legacy/modern normalization, random substring queries, merge, cancellation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}