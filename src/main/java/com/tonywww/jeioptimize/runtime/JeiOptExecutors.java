package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JeiOptExecutors {
    private static final Logger LOGGER = LoggerFactory.getLogger(JeiOptExecutors.class);

    private static final int DEFAULT_WORKER_THREADS = 2;
    private static final int MIN_WORKER_THREADS = 1;
    private static final int MAX_WORKER_THREADS = 8;

    private static final Executor MAIN_THREAD = command -> {
        Objects.requireNonNull(command, "command");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            command.run();
        } else {
            minecraft.execute(command);
        }
    };

    private static final Object LOCK = new Object();
    private static ExecutorService workerExecutor;
    private static int workerThreadCount = DEFAULT_WORKER_THREADS;

    private static final Object JEI_START_LOCK = new Object();
    private static final ThreadLocal<JeiStartTask> CURRENT_JEI_START = new ThreadLocal<>();
    private static ExecutorService jeiStartExecutor;
    private static JeiStartTask latestJeiStart;

    private JeiOptExecutors() {
    }

    public static Executor mainThreadExecutor() {
        return MAIN_THREAD;
    }

    public static void executeOnMainThread(Runnable command) {
        MAIN_THREAD.execute(command);
    }

    public static void runJeiStartAsync(long generation, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        JeiStartTask task = new JeiStartTask(generation);
        FutureTask<Void> future = new FutureTask<>(() -> {
            CURRENT_JEI_START.set(task);
            try {
                checkJeiStartActive();
                runnable.run();
                checkJeiStartActive();
            } finally {
                CURRENT_JEI_START.remove();
                synchronized (JEI_START_LOCK) {
                    if (latestJeiStart == task) {
                        latestJeiStart = null;
                    }
                }
            }
            return null;
        });
        task.attach(future);

        synchronized (JEI_START_LOCK) {
            if (latestJeiStart != null) {
                latestJeiStart.cancel();
            }
            latestJeiStart = task;
            jeiStartExecutorLocked().execute(future);
        }
    }

    public static boolean cancelJeiStart() {
        synchronized (JEI_START_LOCK) {
            if (latestJeiStart == null) {
                return false;
            }
            latestJeiStart.cancel();
            latestJeiStart = null;
            return true;
        }
    }

    public static boolean isJeiStartRunning() {
        synchronized (JEI_START_LOCK) {
            return latestJeiStart != null;
        }
    }

    public static boolean isJeiStartThread() {
        return CURRENT_JEI_START.get() != null;
    }

    public static void checkJeiStartActive() {
        JeiStartTask task = CURRENT_JEI_START.get();
        if (task != null) {
            ensureJeiStartActive(task);
        }
    }

    public static boolean isJeiStartCancellation(Throwable throwable) {
        return throwable instanceof JeiStartCancelled;
    }

    public static void awaitJeiStartTask(CompletableFuture<?> future) {
        Objects.requireNonNull(future, "future");
        JeiStartTask task = CURRENT_JEI_START.get();
        if (task == null) {
            throw new IllegalStateException("Only the JEI startup thread may wait for startup work");
        }
        ensureJeiStartActive(task);
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JeiStartCancelled();
        } catch (CancellationException e) {
            throw new JeiStartCancelled();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause != null ? cause : e);
        }
        ensureJeiStartActive(task);
    }

    public static void runOnMainThreadAndWait(Runnable command) {
        Objects.requireNonNull(command, "command");
        JeiStartTask task = CURRENT_JEI_START.get();
        if (task == null) {
            command.run();
            return;
        }
        ensureJeiStartActive(task);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            ensureJeiStartActive(task);
            command.run();
            return;
        }

        FutureTask<Void> publication = new FutureTask<>(() -> {
            ensureJeiStartActive(task);
            command.run();
            return null;
        });
        minecraft.execute(publication);
        try {
            publication.get();
        } catch (InterruptedException e) {
            publication.cancel(false);
            Thread.currentThread().interrupt();
            throw new JeiStartCancelled();
        } catch (CancellationException e) {
            throw new JeiStartCancelled();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause != null ? cause : e);
        }
        ensureJeiStartActive(task);
    }

    public static ExecutorService workerExecutor() {
        synchronized (LOCK) {
            if (workerExecutor == null || workerExecutor.isShutdown()) {
                workerExecutor = Executors.newFixedThreadPool(workerThreadCount, newWorkerThreadFactory());
            }
            return workerExecutor;
        }
    }

    public static void configureWorkerThreads(int requestedThreadCount) {
        int boundedThreadCount = clamp(requestedThreadCount, MIN_WORKER_THREADS, MAX_WORKER_THREADS);
        synchronized (LOCK) {
            if (workerThreadCount == boundedThreadCount) {
                return;
            }
            workerThreadCount = boundedThreadCount;
            shutdownWorkerExecutorLocked();
        }
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return CompletableFuture.supplyAsync(supplier, workerExecutor());
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return CompletableFuture.runAsync(runnable, workerExecutor());
    }

    public static void shutdownWorkerExecutor() {
        synchronized (LOCK) {
            shutdownWorkerExecutorLocked();
        }
    }

    private static void shutdownWorkerExecutorLocked() {
        if (workerExecutor != null) {
            workerExecutor.shutdownNow();
            workerExecutor = null;
        }
    }

    private static ExecutorService jeiStartExecutorLocked() {
        if (jeiStartExecutor == null || jeiStartExecutor.isShutdown()) {
            jeiStartExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, JeiOptimize.MOD_ID + "-start");
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((t, e) ->
                    LOGGER.error("Uncaught exception on {}", t.getName(), e));
                return thread;
            });
        }
        return jeiStartExecutor;
    }

    private static void ensureJeiStartActive(JeiStartTask task) {
        if (task.cancelled.get()
            || !JeiOptRuntimeState.isCurrent(task.generation)
            || (CURRENT_JEI_START.get() == task && Thread.currentThread().isInterrupted())) {
            throw new JeiStartCancelled();
        }
    }

    private static ThreadFactory newWorkerThreadFactory() {
        AtomicInteger threadIndex = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, JeiOptimize.MOD_ID + "-worker-" + threadIndex.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) ->
                LOGGER.error("Uncaught exception in {}", t.getName(), e)
            );
            return thread;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class JeiStartTask {
        private final long generation;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private FutureTask<Void> future;

        private JeiStartTask(long generation) {
            this.generation = generation;
        }

        private void attach(FutureTask<Void> future) {
            this.future = future;
        }

        private void cancel() {
            cancelled.set(true);
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private static final class JeiStartCancelled extends Error {
        private JeiStartCancelled() {
            super("JEI startup was cancelled", null, false, false);
        }
    }
}