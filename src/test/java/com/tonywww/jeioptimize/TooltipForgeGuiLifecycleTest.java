package com.tonywww.jeioptimize;

import com.tonywww.jeioptimize.runtime.ForgeGuiRegistrationTracker;

import java.util.concurrent.atomic.AtomicInteger;

public final class TooltipForgeGuiLifecycleTest {
    public static void main(String[] arguments) {
        cancelledBeforeGuiRegistrationDoesNotClean();
        cancelledAfterGuiRegistrationCleansOnce();
        repeatedDisconnectIsIdempotent();
        publishedRuntimeLeavesCleanupToJei();
        olderGenerationCannotClearNewerRegistration();
        System.out.println("TooltipForgeGuiLifecycleTest passed");
    }

    private static void cancelledBeforeGuiRegistrationDoesNotClean() {
        ForgeGuiRegistrationTracker.resetForTest();
        AtomicInteger cleanups = new AtomicInteger();
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(1L);
        check(cleanups.get() == 0, "cleanup before registration must not run");
    }

    private static void cancelledAfterGuiRegistrationCleansOnce() {
        ForgeGuiRegistrationTracker.resetForTest();
        AtomicInteger cleanups = new AtomicInteger();
        ForgeGuiRegistrationTracker.markRegistered(1L, cleanups::incrementAndGet);
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(1L);
        check(cleanups.get() == 1, "unpublished GUI registration must be cleaned once");
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(1L);
        check(cleanups.get() == 1, "second cancellation must not clean again");
    }

    private static void repeatedDisconnectIsIdempotent() {
        ForgeGuiRegistrationTracker.resetForTest();
        AtomicInteger cleanups = new AtomicInteger();
        ForgeGuiRegistrationTracker.markRegistered(4L, cleanups::incrementAndGet);
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(4L);
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(4L);
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(4L);
        check(cleanups.get() == 1, "repeated disconnects stay idempotent");
    }

    private static void publishedRuntimeLeavesCleanupToJei() {
        ForgeGuiRegistrationTracker.resetForTest();
        AtomicInteger cleanups = new AtomicInteger();
        ForgeGuiRegistrationTracker.markRegistered(2L, cleanups::incrementAndGet);
        ForgeGuiRegistrationTracker.markPublished(2L);
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(2L);
        check(cleanups.get() == 0, "published runtime must keep JEI's own stop path");
    }

    private static void olderGenerationCannotClearNewerRegistration() {
        ForgeGuiRegistrationTracker.resetForTest();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        ForgeGuiRegistrationTracker.markRegistered(1L, first::incrementAndGet);
        ForgeGuiRegistrationTracker.markRegistered(2L, second::incrementAndGet);
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(1L);
        check(first.get() == 0 && second.get() == 0, "an older cancel must not clear the newer GUI");
        ForgeGuiRegistrationTracker.cleanupIfUnpublished(2L);
        check(second.get() == 1 && first.get() == 0, "the newer unpublished GUI is cleaned by its own generation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
