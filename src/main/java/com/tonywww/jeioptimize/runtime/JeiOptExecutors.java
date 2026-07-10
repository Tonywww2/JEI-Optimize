package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import net.minecraft.client.Minecraft;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
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

    private JeiOptExecutors() {
    }

    public static Executor mainThreadExecutor() {
        return MAIN_THREAD;
    }

    public static void executeOnMainThread(Runnable command) {
        MAIN_THREAD.execute(command);
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
}