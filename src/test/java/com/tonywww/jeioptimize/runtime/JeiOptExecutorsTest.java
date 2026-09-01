package com.tonywww.jeioptimize.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiOptExecutorsTest {
    @Test
    void retiresJeiStartupExecutorAfterTheLastTaskCompletes() throws Exception {
        long generation = JeiOptRuntimeState.beginStart();

        Future<?> task = JeiOptExecutors.runJeiStartAsync(generation, () -> {
        });
        task.get(5, TimeUnit.SECONDS);

        assertFalse(JeiOptExecutors.isJeiStartRunning());
        assertFalse(JeiOptExecutors.hasJeiStartExecutor());
    }

    @Test
    void cancellationReleasesExecutorAndAllowsANewGeneration() throws Exception {
        long firstGeneration = JeiOptRuntimeState.beginStart();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger maximumActiveTasks = new AtomicInteger();
        Future<?> first = JeiOptExecutors.runJeiStartAsync(firstGeneration, () -> {
            maximumActiveTasks.accumulateAndGet(activeTasks.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                activeTasks.decrementAndGet();
            }
        });

        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(JeiOptExecutors.cancelJeiStart());
        assertTrue(first.isCancelled());

        long secondGeneration = JeiOptRuntimeState.beginStart();
        Future<?> second = JeiOptExecutors.runJeiStartAsync(secondGeneration, () -> {
            maximumActiveTasks.accumulateAndGet(activeTasks.incrementAndGet(), Math::max);
            activeTasks.decrementAndGet();
        });
        second.get(5, TimeUnit.SECONDS);

        assertEquals(1, maximumActiveTasks.get());
        assertFalse(JeiOptExecutors.isJeiStartRunning());
        assertFalse(JeiOptExecutors.hasJeiStartExecutor());
    }

    @Test
    void cancellingAQueuedStartupStillRetiresTheExecutor() throws Exception {
        long firstGeneration = JeiOptRuntimeState.beginStart();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        JeiOptExecutors.runJeiStartAsync(firstGeneration, () -> {
            firstEntered.countDown();
            boolean released = false;
            while (!released) {
                try {
                    releaseFirst.await();
                    released = true;
                } catch (InterruptedException ignored) {
                }
            }
        });
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

        AtomicBoolean queuedTaskRan = new AtomicBoolean();
        long secondGeneration = JeiOptRuntimeState.beginStart();
        Future<?> queued = JeiOptExecutors.runJeiStartAsync(
            secondGeneration,
            () -> queuedTaskRan.set(true)
        );
        assertTrue(JeiOptExecutors.cancelJeiStart());
        assertTrue(queued.isCancelled());
        releaseFirst.countDown();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (JeiOptExecutors.hasJeiStartExecutor() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(queuedTaskRan.get());
        assertFalse(JeiOptExecutors.isJeiStartRunning());
        assertFalse(JeiOptExecutors.hasJeiStartExecutor());
    }
}