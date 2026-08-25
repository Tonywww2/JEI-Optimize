package com.tonywww.jeioptimize.recipe;

import java.util.ArrayList;
import java.util.List;

public final class CoveragePairPlanner {
    private CoveragePairPlanner() {
    }

    public static List<Pair> plan(int itemCount, int variantCount) {
        if (itemCount <= 0 || variantCount <= 0) {
            return List.of();
        }

        int resultSize = Math.max(itemCount, variantCount);
        List<Pair> pairs = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            pairs.add(new Pair(index % itemCount, index % variantCount));
        }
        return List.copyOf(pairs);
    }

    public record Pair(int itemIndex, int variantIndex) {
    }
}