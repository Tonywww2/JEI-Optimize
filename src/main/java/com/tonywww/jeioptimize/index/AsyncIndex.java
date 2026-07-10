package com.tonywww.jeioptimize.index;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface AsyncIndex<T> {
    AsyncIndexState state();

    CompletableFuture<T> future();

    Optional<T> readyValue();

    T awaitOrFallback(Supplier<T> fallback);
}