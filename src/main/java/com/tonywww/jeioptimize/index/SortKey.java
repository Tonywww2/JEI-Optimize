package com.tonywww.jeioptimize.index;

import java.util.Comparator;

public record SortKey<T>(T value, int index) {
    public static <T> Comparator<SortKey<T>> compareByIndex() {
        return Comparator.comparingInt(SortKey::index);
    }
}