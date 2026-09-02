package com.tonywww.jeioptimize.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SfmFallingAnvilLifecycleTest {
    @AfterEach
    void clearState() {
        SfmFallingAnvilCache.clear();
        SfmFallingAnvilRepresentativeLimiter.clear();
    }

    @Test
    void abortDropsAnIncompleteCapturedLayout() {
        SfmFallingAnvilCache.beginCapture(true);
        assertTrue(SfmFallingAnvilCache.hasCapture());

        SfmFallingAnvilCache.abortCapture();

        assertFalse(SfmFallingAnvilCache.hasCapture());
    }

    @Test
    void clearDropsRepresentativeLimiterContext() {
        SfmFallingAnvilRepresentativeLimiter.begin(true, 3);
        assertTrue(SfmFallingAnvilRepresentativeLimiter.hasContext());

        SfmFallingAnvilRepresentativeLimiter.clear();

        assertFalse(SfmFallingAnvilRepresentativeLimiter.hasContext());
    }
}