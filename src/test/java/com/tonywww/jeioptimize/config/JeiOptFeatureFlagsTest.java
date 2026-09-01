package com.tonywww.jeioptimize.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JeiOptFeatureFlagsTest {
    @Test
    void retiredSearchPreheatCannotBeReenabledByLegacyConfig() {
        assertFalse(JeiOptFeatureFlags.searchPreheat());
        assertFalse(JeiOptFeatureFlags.sortPreheat());
        assertFalse(JeiOptFeatureFlags.recipeFocusPreheat());
        assertFalse(JeiOptFeatureFlags.catalystPreheat());
    }

    @Test
    void automaticWorkerCountLeavesTwoProcessorsFreeAndClampsToBounds() {
        assertEquals(1, JeiOptFeatureFlags.resolveWorkerThreads(0, 1));
        assertEquals(1, JeiOptFeatureFlags.resolveWorkerThreads(0, 2));
        assertEquals(2, JeiOptFeatureFlags.resolveWorkerThreads(0, 4));
        assertEquals(8, JeiOptFeatureFlags.resolveWorkerThreads(0, 32));
    }

    @Test
    void explicitWorkerCountIsClamped() {
        assertEquals(1, JeiOptFeatureFlags.resolveWorkerThreads(-1, 16));
        assertEquals(4, JeiOptFeatureFlags.resolveWorkerThreads(4, 16));
        assertEquals(8, JeiOptFeatureFlags.resolveWorkerThreads(64, 16));
    }
}