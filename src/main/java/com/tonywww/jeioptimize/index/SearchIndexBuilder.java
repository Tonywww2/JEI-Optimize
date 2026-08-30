package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;

import java.util.Collection;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class SearchIndexBuilder {
    private SearchIndexBuilder() {
    }

    public static BuiltSearchIndex build(Collection<IngredientSearchSnapshot> snapshots) {
        return build(snapshots, false, Integer.MAX_VALUE);
    }

    public static BuiltSearchIndex build(
        Collection<IngredientSearchSnapshot> snapshots,
        boolean parallel,
        int parallelThreshold
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return BuiltSearchIndex.empty();
        }

        List<IngredientSearchSnapshot> safeSnapshots = List.copyOf(snapshots);
        Map<Object, IngredientSearchSnapshot> byUid = new LinkedHashMap<>();
        for (IngredientSearchSnapshot snapshot : safeSnapshots) {
            if (snapshot == null || snapshot.uid() == null) {
                continue;
            }
            byUid.put(snapshot.uid(), snapshot);
        }

        Map<SearchPrefix, Map<String, Set<Object>>> indexes = new EnumMap<>(SearchPrefix.class);
        EnumSet<SearchPrefix> failedPrefixes = EnumSet.noneOf(SearchPrefix.class);
        boolean useParallel = parallel
            && safeSnapshots.size() >= Math.max(1, parallelThreshold)
            && JeiOptExecutors.pureComputationPool().getParallelism() > 1;
        if (useParallel) {
            Map<SearchPrefix, Future<Map<String, Set<Object>>>> tasks = new EnumMap<>(SearchPrefix.class);
            for (SearchPrefix prefix : SearchPrefix.values()) {
                tasks.put(prefix, JeiOptExecutors.pureComputationPool().submit(
                    () -> buildPrefix(prefix, safeSnapshots)
                ));
            }
            for (SearchPrefix prefix : SearchPrefix.values()) {
                try {
                    indexes.put(prefix, tasks.get(prefix).get());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    failedPrefixes.add(prefix);
                } catch (ExecutionException | RuntimeException error) {
                    failedPrefixes.add(prefix);
                }
            }
        } else {
            for (SearchPrefix prefix : SearchPrefix.values()) {
                try {
                    indexes.put(prefix, buildPrefix(prefix, safeSnapshots));
                } catch (RuntimeException error) {
                    failedPrefixes.add(prefix);
                }
            }
        }

        return new BuiltSearchIndex(
            freeze(byUid),
            freezeIndexes(indexes),
            failedPrefixes.isEmpty() ? Set.of() : Set.copyOf(failedPrefixes)
        );
    }

    private static Map<String, Set<Object>> buildPrefix(
        SearchPrefix prefix,
        Collection<IngredientSearchSnapshot> snapshots
    ) {
        Map<String, Set<Object>> index = new HashMap<>();
        for (IngredientSearchSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.uid() == null) {
                continue;
            }
            Collection<String> values = switch (prefix) {
                case NAME -> snapshot.names();
                case MOD -> combine(snapshot.modNames(), snapshot.modIds());
                case TOOLTIP -> snapshot.tooltipStrings();
                case TAG -> snapshot.tagStrings();
                case CREATIVE_TAB -> snapshot.creativeTabStrings();
                case COLOR -> snapshot.colorStrings();
                case RESOURCE_LOCATION -> List.of(snapshot.resourceLocation());
            };
            if (values == null) {
                throw new IllegalArgumentException("Search snapshot has no " + prefix + " values");
            }
            indexStrings(index, snapshot.uid(), values);
        }
        return index;
    }

    private static Collection<String> combine(Collection<String> first, Collection<String> second) {
        java.util.ArrayList<String> combined = new java.util.ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return combined;
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
        Map<SearchPrefix, Map<String, Set<Object>>> indexes,
        Set<SearchPrefix> failedPrefixes
    ) {
        public static BuiltSearchIndex empty() {
            return new BuiltSearchIndex(Map.of(), Map.of(), Set.of());
        }

        public boolean failed(SearchPrefix prefix) {
            return failedPrefixes.contains(prefix);
        }

        public List<IngredientSearchSnapshot> allVisible() {
            return byUid.values()
                .stream()
                .filter(IngredientSearchSnapshot::visible)
                .toList();
        }

        public List<IngredientSearchSnapshot> search(SearchPrefix prefix, String token) {
            Objects.requireNonNull(prefix, "prefix");
            if (failed(prefix)) {
                return List.of();
            }
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