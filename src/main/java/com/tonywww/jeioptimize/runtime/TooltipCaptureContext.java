package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

public final class TooltipCaptureContext implements AutoCloseable {
    private static final ThreadLocal<TooltipCaptureContext> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<BiConsumer<Object, Collection<String>>> OBSERVER = new ThreadLocal<>();

    private final TooltipCaptureContext previous;
    private final Object element;
    private final int ordinal;
    private final Thread owner;
    private TooltipSearchSnapshot snapshot;
    private boolean failed;
    private boolean closed;
    private boolean suppress;
    private List<String> replay;
    private int settings = -1;
    private BooleanSupplier advancedSetting;
    private final LongSupplier clock;
    private long getterStarted;
    private boolean getterRunning;
    private long getterNanos;
    private long maxGetterNanos;
    private int getterCalls;

    private TooltipCaptureContext(Object element, int ordinal) {
        this(element, ordinal, System::nanoTime);
    }

    TooltipCaptureContext(Object element, int ordinal, LongSupplier clock) {
        this.element = Objects.requireNonNull(element);
        this.clock = Objects.requireNonNull(clock);
        this.ordinal = ordinal;
        this.owner = Thread.currentThread();
        this.previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static TooltipCaptureContext open(Object element, int ordinal) {
        return new TooltipCaptureContext(element, ordinal);
    }

    public static TooltipCaptureContext suppress(Object element, int ordinal) {
        TooltipCaptureContext context = open(element, ordinal);
        context.suppress = true;
        return context;
    }

    public static TooltipCaptureContext replay(Object element, int ordinal, List<String> strings) {
        TooltipCaptureContext context = open(element, ordinal);
        context.replay = strings;
        return context;
    }

    public static List<String> replayStrings(Object element) {
        TooltipCaptureContext context = CURRENT.get();
        return context != null && context.element == element ? context.replay : null;
    }

    public static boolean suppresses(Object element) {
        TooltipCaptureContext context = CURRENT.get();
        return context != null && context.element == element && context.suppress && !context.failed;
    }

    public static void beginGetter(Object element) {
        TooltipCaptureContext context = CURRENT.get();
        if (context != null && context.element == element && context.replay == null && !context.getterRunning) {
            context.getterStarted = context.clock.getAsLong();
            context.getterRunning = true;
        }
    }

    public static void endGetter(Object element) {
        TooltipCaptureContext context = CURRENT.get();
        if (context != null && context.element == element) {
            context.endGetter();
        }
    }

    private void endGetter() {
        if (getterRunning) {
            long elapsed = clock.getAsLong() - getterStarted;
            getterNanos += elapsed;
            maxGetterNanos = Math.max(maxGetterNanos, elapsed);
            getterCalls++;
            getterRunning = false;
        }
    }

    public long getterNanos() {
        return getterNanos;
    }

    public long maxGetterNanos() {
        return maxGetterNanos;
    }

    public int getterCalls() {
        return getterCalls;
    }

    public static void settings(Object element, boolean advanced) {
        TooltipCaptureContext context = CURRENT.get();
        if (context != null && context.element == element) {
            context.settings = advanced ? 1 : 0;
        }
    }

    public static void settings(Object element, BooleanSupplier advanced) {
        settings(element, advanced.getAsBoolean());
        TooltipCaptureContext context = CURRENT.get();
        if (context != null && context.element == element) {
            context.advancedSetting = advanced;
        }
    }

    public BooleanSupplier advancedSetting() {
        return advancedSetting;
    }

    public int settings() {
        return settings;
    }

    public static boolean isCapturing(Object element) {
        TooltipCaptureContext context = CURRENT.get();
        return context != null && context.element == element || OBSERVER.get() != null;
    }

    public static void observe(BiConsumer<Object, Collection<String>> observer, Runnable action) {
        BiConsumer<Object, Collection<String>> previous = OBSERVER.get();
        OBSERVER.set(observer);
        try {
            action.run();
        } finally {
            if (previous == null) {
                OBSERVER.remove();
            } else {
                OBSERVER.set(previous);
            }
        }
    }

    public static void fail(Object element) {
        TooltipCaptureContext context = CURRENT.get();
        if (context != null && context.element == element) {
            context.failed = true;
        }
    }

    public static void record(Object element, Collection<String> strings) {
        BiConsumer<Object, Collection<String>> observer = OBSERVER.get();
        if (observer != null) {
            observer.accept(element, strings);
        }
        TooltipCaptureContext context = CURRENT.get();
        if (context != null && context.element == element) {
            if (context.snapshot != null) {
                context.failed = true;
                return;
            }
            try {
                context.snapshot = new TooltipSearchSnapshot(context.ordinal, List.copyOf(strings));
            } catch (RuntimeException failure) {
                context.failed = true;
            }
        }
    }

    public boolean failed() {
        return failed;
    }

    public TooltipSearchSnapshot snapshot() {
        return snapshot;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        if (Thread.currentThread() != owner || CURRENT.get() != this) {
            throw new IllegalStateException("Tooltip capture scopes must close on their owner in reverse order");
        }
        endGetter();
        closed = true;
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}