package com.tonywww.jeioptimize.runtime;

import java.util.concurrent.CompletableFuture;

public final class JeiOptStartupProgressState {
    private static final Object LOCK = new Object();
    private static final CompletableFuture<Void> ALREADY_PUBLISHED = CompletableFuture.completedFuture(null);

    private static long generation = Long.MIN_VALUE;
    private static Stage stage = Stage.HIDDEN;
    private static int completedChunks;
    private static int totalChunks;
    private static int totalIngredients;
    private static boolean buildRegistered;
    private static boolean runtimeComplete;
    private static CompletableFuture<Void> publication = ALREADY_PUBLISHED;

    private JeiOptStartupProgressState() {
    }

    public static void begin(long newGeneration) {
        CompletableFuture<Void> previous;
        synchronized (LOCK) {
            previous = publication;
            generation = newGeneration;
            stage = Stage.PREPARING;
            completedChunks = 0;
            totalChunks = 0;
            totalIngredients = 0;
            buildRegistered = false;
            runtimeComplete = false;
            publication = new CompletableFuture<>();
        }
        if (!previous.isDone()) {
            previous.cancel(false);
        }
    }

    public static boolean registerBuild(long expectedGeneration, int chunks, int ingredients) {
        synchronized (LOCK) {
            if (generation != expectedGeneration || stage == Stage.CANCELLED || stage == Stage.HIDDEN) {
                return false;
            }
            buildRegistered = true;
            completedChunks = 0;
            totalChunks = Math.max(0, chunks);
            totalIngredients = Math.max(0, ingredients);
            stage = totalChunks == 0 ? Stage.READY : Stage.INDEXING;
            return true;
        }
    }

    public static void markChunkCompleted(long expectedGeneration) {
        synchronized (LOCK) {
            if (generation != expectedGeneration || !buildRegistered || stage != Stage.INDEXING) {
                return;
            }
            completedChunks = Math.min(totalChunks, completedChunks + 1);
            if (completedChunks == totalChunks) {
                stage = Stage.READY;
            }
        }
    }

    public static void markReady(long expectedGeneration) {
        synchronized (LOCK) {
            if (generation != expectedGeneration || !buildRegistered || stage == Stage.CANCELLED) {
                return;
            }
            completedChunks = totalChunks;
            stage = Stage.READY;
        }
    }

    public static CompletableFuture<Void> publicationFuture(long expectedGeneration) {
        synchronized (LOCK) {
            if (generation != expectedGeneration || !buildRegistered) {
                return ALREADY_PUBLISHED;
            }
            return publication;
        }
    }

    public static void markPublished(long expectedGeneration) {
        CompletableFuture<Void> toComplete;
        synchronized (LOCK) {
            if (generation != expectedGeneration || !buildRegistered || stage == Stage.CANCELLED) {
                return;
            }
            completedChunks = totalChunks;
            stage = runtimeComplete ? Stage.HIDDEN : Stage.PUBLISHED;
            toComplete = publication;
        }
        toComplete.complete(null);
    }

    public static void markRuntimeComplete(long expectedGeneration) {
        CompletableFuture<Void> toComplete = null;
        synchronized (LOCK) {
            if (generation != expectedGeneration || stage == Stage.CANCELLED) {
                return;
            }
            runtimeComplete = true;
            if (!buildRegistered) {
                stage = Stage.HIDDEN;
                toComplete = publication;
            } else if (stage == Stage.PUBLISHED) {
                stage = Stage.HIDDEN;
            }
        }
        if (toComplete != null) {
            toComplete.complete(null);
        }
    }

    public static void fail(long expectedGeneration, Throwable error) {
        CompletableFuture<Void> toFail;
        synchronized (LOCK) {
            if (generation != expectedGeneration) {
                return;
            }
            stage = Stage.HIDDEN;
            toFail = publication;
        }
        toFail.completeExceptionally(error);
    }

    public static void cancel(long expectedGeneration) {
        CompletableFuture<Void> toCancel;
        synchronized (LOCK) {
            if (generation != expectedGeneration) {
                return;
            }
            stage = Stage.CANCELLED;
            completedChunks = 0;
            totalChunks = 0;
            totalIngredients = 0;
            buildRegistered = false;
            runtimeComplete = false;
            toCancel = publication;
        }
        if (!toCancel.isDone()) {
            toCancel.cancel(false);
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(stage, completedChunks, totalChunks, totalIngredients);
        }
    }

    public enum Stage {
        HIDDEN,
        PREPARING,
        INDEXING,
        READY,
        PUBLISHED,
        CANCELLED
    }

    public record Snapshot(Stage stage, int completedChunks, int totalChunks, int totalIngredients) {
        public boolean visible() {
            return stage != Stage.HIDDEN && stage != Stage.CANCELLED;
        }

        public double fraction() {
            if (totalChunks <= 0) {
                return stage == Stage.READY || stage == Stage.PUBLISHED ? 1.0D : 0.0D;
            }
            return Math.min(1.0D, Math.max(0.0D, (double) completedChunks / totalChunks));
        }
    }
}