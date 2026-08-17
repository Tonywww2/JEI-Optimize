package com.tonywww.jeioptimize.runtime;

import java.util.Objects;

public final class JeiOptRuntimePublication {
    private static final ThreadLocal<PendingCallbacks> PENDING_CALLBACKS = new ThreadLocal<>();

    private JeiOptRuntimePublication() {
    }

    public static void deferCallbacks(long generation, Runnable callbacks) {
        Objects.requireNonNull(callbacks, "callbacks");
        if (!JeiOptExecutors.isJeiStartThread()) {
            throw new IllegalStateException("Only the JEI startup thread may defer runtime callbacks");
        }
        if (PENDING_CALLBACKS.get() != null) {
            throw new IllegalStateException("JEI runtime callbacks are already pending publication");
        }
        PENDING_CALLBACKS.set(new PendingCallbacks(generation, callbacks));
    }

    public static void runCallbacksAndPublish(Runnable publication) {
        Objects.requireNonNull(publication, "publication");
        PendingCallbacks pending = PENDING_CALLBACKS.get();
        PENDING_CALLBACKS.remove();
        JeiOptExecutors.runOnMainThreadAndWait(() -> {
            if (pending != null) {
                pending.callbacks().run();
                JeiOptExecutors.checkJeiStartGeneration(pending.generation());
            }
            publication.run();
        });
    }

    public static void clearPendingCallbacks() {
        PENDING_CALLBACKS.remove();
    }

    private record PendingCallbacks(long generation, Runnable callbacks) {
    }
}