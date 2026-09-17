package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TooltipBackendTest {
    public static void main(String[] arguments) throws Exception {
        TooltipSearchBackend backend = new TooltipSearchBackend(false);
        List<TooltipSearchSnapshot> snapshots = new ArrayList<>();
        Random random = new Random(8192);
        for (int ordinal = 0; ordinal < 320; ordinal++) {
            String text = "prefix-" + random.nextInt(40) + "-\u4e2d\u6587-" + ordinal + "-suffix";
            snapshots.add(new TooltipSearchSnapshot(ordinal, List.of(text, " shared ")));
            backend.put(text, ordinal);
            backend.put(" shared ", ordinal);
            backend.put(text, ordinal);
        }
        TooltipSearchIndex reference = TooltipSearchIndex.build(snapshots, false);
        List<String> queries = new ArrayList<>(reference.validationTokens(2000));
        queries.addAll(List.of("shared", " ", "x-1", "300", "Suffix", "\u6587", "missing"));
        for (String query : queries) {
            if (!backend.search(query).equals(reference.search(query))) {
                throw new AssertionError("Native suffix index differs: " + query);
            }
        }
        System.out.println("TooltipBackendTest passed: " + queries.size() + " native substring comparisons");
    }
}