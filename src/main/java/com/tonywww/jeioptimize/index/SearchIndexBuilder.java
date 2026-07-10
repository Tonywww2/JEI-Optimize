package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SearchIndexBuilder {
    private SearchIndexBuilder() {
    }

    public static BuiltSearchIndex build(Collection<IngredientSearchSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return BuiltSearchIndex.empty();
        }

        Map<Object, IngredientSearchSnapshot> byUid = new LinkedHashMap<>();
        Map<SearchPrefix, Map<String, Set<Object>>> indexes = new EnumMap<>(SearchPrefix.class);
        for (SearchPrefix prefix : SearchPrefix.values()) {
            indexes.put(prefix, new HashMap<>());
        }

        for (IngredientSearchSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.uid() == null) {
                continue;
            }
            byUid.put(snapshot.uid(), snapshot);
            indexStrings(indexes.get(SearchPrefix.NAME), snapshot.uid(), snapshot.names());
            indexStrings(indexes.get(SearchPrefix.MOD), snapshot.uid(), snapshot.modNames());
            indexStrings(indexes.get(SearchPrefix.MOD), snapshot.uid(), snapshot.modIds());
            indexStrings(indexes.get(SearchPrefix.TOOLTIP), snapshot.uid(), snapshot.tooltipStrings());
            indexStrings(indexes.get(SearchPrefix.TAG), snapshot.uid(), snapshot.tagStrings());
            indexStrings(indexes.get(SearchPrefix.CREATIVE_TAB), snapshot.uid(), snapshot.creativeTabStrings());
            indexStrings(indexes.get(SearchPrefix.COLOR), snapshot.uid(), snapshot.colorStrings());
            indexString(indexes.get(SearchPrefix.RESOURCE_LOCATION), snapshot.uid(), snapshot.resourceLocation());
        }

        return new BuiltSearchIndex(freeze(byUid), freezeIndexes(indexes));
    }

    private static void indexStrings(Map<String, Set<Object>> index, Object uid, Collection<String> strings) {
        if (strings == null) {
            return;
        }
        for (String string : strings) {
            indexString(index, uid, string);
        }
    }

    private static void indexString(Map<String, Set<Object>> index, Object uid, String string) {
        String normalized = normalize(string);
        if (normalized.isEmpty()) {
            return;
        }
        index.computeIfAbsent(normalized, ignored -> new LinkedHashSet<>()).add(uid);
    }

    private static String normalize(String string) {
        if (string == null) {
            return "";
        }
        return string.toLowerCase(Locale.ROOT).trim();
    }

    private static Map<Object, IngredientSearchSnapshot> freeze(Map<Object, IngredientSearchSnapshot> byUid) {
        return Map.copyOf(byUid);
    }

    private static Map<SearchPrefix, Map<String, Set<Object>>> freezeIndexes(Map<SearchPrefix, Map<String, Set<Object>>> indexes) {
        Map<SearchPrefix, Map<String, Set<Object>>> frozen = new EnumMap<>(SearchPrefix.class);
        for (Map.Entry<SearchPrefix, Map<String, Set<Object>>> entry : indexes.entrySet()) {
            Map<String, Set<Object>> prefixIndex = new HashMap<>();
            for (Map.Entry<String, Set<Object>> tokenEntry : entry.getValue().entrySet()) {
                prefixIndex.put(tokenEntry.getKey(), Set.copyOf(tokenEntry.getValue()));
            }
            frozen.put(entry.getKey(), Map.copyOf(prefixIndex));
        }
        return Map.copyOf(frozen);
    }

    public enum SearchPrefix {
        NAME,
        MOD,
        TOOLTIP,
        TAG,
        CREATIVE_TAB,
        COLOR,
        RESOURCE_LOCATION
    }

    public record BuiltSearchIndex(
        Map<Object, IngredientSearchSnapshot> byUid,
        Map<SearchPrefix, Map<String, Set<Object>>> indexes
    ) {
        public static BuiltSearchIndex empty() {
            return new BuiltSearchIndex(Map.of(), Map.of());
        }

        public List<IngredientSearchSnapshot> allVisible() {
            return byUid.values()
                .stream()
                .filter(IngredientSearchSnapshot::visible)
                .toList();
        }

        public List<IngredientSearchSnapshot> search(SearchPrefix prefix, String token) {
            Objects.requireNonNull(prefix, "prefix");
            String normalized = normalize(token);
            if (normalized.isEmpty()) {
                return List.of();
            }

            Map<String, Set<Object>> prefixIndex = indexes.getOrDefault(prefix, Map.of());
            Set<Object> matchedUids = new LinkedHashSet<>();
            for (Map.Entry<String, Set<Object>> entry : prefixIndex.entrySet()) {
                if (entry.getKey().contains(normalized)) {
                    matchedUids.addAll(entry.getValue());
                }
            }

            return matchedUids.stream()
                .map(byUid::get)
                .filter(Objects::nonNull)
                .filter(IngredientSearchSnapshot::visible)
                .toList();
        }

        public int size() {
            return byUid.size();
        }
    }
}