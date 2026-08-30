package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexBuilderTest {
    @Test
    void buildsEquivalentIndexesSequentiallyAndInParallel() {
        List<IngredientSearchSnapshot> snapshots = List.of(
            snapshot("a", "alpha", "first tooltip"),
            snapshot("b", "beta", "second tooltip")
        );

        SearchIndexBuilder.BuiltSearchIndex sequential = SearchIndexBuilder.build(snapshots);
        SearchIndexBuilder.BuiltSearchIndex parallel = SearchIndexBuilder.build(snapshots, true, 1);

        assertEquals(sequential.byUid(), parallel.byUid());
        assertEquals(sequential.indexes(), parallel.indexes());
        assertTrue(parallel.failedPrefixes().isEmpty());
    }

    @Test
    void reportsOnlyThePrefixThatFails() {
        IngredientSearchSnapshot broken = new IngredientSearchSnapshot(
            "broken",
            null,
            List.of("mod"),
            List.of("modid"),
            List.of("tooltip"),
            List.of("tag"),
            List.of("tab"),
            List.of("color"),
            "mod:broken",
            true,
            0
        );

        SearchIndexBuilder.BuiltSearchIndex result = SearchIndexBuilder.build(List.of(broken), false, 1);

        assertTrue(result.failed(SearchIndexBuilder.SearchPrefix.NAME));
        assertTrue(result.search(SearchIndexBuilder.SearchPrefix.NAME, "broken").isEmpty());
        assertEquals(1, result.search(SearchIndexBuilder.SearchPrefix.MOD, "mod").size());
    }

    private static IngredientSearchSnapshot snapshot(String uid, String name, String tooltip) {
        return new IngredientSearchSnapshot(
            uid,
            List.of(name),
            List.of("example"),
            List.of("example"),
            List.of(tooltip),
            List.of("tag"),
            List.of("tab"),
            List.of("color"),
            "example:" + uid,
            true,
            0
        );
    }
}