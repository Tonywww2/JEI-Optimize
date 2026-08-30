package com.tonywww.jeioptimize.recipe;

import java.util.HashMap;
import java.util.Map;

public final class RepresentativeExampleBudget<K> {
    private final int limit;
    private final Map<K, Integer> selectedByGroup = new HashMap<>();

    public RepresentativeExampleBudget(int limit) {
        this.limit = Math.max(1, limit);
    }

    public int take(K group, int available) {
        if (available <= 0) {
            return 0;
        }
        int selected = selectedByGroup.getOrDefault(group, 0);
        int taken = Math.min(available, Math.max(0, limit - selected));
        if (taken > 0) {
            selectedByGroup.put(group, selected + taken);
        }
        return taken;
    }
}