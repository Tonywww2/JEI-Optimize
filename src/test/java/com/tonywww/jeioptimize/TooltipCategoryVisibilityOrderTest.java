package com.tonywww.jeioptimize;

import com.tonywww.jeioptimize.runtime.CategoryVisibilityPrep;

import java.util.ArrayList;
import java.util.List;

public final class TooltipCategoryVisibilityOrderTest {
    public static void main(String[] arguments) {
        List<String> categories = List.of("a", "b", "c", "d");
        List<String> visible = CategoryVisibilityPrep.visibleInOrder(categories, category -> "b".equals(category) || "d".equals(category));
        if (!List.of("a", "c").equals(visible)) {
            throw new AssertionError("visible categories must keep source order, got " + visible);
        }
        if (!CategoryVisibilityPrep.visibleInOrder(List.of(), category -> false).isEmpty()) {
            throw new AssertionError("empty category list stays empty");
        }
        ArrayList<String> first = new ArrayList<>();
        int cursor = CategoryVisibilityPrep.appendVisibleSlice(
            first, categories, 0, category -> "b".equals(category) || "d".equals(category), Long.MIN_VALUE);
        if (cursor != 0 || !first.isEmpty()) {
            throw new AssertionError("an expired budget must not consume categories, got cursor=" + cursor + " visible=" + first);
        }
        cursor = CategoryVisibilityPrep.appendVisibleSlice(
            first, categories, cursor, category -> "b".equals(category) || "d".equals(category), Long.MAX_VALUE);
        if (cursor != categories.size() || !List.of("a", "c").equals(first)) {
            throw new AssertionError("a later slice must finish the ordered list, got cursor=" + cursor + " visible=" + first);
        }
        System.out.println("TooltipCategoryVisibilityOrderTest passed");
    }
}
