package com.tonywww.jeioptimize.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CelestialForgeReinforceCacheTest {
    @Test
    void emptyIngredientsAreNotCacheable() {
        assertFalse(CelestialForgeReinforceCache.isCacheableItemCount(0));
        assertTrue(CelestialForgeReinforceCache.isCacheableItemCount(1));
    }
}