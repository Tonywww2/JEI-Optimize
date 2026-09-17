package com.tonywww.jeioptimize.runtime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JeiOptTooltipCache {
    private final Map<Integer, List<String>> entries = new HashMap<>();
    private final long maxWeight;
    private long weight;
    private int hits;

    public JeiOptTooltipCache(long maxWeight) {
        this.maxWeight = Math.max(0, maxWeight);
    }

    public List<String> get(int ordinal) {
        List<String> result = entries.get(ordinal);
        if (result != null) {
            hits++;
        }
        return result;
    }

    public void put(int ordinal, List<String> strings) {
        if (entries.containsKey(ordinal)) {
            return;
        }
        long estimated = 64;
        for (String text : strings) {
            estimated += 48L + 2L * text.length();
        }
        if (estimated > maxWeight - weight) {
            return;
        }
        entries.put(ordinal, List.copyOf(strings));
        weight += estimated;
    }

    public int hits() {
        return hits;
    }

    public long weight() {
        return weight;
    }

    public void clear() {
        entries.clear();
        weight = 0;
    }
}