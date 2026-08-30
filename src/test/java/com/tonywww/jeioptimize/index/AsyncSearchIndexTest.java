package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.snapshot.IngredientSearchSnapshot;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncSearchIndexTest {
    @Test
    void completedIndexKeepsFailedPrefixState() {
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

        AsyncSearchIndex index = AsyncSearchIndex.buildFromSnapshots(
            List.of(broken),
            JeiOptRuntimeState.currentGeneration()
        );

        assertTrue(index.awaitOrFallback(SearchIndexBuilder.BuiltSearchIndex::empty)
            .failed(SearchIndexBuilder.SearchPrefix.NAME));
    }
}