package com.tonywww.jeioptimize.snapshot;

import java.util.List;

public record TooltipSearchSnapshot(int elementOrdinal, List<String> tooltipStrings) {
    public TooltipSearchSnapshot {
        if (elementOrdinal < 0) {
            throw new IllegalArgumentException("Negative ingredient ordinal");
        }
        tooltipStrings = List.copyOf(tooltipStrings);
    }
}