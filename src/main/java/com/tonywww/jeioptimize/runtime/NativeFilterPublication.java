package com.tonywww.jeioptimize.runtime;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Completion wiring for {@code scheduleNative}.
 * The await/abort handler in the mixin is a different route and does not run here.
 * {@code publish} runs only after {@code ready} completes normally. A failure or
 * cancellation cancels the build and does not call {@code publish}.
 */
public final class NativeFilterPublication {
    private NativeFilterPublication() {
    }

    public static <T> CompletableFuture<Void> bind(
        CompletableFuture<T> build,
        CompletableFuture<T> ready,
        Consumer<T> publish,
        Runnable discardStorages
    ) {
        Objects.requireNonNull(build, "build");
        Objects.requireNonNull(ready, "ready");
        Objects.requireNonNull(publish, "publish");
        Objects.requireNonNull(discardStorages, "discardStorages");
        CompletableFuture<Void> published = ready.thenAccept(publish);
        published.whenComplete((ignored, failure) -> {
            if (failure != null) {
                build.cancel(false);
                ready.cancel(false);
                discardStorages.run();
            }
        });
        return published;
    }
}
