package com.tonywww.jeioptimize.runtime;

/**
 * Tracks one Forge GUI registration that happened before {@code JeiStarter.running} is set.
 * Cleanup calls only the registration action captured for that generation, which is
 * {@code ForgeGuiPlugin.onRuntimeUnavailable}. It does not notify other plugins.
 */
public final class ForgeGuiRegistrationTracker {
    private static final long NONE = Long.MIN_VALUE;

    private static long registeredGeneration = NONE;
    private static long publishedGeneration = NONE;
    private static Runnable unpublishedCleanup = () -> {
    };

    private ForgeGuiRegistrationTracker() {
    }

    public static void resetForTest() {
        registeredGeneration = NONE;
        publishedGeneration = NONE;
        unpublishedCleanup = () -> {
        };
    }

    public static void markRegistered(long generation, Runnable cleanup) {
        registeredGeneration = generation;
        unpublishedCleanup = cleanup == null ? () -> {
        } : cleanup;
    }

    public static void markPublished(long generation) {
        publishedGeneration = generation;
    }

    /**
     * Runs the captured Forge GUI cleanup when {@code generation} registered a GUI and never published.
     * A newer registration replaces the captured generation, so this cannot clear that newer GUI.
     */
    public static void cleanupIfUnpublished(long generation) {
        if (generation != registeredGeneration || generation == publishedGeneration) {
            return;
        }
        Runnable cleanup = unpublishedCleanup;
        registeredGeneration = NONE;
        unpublishedCleanup = () -> {
        };
        cleanup.run();
    }
}
