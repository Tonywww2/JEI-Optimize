package com.tonywww.jeioptimize.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiOptStartupProgressStateTest {
    @Test
    void blocksJeiInputUntilRuntimePublicationCompletes() {
        long generation = 101L;

        JeiOptStartupProgressState.begin(generation);
        assertStageAndBlocked(JeiOptStartupProgressState.Stage.PREPARING);

        assertTrue(JeiOptStartupProgressState.registerBuild(generation, 2, 20));
        assertStageAndBlocked(JeiOptStartupProgressState.Stage.INDEXING);

        JeiOptStartupProgressState.markChunkCompleted(generation);
        assertStageAndBlocked(JeiOptStartupProgressState.Stage.INDEXING);

        JeiOptStartupProgressState.markChunkCompleted(generation);
        assertStageAndBlocked(JeiOptStartupProgressState.Stage.INDEXING);

        JeiOptStartupProgressState.markReady(generation);
        assertStageAndBlocked(JeiOptStartupProgressState.Stage.READY);

        JeiOptStartupProgressState.markPublished(generation);
        assertStageAndBlocked(JeiOptStartupProgressState.Stage.PUBLISHED);

        JeiOptStartupProgressState.markRuntimeComplete(generation);
        assertEquals(JeiOptStartupProgressState.Stage.HIDDEN, snapshot().stage());
        assertFalse(JeiOptStartupProgressState.blocksJeiInput());
    }

    @Test
    void cancellationReleasesInput() {
        long generation = 102L;

        JeiOptStartupProgressState.begin(generation);
        JeiOptStartupProgressState.cancel(generation);

        assertEquals(JeiOptStartupProgressState.Stage.CANCELLED, snapshot().stage());
        assertFalse(JeiOptStartupProgressState.blocksJeiInput());
    }

    @Test
    void staleGenerationCannotReleaseCurrentStartup() {
        long staleGeneration = 103L;
        long currentGeneration = 104L;

        JeiOptStartupProgressState.begin(staleGeneration);
        assertTrue(JeiOptStartupProgressState.registerBuild(staleGeneration, 1, 10));
        JeiOptStartupProgressState.begin(currentGeneration);

        JeiOptStartupProgressState.markPublished(staleGeneration);
        JeiOptStartupProgressState.markRuntimeComplete(staleGeneration);
        JeiOptStartupProgressState.cancel(staleGeneration);

        assertStageAndBlocked(JeiOptStartupProgressState.Stage.PREPARING);

        JeiOptStartupProgressState.cancel(currentGeneration);
    }

    private static void assertStageAndBlocked(JeiOptStartupProgressState.Stage expectedStage) {
        assertEquals(expectedStage, snapshot().stage());
        assertTrue(JeiOptStartupProgressState.blocksJeiInput());
    }

    private static JeiOptStartupProgressState.Snapshot snapshot() {
        return JeiOptStartupProgressState.snapshot();
    }
}