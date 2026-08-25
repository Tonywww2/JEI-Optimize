package com.tonywww.jeioptimize.recipe;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RepresentativeItemLimiter {
    private final int limitPerGroup;
    private final Map<Object, GroupSelection> groups = new IdentityHashMap<>();
    private int consideredCount;

    public RepresentativeItemLimiter(int limitPerGroup) {
        this.limitPerGroup = Math.max(1, limitPerGroup);
    }

    public boolean shouldKeep(Object groupKey, String itemKey) {
        if (groupKey == null || itemKey == null || itemKey.isBlank()) {
            return true;
        }
        consideredCount++;
        return groups.computeIfAbsent(groupKey, ignored -> new GroupSelection())
            .shouldKeep(itemKey, familyKey(itemKey), limitPerGroup);
    }

    public int groupCount() {
        return groups.size();
    }

    public int selectedCount() {
        return groups.values().stream().mapToInt(GroupSelection::size).sum();
    }

    public int consideredCount() {
        return consideredCount;
    }

    static String familyKey(String itemKey) {
        String normalized = itemKey.toLowerCase(Locale.ROOT);
        int metadataStart = firstPositive(normalized.indexOf('{'), normalized.indexOf('['));
        if (metadataStart >= 0) {
            normalized = normalized.substring(0, metadataStart);
        }
        int namespaceSeparator = normalized.indexOf(':');
        String path = namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
        int familySeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('_'));
        return familySeparator >= 0 && familySeparator + 1 < path.length()
            ? path.substring(familySeparator + 1)
            : path;
    }

    private static int firstPositive(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private static final class GroupSelection {
        private final Set<String> items = new LinkedHashSet<>();
        private final Set<String> families = new LinkedHashSet<>();

        private boolean shouldKeep(String itemKey, String familyKey, int limit) {
            if (items.size() >= limit || items.contains(itemKey) || families.contains(familyKey)) {
                return false;
            }
            items.add(itemKey);
            families.add(familyKey);
            return true;
        }

        private int size() {
            return items.size();
        }
    }
}